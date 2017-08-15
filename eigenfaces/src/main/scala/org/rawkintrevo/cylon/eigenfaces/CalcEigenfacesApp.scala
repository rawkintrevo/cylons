package org.rawkintrevo.cylon.eigenfaces

import java.awt.image.{BufferedImage, BufferedImageOp}
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

import org.apache.mahout.math._
import org.apache.mahout.math.decompositions._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.algorithms.preprocessing.MeanCenter

import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.sparkbindings._
import org.apache.spark.{SparkConf, SparkContext}
import org.rawkintrevo.cylon.frameprocessors.ImageUtils

object CalcEigenfacesApp {
  def main(args: Array[String]): Unit = {
    case class Config(

                       bootStrapServers: String = "localhost:9092",
                       inputTopic: String = "flink",
                       outputTopic: String = "test-flink",
                       droneName: String = "test",
                       parallelism: Int = 50
                     )

    val parser = new scopt.OptionParser[Config]("scopt") {
      head("FlinkEngineDemo", "1.0-SNAPSHOT")

      opt[String]('b', "bootstrapServers").optional()
        .action((x, c) => c.copy(bootStrapServers = x))
        .text("Kafka Bootstrap Servers. Default: localhost:9092")

      opt[String]('i', "inputTopic").optional()
        .action((x, c) => c.copy(inputTopic = x))
        .text("Input Kafka Topic. Default: test")

      opt[String]('o', "outputTopic").optional()
        .action((x, c) => c.copy(outputTopic = x))

      opt[Int]('p', "parallelism. default: 50").optional()
        .action((x, c) => c.copy(parallelism = x))

      help("help").text("prints this usage text")
    }

    parser.parse(args, Config()) map { config =>

      val sparkConf = new SparkConf()
        .setAppName("Calculate Eigenfaces")
        .set("spark.kryo.registrator", "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.referenceTracking", "false")
        .set("spark.kryoserializer.buffer", "32k")
        .set("spark.kryoserializer.buffer.max", "1g")
        .set("spark.executor.memory", "22g")
        .set("spark.driver.memory", "2g")

      val sc = new SparkContext(sparkConf)

      implicit val sdc: org.apache.mahout.sparkbindings.SparkDistributedContext = sc2sdc(sc)

      val par = config.parallelism // When using OMP you want as little parallelization as possible
      val imagesRDD: DrmRdd[Int] = sc.binaryFiles("/home/rawkintrevo/gits/cylon-blog/data/lfw-deepfunneled/*/*", par)
        .map(o => new DenseVector(
          ImageUtils.bufferedImageToDoubleArray(
            ImageIO.read(new ByteArrayInputStream(o._2.toArray())))))
        .zipWithIndex
        .map(o => (o._2.toInt, o._1)) // Make the Index the first value of the tuple


      val imagesDRM = drmWrap(rdd = imagesRDD).checkpoint()

      println(s"Dataset: ${imagesDRM.nrow} images, ${imagesDRM.ncol} pixels per image")

      // Mean Center Pixels
      val mcModel = new MeanCenter().fit(imagesDRM)
      // mcModel.colCentersV need to persist this out to be loaded by streaming model.

      val colMeansInCore = dense(mcModel.colCentersV)

      val mcImagesDrm = mcModel.transform(imagesDRM)

      val numberOfEigenfaces = 130
      val (drmU, drmV, s) = dssvd(mcImagesDrm, k = 130, p = 15, q = 0)

      /**
        * drmV -> Eignfaces (transposed) need to load this into Flink engine
        * drmU -> Eigenface linear combos of input faces, load this into Solr
        * -- Or don't only required if we're going to match celebrities.
        */

      drmParallelize(colMeansInCore, 1).dfsWrite("file:///home/rawkintrevo/gits/cylon-blog/data/colMeans")

      drmParallelize(drmV, 3).dfsWrite("file:///home/rawkintrevo/gits/cylon-blog/data/eigenfaces")
    } getOrElse {
      // arguments are bad, usage message will have been displayed
    }
  }
}
