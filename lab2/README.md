# How to run the GDELT analysis on a AWS EMR cluster

In this blog post we present a brief description of what we did for running the
GDELT dataset analysis on Amazon AWS. The topics that we will cover are listed
below.

- Deploying the JAR in the cluster
- Scaling it
- Running the full dataset

## Deploying the JAR in the cluster

Running a Spark application in an EMR cluster is straightforward and only requires
two changes in the source code.

First, the line that specifies the application master should be commented as its
value won't be "local" anymore (the new value is "yarn" and AWS sets this
automatically so there is no need to worry). Second, the paths to the data
segments should be changed as these are no longer stored in the local machine. 

As it is possible to use the same JAR to launch multiple steps on the cluster,
we found very useful to use parameters in order to control the size of the
dataset (s, m, l, xl and full) and the implementation (rdd and dataset) to use.
These helped us to iterate faster as we didn't have to change the code over and
over again.

Once the JAR was packaged, we proceeded to test multiple configurations on AWS
by launching steps with different configurations on the cluster. An important
parameter that should be added to the spark-submit options is `--class package.className`
as the code run without it. In the following sections, we'll delve deeper into
the scaling process and implementation details that were important to make it work.

## Scaling it

### Comparing RDD to dataset

Once the code was packaged and ready to run on Amazon EMR, we began testing by
running our code with a single machine (master). In this test, we ran our code
on a very small dataset just to see if it would run and to determine which way
to proceed. One of the first things we needed to do was decide whether to use
the RDD or the dataset Implementation. We ran both the implementations with a
small dataset and recorded the results. From our tests, we observed that the RDD
performed faster only when using a single node, but when we tried to run the
same code on a larger cluster (~3 nodes) we noticed that the dataset
implementation was actually faster. For this reason, we decided to perform all of
our future tests using the dataset implementation only. 

### Testing with increasingly larger datasets ###

Given our limitations and problems with Amazon credits, we needed to start
testing our application in small datasets and slowly move to bigger datasets
when positive results are obtained. For this reason, we divided our data into 5
categories.

| Name | Size |
| ------ | ------ |
| Small [S] |  32.1 MiB |
| Medium [M] | 22.6 GiB |
| Large [L] | 306.9 GiB | 
| Extra Large [XL] |  804.9 GiB |
| Full [F] | 3.7TB | 


Similarly, we did not run our code immediately on a big cluster but limited
ourselves to using clusters of 2-3 nodes. This allowed us to test our
application while keeping things simple and the cost low. After several
attempts, we managed to get the code running on the small cluster, but we
noticed that when attempting to increase the number of nodes and data, our
application was
failing. This was probably due to the fact that our cluster was not properly
configured and we were encountering memory issues. 

This issue was fixed by allowing EMR to configure Spark automatically and in an
effective way. This is done by adding the following property to the cluster
configuration:

```sh
[{"classification":"spark","properties":{"maximizeResourceAllocation":"true"}}]
```

After adding this configuration, we were able to analyze the entire dataset in **8.6 minutes**. 


## Running the full dataset

### Improving the speed of the cluster

After adding this configuration, we made some attempts to improve the speed of
the analysis, by manually changing the configuration parameters of the Spark cluster. 

Specifically,  the following parameters were tweaked:

- Spark.default.parallelism
- Spark.executor.memory
- Spark.executor.instances
- Spark.executor.cores

Several values for these properties were used and each time the metrics of the
process was observed. The fastest time achieved was **7.6 minutes** with the
following parameters used: 

- Driver memory: **48407**
- Executor Memory: **11898**
- Executor cores: **9**
- Executor instances: **80**
- Parallelism: **1440**

### Object serialization 

It is possible to configure Spark to use a different serializer than its default
one. This is called the **Kryo serializer**. 

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
This is probably due to the fact that the Kryo serializer is better suited to be
used in conjunction with RDDs and we used datasets.

### Optimizing resource usage and cost 

The metric that we decided to optimize for this exercise was **cost**. The cost
metric can be optimized both by improving the speed of the cluster (less cost
per job) and by maximizing the resource usage (no waste). 

as we can see form the following graph, there is a lot of unused memory.
![comparing scenario 4 to 7.2](https://i.imgur.com/sXYRrkL.png)
This resource waste must be minimized in order to save cost. An attempt was made
to reduce the memory gap by reducing the number of “slaves” in the cluster.
Unfortunately, when the number of nodes is reduced, the total available
CPU of the cluster is reduced too. Therefore, there is a limit on how many nodes
can be removed from the cluster while remaining cost efficient. 
To find this limit we made some tests, each time using a different number of
slave nodes. 

The results of these test can be seen in the table below:

| Scenario | Slaves | Time (minutes) | Cost (Dollars) |
| ------ | ------ | ------ | ------ |
| 7 | 20 | 7.4 | 1.41 |
| 7.1 | 19 | 7.9 | 1.43 |
| 7.2 | 18 | 8.0 | 1.37 |
| 7.3 | 17 | 10.0 | 1.63 |
| 7.4 | 15 | 9.5 | 1.37 |
| 7.5 | 13 | 11.0 | 1.38 |


As can be observed in the table, we were able to get the most cost efficient
result by using 18 slave nodes, costing us only $1.37 in 8 minutes. If we
compare this with our most expenisve run where we used 20 nodes (on demand) of
the c4.8xlarge cluster we can see a reduction of cost of **~75%** going from
$5.6 to $1.37.

| Scenario | Slaves | Time (minutes) | Cost (Dollars) |
| ------ | ------ | ------ | ------ |
| On demand c4.8xlarge | 20 | 8.6 | 5.6 |

By reducing the number of slave nodes, we also managed to increase the average
load of the cluster by ~12% (from 500 to 560). Also, the average utilization of
the cluster went from 43% (20 slave nodes) to 52% (15
slave nodes). Although this must be taken as a grain of salt, it could imply
that we are better using the resources that are available.

![comparing scenario 4 to 7.2](https://i.imgur.com/lDUeQTl.png)