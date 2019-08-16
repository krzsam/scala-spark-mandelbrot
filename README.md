# scala-spark-mandelbrot

An example application to calculate a [Mandelbrot Set](https://en.wikipedia.org/wiki/Mandelbrot_set) using Spark. The goal is to demonstrate use of Spark computing framework to perform calculations
while using Kubernetes as the underlying cluster management platform. The solution is not intended to be optimal in terms of choice of technologies or performance. 

## Application structure

The calculation of the Mandelbrot Set is done in two steps:

* Input data file generation
* Iteration process and creation of the image

#### Input file generation

First, an input data file needs to be created with data points for the iteration process. The data has two types of coordinates combined together within
one data line:
* image space coordinates
* data point coordinates - ***c*** value in the iteration formula

The format of the one line is as below. In image space coordinates, top left corner is (0,0), and bottom right corner is defined
by -sx and -sy parameters (horizontal and vertical image sizes respectively) - see *run-spark-k8s-mand-generate.sh* batch below.

The application currently provides the same number of iterations for each data point in the input data file - providing number of iterations for each data point
separately makes the calculation logic simpler and theoretically more flexible if necessary. 

The input data file is created by ***mandelbrot.Main.generateInputData*** function.

```
image-x,image-y,position-x,position-y,number-of-iterations
```

Where:
* *image-x* : X position of the data point in image space
* *image-y*: Y position of the data point in image space
* *position-x* : X position of the data point in the data space
* *position-y* : Y position of the data point in the data space
* *number-of-iterations* : number of iterations for the given data point.

Batch file to generate the input data file: *run-spark-k8s-mand-generate.sh*
```
hdfs dfs -put -f scala-spark-mandelbrot-assembly-0.3.jar /scala-spark-mandelbrot-assembly-0.3.jar
hdfs dfs -rm -r -f /input-800-600

$SPARK_HOME/bin/spark-submit 
    --class mandelbrot.Main 
    --master k8s://172.31.36.93:6443 
    --deploy-mode cluster 
    --executor-memory 1G 
    --total-executor-cores 3 
    --name mandelbrot 
    --conf spark.kubernetes.container.image=krzsam/spark:spark-docker 
    --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark 
    hdfs://ip-172-31-36-93:4444/scala-spark-mandelbrot-assembly-0.3.jar 
    -h hdfs://ip-172-31-36-93:4444/ 
    -c generate 
    -f hdfs://ip-172-31-36-93:4444/input-800-600 
    -tl -2.2,1.2 
    -br 1.0,-1.2 
    -sx 800 
    -sy 600 
    -i 1024
```

Application parameters:
* *-h* : specifies HDFS URI
* *-c* : specifies step, for input file generation the value should be *generate*
* *-f* : specifies HDFS location for the input data - please bear in mind that input data file will be in fact a directory as the file will be partitioned 
       by Spark and will be physically represented as a directory with files containing partitioned data. 
       Currently the application writes the input file data in Parquet format, but this can be changed to any other supported format.
* *-tl* : specifies top-left corner in input data coordinates. Provided as comma separated pair of decimal numbers.
* *-br* :  specifies bottom-right corner in input data coordinates. Provided as comma separated pair of decimal numbers.
* *-sx* : specifies horizontal image size
* *-sy* : specifies vertical image size
* *-i* : number of iterations each data point will be iterated through

#### Iteration process and creation of the image

Once the input data file is created on HDFS, the generation process reads it line by line and iterates each data point defined by each input line 
using the Mandelbrot Set formula using batch file *run-spark-k8s-mand-calculate.sh*. The actual calculation 
is done by ***mandelbrot.Main.calculateImage*** function.

The size of the image to generate is gathered for the input data using:

```
    val dim_x = calculated.agg( sql.functions.max( "img_x" ) ).head().getInt( 0 ) + 1
    val dim_y = calculated.agg( sql.functions.max( "img_y" ) ).head().getInt( 0 ) + 1
```

The above is not optimal from the performance perspective as input data needs to be re-analyzed, 
but is done that way as an example of using aggregation functions.

```
hdfs dfs -put -f scala-spark-mandelbrot-assembly-0.3.jar /scala-spark-mandelbrot-assembly-0.3.jar

$SPARK_HOME/bin/spark-submit 
    --class mandelbrot.Main 
    --master k8s://172.31.36.93:6443 
    --deploy-mode cluster 
    --executor-memory 1G 
    --total-executor-cores 3 
    --name mandelbrot 
    --conf spark.kubernetes.container.image=krzsam/spark:spark-docker 
    --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark 
    hdfs://ip-172-31-36-93:4444/scala-spark-mandelbrot-assembly-0.3.jar 
    -h hdfs://ip-172-31-36-93:4444/ 
    -c calculate 
    -f hdfs://ip-172-31-36-93:4444/input-800-600
```

Application parameters:
* *-h* : specifies HDFS URI
* *-c* : specifies step, for calculation and image generation the value should be *calculate*
* *-f* : specifies HDFS location for the input data file as created in the generation step above.

The calculation step produces a **single** PNG file on HDFS, and the name corresponds to the input data file as below:
```
input-800-600.<timestamp>.png
``` 

An example file produced by the calculation is shown below:

![Example Mandelbrot Set](https://github.com/krzsam/scala-spark-mandelbrot/blob/master/img/input-800-600.1564009642759.png)

## Application build

The application is build in the same way and uses the same Spark Docker image as created for 
[/github.com/krzsam scala-spark-example](https://github.com/krzsam/scala-spark-example) project.

For specific details on running Spark on Kubernetes including creating Spark Docker image please refer to 
[Running application on Spark via Kubernetes](https://github.com/krzsam/scala-spark-example/blob/master/README-spark-k8s.md)

## Links
* [Hadoop/HDFS](https://hadoop.apache.org/): 3.2.0
  * [HDFS FS shell reference](https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/FileSystemShell.html)
    * for latest version it is recommended to use *hdfs* instead of *hadoop* command
* [Spark](https://spark.apache.org/docs/latest/index.html): 2.4.3
  * this install **included** Scala 2.11.12
  * this install **did not include** Hadoop and instead used jars from the above separate Hadoop installation
  * [Submitting applications in Spark](https://spark.apache.org/docs/latest/submitting-applications.html)
  * [Running Spark on K8s](https://spark.apache.org/docs/latest/running-on-kubernetes.html)
  * [Spark configuration parameters](https://spark.apache.org/docs/latest/configuration.html)
* Docker
  * [Dockerfile reference](https://docs.docker.com/engine/reference/builder/)
  * [Docker Docs](https://docs.docker.com/get-started/)
  * [Spark Docker repository](https://cloud.docker.com/u/krzsam/repository/docker/krzsam/spark)
* Kubernetes
  * [Kubernetes Deployment Docs](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
  * [Kubernetes Service Docs](https://kubernetes.io/docs/concepts/services-networking/service/)
* [sbt-assembly](https://github.com/sbt/sbt-assembly): 0.14.9
* Scala: 2.11.12  (this exact version as it was the version included in Spark)
* Infrastructure
  * AWS, 3 nodes _t3a.xlarge_ (4 processors, 16GB memory)
  * For simplicity, all network traffic on all TCP and UDP ports is enabled in between each of the nodes
    * ip-172-31-36-93 : k8s _Master_ (also serves as _Worker_ node)
    * ip-172-31-38-214 : k8s _Worker_ node
    * ip-172-31-45-170 : k8s _Worker_ node
