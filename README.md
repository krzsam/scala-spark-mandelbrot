# scala-spark-mandelbrot
Calculating Mandelbrot set using Spark cluster


*** Input file format

image-x,image-y,position-x,position-y,number-of-iterations


** create input data - run-spark-k8s-mand-generate.sh

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
    -h hdfs://ip-172-31-36-93:4444/ -c generate -f hdfs://ip-172-31-36-93:4444/input-800-600 -tl -2.2,1.2 -br 1.0,-1.2 -sx 800 -sy 600 -i 1024
```

** generate image - run-spark-k8s-mand-calculate.sh

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
    -h hdfs://ip-172-31-36-93:4444/ -c calculate -f hdfs://ip-172-31-36-93:4444/input-800-600
```
