package example

import org.apache.spark.sql.types._
import org.apache.spark.sql._
import org.apache.spark.SparkContext._
import org.apache.log4j.{Level, Logger}
import java.sql.Timestamp


object ExampleSpark {

  def main(args: Array[String]) {


    val spark = SparkSession
      .builder
      .appName("GDELT")
      .config("spark.master", "local")
      .getOrCreate()
    val sc = spark.sparkContext

    // split by tab
    val ds = sc.textFile("segment").map(line => line.split("\t"))

    // take rows > 23 columns
    val filtered = ds.filter(row => row.length > 23)

    // take columns 1 and 23 and split 23 by ;
    val data = filtered.map(record => ((record(1)), (record(23).split(";"))))

    // split col 23 by comma and take only left part
    val formatted = data.map {case (left, right) => ( left.take(8), right.map(x => x.split(",")(0)))}
    
    // split words into separate rows
    val flat = formatted.flatMapValues(x=> x)

    // get count per word-day combinations
    val reduced = (flat.map { case (left, right) => ((left, right), 1) }).reduceByKey(_+_)

    // show word count for each day 
    val perDay = reduced.map{ case ( (x,y),z ) => (x ,(y,z) )}.groupByKey()

    // top 10 per day
    val sorted = perDay.mapValues(x => x.toList.sortBy(x => -x._2).take(10))

    val sqlContext = new SQLContext(sc) 
    import sqlContext.implicits._ 
    sorted.toDF().show()
    spark.stop
  }
}