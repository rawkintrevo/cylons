package org.rawkintrevo.cylon.flinkengine.apps

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Properties

import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.operators.TwoInputStreamOperator
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.assigners.{SlidingEventTimeWindows, SlidingProcessingTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.fs.bucketing.BucketingSink
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer010, FlinkKafkaProducer010}
import org.apache.flink.util.Collector
import org.apache.mahout.math.algorithms.clustering.CanopyFn
import org.apache.mahout.math.{DenseVector, Matrix, Vector}
import org.apache.mahout.math.algorithms.common.distance.Cosine
import org.apache.mahout.math.scalabindings._
import org.rawkintrevo.cylon.flinkengine.schemas.{KeyedBufferedImageSchema, MahoutVectorAndCoordsSchema}
import org.rawkintrevo.cylon.flinkengine.windowfns.{CanopyWindowFunction, CanopyAssignmentCoProcessFunction}
import org.slf4j.{Logger, LoggerFactory}



object BasicFacialTracking {
  def main(args: Array[String]) {

    var params = ParameterTool.fromArgs(args)


    case class Config(

                       bootStrapServers: String = "localhost:9092",
                       inputTopic: String = "flink",
                       outputTopic: String = "test-flink",
                       droneName: String = "test",
                       parallelism: Int = 1
                     )

      val logger: Logger = LoggerFactory.getLogger(classOf[App])
      val env = StreamExecutionEnvironment.getExecutionEnvironment
      env.getConfig.setGlobalJobParameters(params)

      val config = Config(bootStrapServers = params.has("bootStrapServers") match {
        case true => params.get("bootStrapServers")
        case false => "localhost:9092"
      },
        inputTopic = params.has("inputTopic") match {
          case true => params.get("inputTopic")
          case false => "flink-test-topic"
        },
        outputTopic = params.has("outputTopic") match {
          case true => params.get("outputTopic")
          case false => "test-flink"
        })

      logger.info(s"bootStrapServers: ${config.bootStrapServers}\ninputTopic: ${config.inputTopic}")

      //env.setParallelism(config.parallelism) // Changing paralellism increases throughput but really jacks up the video output.

      val properties = new Properties()
      properties.setProperty("bootstrap.servers", config.bootStrapServers)
      properties.setProperty("group.id", "flink")

      val rawVideoConsumer: FlinkKafkaConsumer010[(String, Int, Int, Int, Int, Int, Vector)] =
        new FlinkKafkaConsumer010[(String, Int, Int, Int, Int, Int, Vector)](config.inputTopic,
        new MahoutVectorAndCoordsSchema(),
        properties)

      rawVideoConsumer.setStartFromLatest()

      val kafkaProducer = new FlinkKafkaProducer010(
        config.outputTopic, // topic
        new KeyedBufferedImageSchema(),
        properties)


      val stream = env
        .addSource(rawVideoConsumer)
        .map(record => {
          DecomposedFace(s"${record._1}_${record._6}", record._2, record._3, record._4, record._5, record._6, record._7)
        } )

      /*  This stream generates Canopy Centers- the results are a Matrix which is broadcasted
      *   and can be used to cluster faces as they arrive.
      */
      val canopyStream: DataStream[(String, Matrix)] = stream.map(face => (face.key, dvec(face.x, face.y, face.frame)))
        .keyBy(0)
        .window(SlidingEventTimeWindows.of(Time.seconds(10), Time.seconds(1)))
        .apply(new CanopyWindowFunction())


    // One stream will be windowed over, say 10 seconds, and identify unique faces (smoothing)
    // E.g. in -> 10 frames, with 17 face rects.  out -> 17 faceRects with "cluster ID" added.
    // val clusteredStream = stream

    stream.connect(canopyStream)
      .process(new CanopyAssignmentCoProcessFunction())





    // One stream will take identified faces, either recognize them or add them to Solr (next episode)



      // execute program
      env.execute("Flink Count Rects in Frame Demo")

  }
}

case class DecomposedFace(key: String
                          ,h : Int
                          ,w : Int
                          ,x : Int
                          ,y : Int
                          ,frame : Int
                          ,v : Vector)




//object FaceDetectorProcessor extends Serializable {
//
////  //System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
////  Class.forName("org.rawkintrevo.cylon.opencv.LoadNative")
////  //NativeUtils.loadOpenCVLibFromJar()
////
////  var inputRawImage: BufferedImage = _
////  var inputMarkupImage: Option[BufferedImage] = _
////  var outputMarkupImage: BufferedImage = _
////
////  var mat: Mat = _
////  //val mat: Mat = bufferedImageToMat(inputRawImage)
////
////  def bufferedImageToMat(bi: BufferedImage): Unit = {
////    // https://stackoverflow.com/questions/14958643/converting-bufferedimage-to-mat-in-opencv
////    mat= new Mat(bi.getHeight, bi.getWidth, CvType.CV_8UC3)
////    val data = bi.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData
////    mat.put(0, 0, data)
////
////  }
//
//  var faceRects: Array[MatOfRect] = _
//
//  var faceXmlPaths: Array[String] = _
//  var cascadeColors: Array[Color] = _
//  var cascadeNames: Array[String] = _
//  var faceCascades: Array[CascadeClassifier] = _
//
//  def initCascadeFilters(paths: Array[String], colors: Array[Color], names: Array[String]): Unit = {
//    faceXmlPaths = paths
//    cascadeColors = colors
//    cascadeNames = names
//    faceCascades = faceXmlPaths.map(s => new CascadeClassifier(s))
//  }
//
//  def createFaceRects(): Array[MatOfRect] = {
//
//    var greyMat = new Mat();
//    var equalizedMat = new Mat()
//
//    // Convert matrix to greyscale
//    Imgproc.cvtColor(mat, greyMat, Imgproc.COLOR_RGB2GRAY)
//    // based heavily on https://chimpler.wordpress.com/2014/11/18/playing-with-opencv-in-scala-to-do-face-detection-with-haarcascade-classifier-using-a-webcam/
//    Imgproc.equalizeHist(greyMat, equalizedMat)
//
//    faceRects = (0 until faceCascades.length).map(i => new MatOfRect()).toArray // will hold the rectangles surrounding the detected faces
//
//    for (i <- faceCascades.indices){
//      faceCascades(i).detectMultiScale(equalizedMat, faceRects(i))
//    }
//    faceRects
//  }
//
//  def markupImage(faceRects: Array[MatOfRect]): Unit = {
//
//    val image: BufferedImage = inputMarkupImage match {
//      case img: Some[BufferedImage] => img.get
//      case _ => {
//        val matBuffer = new MatOfByte()
//        Imgcodecs.imencode(".jpg", mat, matBuffer)
//        ImageIO.read(new ByteArrayInputStream(matBuffer.toArray))
//      }
//
//    }
//
//    val graphics = image.getGraphics
//    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18))
//
//    for (j <- faceRects.indices){
//      graphics.setColor(cascadeColors(j))
//      val name = cascadeNames(j)
//      val faceRectsList = faceRects(j).toList
//      for(i <- 0 until faceRectsList.size()) {
//        val faceRect = faceRectsList.get(i)
//        graphics.drawRect(faceRect.x, faceRect.y, faceRect.width, faceRect.height)
//        graphics.drawString(s"$name", faceRect.x, faceRect.y - 20)
//      }
//    }
//    outputMarkupImage = image
//  }
//
//  def process(image: BufferedImage): BufferedImage = {
//    bufferedImageToMat(image)
//    inputMarkupImage = Some(image)
//    initCascadeFilters(Array("/home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_profileface.xml",
//      "/home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_frontalface_default.xml",
//      "/home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_frontalface_alt.xml",
//      "/home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_frontalface_alt2.xml"),
//      Array(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN),
//      Array("pf", "ff_default", "ff_alt", "ff_alt2")
//    )
//    markupImage(createFaceRects())
//    outputMarkupImage
//  }
//}
