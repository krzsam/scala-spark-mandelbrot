package mandelbrot

import java.awt.image.BufferedImage
import java.net.URI

import javax.imageio.ImageIO
import org.apache.commons.math3.complex.Complex
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}
import org.apache.spark.sql
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.storage.StorageLevel
import org.slf4j.{Logger, LoggerFactory}

case class InputData(  img_x: Int, img_y: Int, pos_x: Double, pos_y: Double, iter: Int )
case class ResultData( img_x: Int, img_y: Int, result: Double )

object Main {
  private val LOG: Logger = LoggerFactory.getLogger( this.getClass )

  type OptionMap = Map[ Symbol, Any ]

  def getNumCores( sparkSession: SparkSession ): Int = {
    val execCount = sparkSession.sparkContext.statusTracker.getExecutorInfos.length - 1
    val coresPerExecutor = sparkSession.sparkContext.getConf.get("spark.executor.cores","1").toInt
    val totalCores = execCount * coresPerExecutor

    LOG.info( s"Executor count: ${execCount}, cores per executor: ${coresPerExecutor}, total cores: ${totalCores}")

    totalCores
  }

  def generateInputData( fileName: String, sparkSession: SparkSession, topLeft: Complex, bottomRight: Complex,
                         step_x: Int, step_y: Int, iterations: Int ) = {

    // image-x,image-y,(position-x,position-y),number-of-iterations
    val distRe = bottomRight.getReal() - topLeft.getReal()
    val stepRe = distRe / step_x

    val distIm = topLeft.getImaginary() - bottomRight.getImaginary()
    val stepIm = distIm / step_y

    val range_y = 0 until step_y
    val range_x = 0 until step_x
    val rows = for {
      pos_y <- range_y
      pos_x <- range_x
    } yield InputData( pos_x, pos_y, topLeft.getReal + stepRe * pos_x, topLeft.getImaginary - stepIm * pos_y, iterations )

    val df = sparkSession.createDataFrame( rows )

    df.repartition( 2 * getNumCores(sparkSession) ).write.parquet( fileName )
  }

  def calculateImage( fileName: String, sparkSession: SparkSession, fs: FileSystem )= {
    import sparkSession.implicits._
    val dataIn: DataFrame = sparkSession.read.parquet( fileName )
    dataIn.printSchema()

    val calculated = dataIn.map{
      row =>
        val img_x = row.getInt( 0 )
        val img_y = row.getInt( 1 )
        val c0 = new Complex( row.get( 2 ).asInstanceOf[Double], row.get( 3 ).asInstanceOf[Double] )
        val iter  = row.getInt( 4 )
        val result = Calculate.calculateOne( c0, iter )

        ResultData( img_x, img_y, result.abs() )
    }

    calculated.persist( StorageLevel.MEMORY_AND_DISK )

    val dim_x = calculated.agg( sql.functions.max( "img_x" ) ).head().getInt( 0 ) + 1
    val dim_y = calculated.agg( sql.functions.max( "img_y" ) ).head().getInt( 0 ) + 1

    val now = System.currentTimeMillis()
    val prefix = if( fileName.startsWith( "hdfs") ) "" else "hdfs://"
    val newFile = new Path( prefix + fileName + s".${now}.png" )
    LOG.info( s"Output file is: ${newFile}")

    val outputStream: FSDataOutputStream = fs.create( newFile )

    LOG.info( s"Creating image buffer  x size: ${dim_x}  y size: ${dim_y}")
    val img = new BufferedImage( dim_x, dim_y, BufferedImage.TYPE_INT_RGB )

    val iterator = calculated.toLocalIterator
    while( iterator.hasNext ) {
      val row = iterator.next()
      val img_x = row.img_x
      val img_y = row.img_y
      val rawVal = row.result
      val colour = if( rawVal.isNaN ||  rawVal.isInfinite ) 0 else 0xFFFFFF
      img.setRGB( img_x, img_y, colour )
    }

    ImageIO.write( img, "PNG", outputStream )

    outputStream.close()
  }

  def nextOption( map: OptionMap, params: List[String] ):OptionMap = {
    params match {
      case "-c" :: command :: tail =>
        nextOption( map + ( 'command -> command ), tail )
      case "-f" :: fileName :: tail =>
        nextOption( map + ( 'fileName -> fileName ), tail )
      case "-tl" :: topLeft :: tail =>
        val pair = topLeft.split( ",")
        val numPair = new Complex( pair(0).toDouble, pair(1).toDouble )
        nextOption( map + ( 'topLeft -> numPair ), tail )
      case "-br" :: bottomRight :: tail =>
        val pair = bottomRight.split( ",")
        val numPair = new Complex( pair(0).toDouble, pair(1).toDouble )
        nextOption( map + ( 'bottomRight -> numPair ), tail )
      case "-sx" :: steps_x :: tail =>
        nextOption( map + ( 'steps_x -> steps_x.toInt ), tail )
      case "-sy" :: steps_y :: tail =>
        nextOption( map + ( 'steps_y -> steps_y.toInt ), tail )
      case "-i" :: iterations :: tail =>
        nextOption( map + ( 'iterations -> iterations.toInt ), tail )
      case "-h" :: hadoopUri :: tail =>
        nextOption( map + ( 'hadoopUri -> Some( hadoopUri ) ), tail )
      case Nil =>
        map
      case _ =>
        map
    }
  }

  def main(args: Array[String]): Unit = {
    val parameters = args.mkString(",")
    val version = 0.01
    LOG.info( s"Version: ${version} Parameters passed: ${parameters}")

    LOG.error( "Usage:" )
    LOG.error( "\t -c generate   -f <HDFS://file>  -tl <x>,<y> -br <x>,<y> -sx <sx> -sy <sy> -i <iterations> [-h <HADOOP-URI>]")
    LOG.error( "\t -c calculate  -f <HDFS://file>                                                            [-h <HADOOP-URI>]")

    val options = nextOption( Map() + ( 'hadoopUri -> None ), args.toList )

    LOG.info( s"Parsed parameters: ${options}")

    val command =     options.getOrElse( 'command, "unknown" ).asInstanceOf[String]
    val fileName =    options.getOrElse( 'fileName, "unknown" ).asInstanceOf[String]
    val hadoopURI =   options.getOrElse( 'hadoopUri, None ).asInstanceOf[Option[String]]
    val topLeft =     options.getOrElse( 'topLeft, new Complex(0,0) ).asInstanceOf[Complex]
    val bottomRight = options.getOrElse( 'bottomRight, new Complex(0,0) ).asInstanceOf[Complex]
    val steps_x =     options.getOrElse( 'steps_x, 0 ).asInstanceOf[Int]
    val steps_y =     options.getOrElse( 'steps_y, 0 ).asInstanceOf[Int]
    val iterations =  options.getOrElse( 'iterations, 0 ).asInstanceOf[Int]

    // https://spark.apache.org/docs/latest/configuration.html
    val sparkSession: SparkSession = SparkSession.builder()
      .config( "spark.driver.bindAddress", "0.0.0.0" )
      .getOrCreate()

    sparkSession.sqlContext.setConf("spark.sql.parquet.compression.codec","uncompressed")

    val hadoop = sparkSession.sparkContext.hadoopConfiguration
    LOG.info( s"Hadoop configuration is: ${hadoop}" )

    val fs = hadoopURI match {
      case Some( uri ) => FileSystem.get( new URI(uri), hadoop )
      case None => FileSystem.get( hadoop )
    }

    command match {
      case "generate" =>
        generateInputData( fileName, sparkSession, topLeft, bottomRight, steps_x, steps_y, iterations )
      case "calculate" =>
        calculateImage( fileName, sparkSession, fs )
    }

    sparkSession.stop()
  }
}
