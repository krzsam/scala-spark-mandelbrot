hdfs dfs -put -f scala-spark-mandelbrot-assembly-0.3.jar /scala-spark-mandelbrot-assembly-0.3.jar

$SPARK_HOME/bin/spark-submit --class mandelbrot.Main --master k8s://172.31.36.93:6443 --deploy-mode cluster --executor-memory 1G --total-executor-cores 3 --name mandelbrot --conf spark.kubernetes.container.image=krzsam/spark:spark-docker --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark hdfs://ip-172-31-36-93:4444/scala-spark-mandelbrot-assembly-0.3.jar -h hdfs://ip-172-31-36-93:4444/ -c calculate -f hdfs://ip-172-31-36-93:4444/input-800-600 
