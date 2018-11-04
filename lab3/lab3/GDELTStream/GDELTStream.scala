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
    p
  }

  val builder: StreamsBuilder = new StreamsBuilder



  // Filter this stream to a stream of (key, name). This is similar to Lab 1,
  // only without dates! After this apply the HistogramTransformer. Finally, 
  // write the result to a new topic called gdelt-histogram. 

    val countStoreSupplier: StoreBuilder[KeyValueStore[String, Long]] =
    Stores.keyValueStoreBuilder(
    Stores.persistentKeyValueStore("Counts"),
    Serdes.String,
    Serdes.Long)
    builder.addStateStore(countStoreSupplier);

  // check time format in doc for key 
  val records: KStream[String, String] = builder.stream[String, String]("gdelt")
  val split = records.map( (key, line) => (key, line.split("\t")))
  val filtered = split.filter((key, row) => row.length > 23)
  val data = filtered.map((key, record) => (key.substring(0, 12), record(23).split(";")))
  val formatted = data.map((key, words) => (key, words.map(x => x.split(",")(0))))
  val flat = formatted.flatMapValues(x => x)

  val outputStream = flat.transform(new HistogramTransformer(), "Counts")
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
  var state: KeyValueStore[String, Long] = _

  // Initialize Transformer object
  def init(context: ProcessorContext){
    this.context = context
    this.state = context.getStateStore("Counts").asInstanceOf[KeyValueStore[String, Long]]
  }

  // Should return the current count of the name during the _last_ hour
  def transform(key: String, name: String): (String, Long) = {
    val df: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmm")
    val count: Optional[Long] = Optional.ofNullable(state.get(name))
    val date1: Date  = new Date(context.timestamp)
    val date2: Date  = df.parse(df.format(Calendar.getInstance().getTime()))
    var incrementedCount: Long = 0L
    // if record is within the hour add one to the counter
    if((date2.getTime() - date1.getTime()) / 1000 < 3600){
      incrementedCount = count.orElse(0L) + 1
      state.put(name, incrementedCount)
      // schedule to remove the count after 1 hour
      this.context.schedule(60000, PunctuationType.WALL_CLOCK_TIME, (timestamp) => {
        state.put(name, state.get(name)-1)
      })
    }
    return (name, incrementedCount)
  }
  // Close any resources if any
  def close() {
  }
}


  // val windowed: TimeWindowedKStream[String, Long] = records
  //   .groupByKey 
  //   .windowedBy(TimeWindows.of(3600000).advanceBy(60000)) 
  // windowed.count().toStream((k, v) => k.key()).to("gdelt-histogram")