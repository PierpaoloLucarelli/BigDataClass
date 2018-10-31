package lab3

import java.util.Properties
import java.util.concurrent.TimeUnit

import org.apache.kafka.streams.kstream.{Transformer}
import org.apache.kafka.streams.processor._
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}


import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.KeyValueStore;

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
  val records: KStream[String, String] = builder.stream[String, String]("gdelt")
  val split = records.map( (key, line) => (key, line.split("\t")))
  val filtered = split.filter((key, row) => row.length > 23)
  val data = filtered.map((key, record) => (key.substring(0, 12), record(23).split(";")))
  val formatted = data.map((key, words) => (key, words.map(x => x.split(",")(0))))
  val flat = formatted.flatMapValues(x => x)
  
  flat.foreach((key,value) => {
    println(key)
    println(value)
  })

  flat.to("gdelt-histogram")

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
  var countStore: KeyValueStore[String, Long] = _

  // Initialize Transformer object
  def init(context: ProcessorContext) {
    this.context = context
    // Using a `KeyValueStoreBuilder` to build a `KeyValueStore`.
    val countStoreSupplier: StoreBuilder[KeyValueStore[String, Long]] =
    Stores.keyValueStoreBuilder(
    Stores.persistentKeyValueStore("Counts"),
    Serdes.String,
    Serdes.Long)
    this.countStore = countStoreSupplier.build()
  }

  // Should return the current count of the name during the _last_ hour
  def transform(key: String, name: String): (String, Long) = {
    ("Donald Trump", 1L)
  }

  // Close any resources if any
  def close() {
  }
}
