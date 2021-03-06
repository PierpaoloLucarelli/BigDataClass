package lab3

import java.util.Properties
import java.util.Date;
import java.util.concurrent.TimeUnit
import java.util.Optional
import java.text.SimpleDateFormat
import java.util.Calendar
import org.apache.kafka.streams.kstream.{Transformer}
import org.apache.kafka.streams.processor._
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}


import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.TransformerSupplier
import scala.collection.JavaConversions._


object GDELTStream extends App {
  import Serdes._

  val props: Properties = {
    val p = new Properties()
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, "lab3-gdelt-stream")
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    p.put(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG")
    p
  }

  val builder: StreamsBuilder = new StreamsBuilder



  // Filter this stream to a stream of (key, name). This is similar to Lab 1,
  // only without dates! After this apply the HistogramTransformer. Finally, 
  // write the result to a new topic called gdelt-histogram. 

  val countStoreSupplier: StoreBuilder[KeyValueStore[String, Long]] =
    Stores.keyValueStoreBuilder(
    Stores.inMemoryKeyValueStore("Counts"),
    Serdes.String,
    Serdes.Long).withLoggingDisabled

  val fullLogStoreSupplier: StoreBuilder[KeyValueStore[String, String]] =
    Stores.keyValueStoreBuilder(
    Stores.inMemoryKeyValueStore("fullLog"),
    Serdes.String,
    Serdes.String).withLoggingDisabled

  builder.addStateStore(countStoreSupplier);
  builder.addStateStore(fullLogStoreSupplier);

  // Retrieving and processing the stream
  val records: KStream[String, String] = builder.stream[String, String]("gdelt")
  val split = records.map( (key, line) => (key, line.split("\t")))
  val filtered = split.filter((key, row) => row.length > 23)
  val data = filtered.map((key, record) => (key.substring(0, 12), record(23).split(";")))
  val formatted = data.map((key, words) => (key, words.map(x => x.split(",")(0))))
  val flat = formatted.flatMapValues(x => x)

  val outputStream = flat.transform(new HistogramTransformer(), "Counts", "fullLog")
  
  outputStream.foreach((key,value) => {
      println(key)
      println(value)
  })

  outputStream.to("gdelt-histogram")

  val streams: KafkaStreams = new KafkaStreams(builder.build(), props)
  streams.cleanUp()
  streams.start()

  sys.ShutdownHookThread {
    println("Closing streams.")
    streams.close(10, TimeUnit.SECONDS)
  }

  System.in.read()
  System.exit(0)
}

// This transformer should count the number of times a name occurs 
// during the last hour. This means it needs to be able to 
//  1. Add a new record to the histogram and initialize its count;
//  2. Change the count for a record if it occurs again; and
//  3. Decrement the count of a record an hour later.
// You should implement the Histogram using a StateStore (see manual)
class HistogramTransformer extends Transformer[String, String, (String, Long)] {
  var context: ProcessorContext = _
  var state: KeyValueStore[String, Long] = _ // Histogram
  var fullLog: KeyValueStore[String, String] = _ // Windowed log of all messages

  val df: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmm")
  val TimeWindowWidth = 3600 // 3600 secs for one hour
  val HistogramRefreshRate = 5000 

  def init(context: ProcessorContext){
    this.context = context
    this.state = context.getStateStore("Counts").asInstanceOf[KeyValueStore[String, Long]]
    this.fullLog = context.getStateStore("fullLog").asInstanceOf[KeyValueStore[String, String]]
      
    // Updates the window of the histogram in the specified interval
    this.context.schedule(this.HistogramRefreshRate, PunctuationType.WALL_CLOCK_TIME, (timestamp) => {
      computeHistogram()  
    })
  }

  def computeHistogram() = {
    println("Recomputing histogram ...")
    val recordsIterator = fullLog.all()
    var count = 0
    val currentDate: Date  = df.parse(df.format(Calendar.getInstance().getTime()))
    while(recordsIterator.hasNext){
      val record = recordsIterator.next()
      try{ // dealing with records comming with different formats
        val recordTimestamp = record.key.split("---")(0).toLong
        val recordDate: Date  = new Date(recordTimestamp)
        if((currentDate.getTime()  - recordDate.getTime()) / 1000 > this.TimeWindowWidth){ 
          println(record.value + " OUT!") 
          state.put(record.value, state.get(record.value)-1) 
          fullLog.delete(record.key)        
          context.forward(record.value, state.get(record.value))
        }
      } catch {          
          case e: Exception => {
            println(record)
            println(e)            
          }
      }
    }     
  }

  def transform(key: String, name: String): (String, Long) = {
    val recordDate: Date  = new Date(context.timestamp)
    val currentDate: Date  = df.parse(df.format(Calendar.getInstance().getTime()))
    
    val currentCount: Optional[Long] = Optional.ofNullable(state.get(name))
    var incrementedCount: Long = currentCount.orElse(0L)
    
    println("record date: " + recordDate)
    println("current date: " + currentDate)

    if ((currentDate.getTime() - recordDate.getTime()) / 1000 < this.TimeWindowWidth) {
      fullLog.put(recordDate.getTime() + "---" + name, name) 
      incrementedCount = incrementedCount + 1 
    }

    if (incrementedCount < 0) incrementedCount = 0 // Temporary fix for the negative values that appear sometimes
      
    state.put(name, incrementedCount)      

    return (name, incrementedCount)
  }

  // Close any resources if any
  def close() {
  }
}