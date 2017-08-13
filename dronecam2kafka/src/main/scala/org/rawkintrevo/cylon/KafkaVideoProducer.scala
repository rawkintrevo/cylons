package org.rawkintrevo.cylon

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.imageio.ImageIO

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.opencv.core.{Core, Mat, MatOfByte}
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.videoio.VideoCapture
import org.slf4j.Logger
import org.slf4j.LoggerFactory



class KafkaVideoProducer(topic: String, droneName: String, bootStrapServers: String = "localhost:9092") {

  val logger: Logger = LoggerFactory.getLogger(classOf[KafkaVideoProducer])

  var producer: KafkaProducer[String, Array[Byte]] = _

  def setupKafkaProducer() = {
    val props = new Properties()
    props.put("bootstrap.servers", bootStrapServers)
    // props.put("key.deserializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")

    producer = new KafkaProducer[String, Array[Byte]](props)
    logger.info(s"Kafka Producer Established on Boot Strap Server $bootStrapServers")
  }

  def fromVideoFeed(inputPath: String): Unit = {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    val videoCapture = new VideoCapture
    logger.info(s"Attempting to open video source at $inputPath")
    videoCapture.open(inputPath)

    if (!videoCapture.isOpened) logger.warn("Camera Error")
    else logger.info(s"Successfully opened video source at $inputPath")

    var mat = new Mat();
    while (videoCapture.read(mat)) {
      val matOfByte = new MatOfByte()
      Imgcodecs.imencode(".jpg", mat, matOfByte)
      val record = new ProducerRecord(topic, droneName, matOfByte.toArray)
      producer.send(record)
    }
  }

  def writeBufferedImageToKafka(img: BufferedImage): Unit = {
    val baos = new ByteArrayOutputStream
    ImageIO.write(img, "jpg", baos)
    val record = new ProducerRecord(topic, droneName, baos.toByteArray)
    producer.send(record)
  }
}
