# How to run the GDELT analysis on a AWS EMR cluster

Instructions: The blog post should be a short informal description of your work
that other students can learn from. Include the things you had to adapt to your
code and cluster to run the entire dataset, and the optimizations you performed.
Also include things that didn't work and why you think they didn't work!

## Deploying the JAR in the cluster

Running a Spark application in a EMR cluster is straight forward and only needs
two simple changes in the code for it to work.

First, the code line that specifies the application master should be commented as
now is not "master" but "yarn" that is used. 

Second, the paths to the segments should be modified as these are no longer
local. In order to test the scalability of the application we found very useful
to use params in the app to be able to test multiple file sizes without having
to modify the code over and over again. 

TODO: add code 

Once this modifications are in place and the application is packaged into a jar
its possible to deploy it to the cluster. In our experience, the easiest way to
do it is by creating a step in the cluster. There it is possible to specify all
the spark-submit options and jar parameters that one will use in the command
line. An example of a set of spark submit options that were used is:

TODO: spark submit parameters

## Scaling it


## Running the full dataset


### Comparing RDD to dataset ###

Once the code was packaged and ready to run on Amazon EMR, we began testing by
running our code with a single machine (master). In this test we ran our code
on a very small dataset just to see if it would run and to determine which way
to proceed. One of the first things we needed to do was decide whether to use
the RDD or the dataset Implementation. We ran both the implementations with a
small dataset and recorded the results. From our tests we observed that the RDD
performed faster only when using a single node, but when we tried to run the
same code on a larger cluster (~3 nodes) we noticed that the dataset
implementation was actually faster. For this reason we decided to perform all of
our future tests using the dataset implementation only. 

### Testing with increasingly larger datasets ###

Given our limitations and problems with Amazon credits, we needed to start
testing our application in small datasets and slowly move to bigger datasets
when positive results are obtained. For this reason we divided our data into 5
categories. 

| Name | Size |
| ------ | ------ |
| Small [S] |  32.1 MiB |
| Medium [M] | 22.6 GiB |
| Large [L] | 306.9 GiB | 
| Extra Large [XL] |  804.9 GiB |
| Full [F] | 3TB | 
**<<check this>>**

Similarly, we did not run our code immediately on a big cluster, but limited
ourselves to using clusters of 2-3 nodes. This allowed us to test our
application while keeping things simple and the cost low. After several attempts
we managed to get the code running on the small cluster, but we noticed that
when attempting to increase the number of nodes and data, our application was
failing. This was probably due to the fact that our cluster was not properly
configured and we were encountering memory issues. 

This issue was fixed by allowing EMR to configure Spark automatically and in an
effective way. This is done by adding the following property to the cluster
configuration:

```sh
[{"classification":"spark","properties":{"maximizeResourceAllocation":"true"}}]
```

After adding this configuration, we were able to analyse the entire dataset in
8.6 minutes. 

### Improving the speed of the cluster ###

Some attempts were made to improve the speed of the analysis, by manually
changing the configuration parameters of the Spark cluster. 

Specifically,  the following parameters were tweaked:

- Spark.default.parallelism
- Spark.executor.memory
- Spark.executor.instances
- Spark.executor.cores

Several values for these properties were used and each time the metrics of the
process were observed. The fastest time achieved was 7.6 minutes with the
following parameters used: 

- Driver memory: **48407**
- Executor Memory: **11898**
- Executor cores: **9**
- Executor instances: **80**
- Parallelism: **1440**

### Object serialization ###

It is possible to configure Spark to use a different serializer than its default
one. This is called the Kryo serializer. 

To use the Kryo serializer, the following lines were added to the configuration
of the SparkSession object:

```sh
...
.config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
.config("spark.kryoserializer.buffer.mb","24")
...
```

The code ran without problems,  however, we did not notice an improvement in
speed after using the better serializer (7.9 minutes)

**<<Add why we think this didnt work>>**

### Problems encountered ###
...
...
...
