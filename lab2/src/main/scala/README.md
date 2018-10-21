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