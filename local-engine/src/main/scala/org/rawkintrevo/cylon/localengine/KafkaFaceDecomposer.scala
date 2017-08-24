package org.rawkintrevo.cylon.localengine

import java.awt.image.BufferedImage

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.mahout.math.Vector
import org.opencv.core.{Core, Mat, Size}
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.rawkintrevo.cylon.common.mahout.MahoutUtils
import org.rawkintrevo.cylon.frameprocessors.{FaceDetectorProcessor, ImageUtils}


class KafkaFaceDecomposer(topic: String, key: String) extends AbstractKafkaLocalEngine with AbstractFaceDecomposer {

  def writeToKafka(topic: String, key: String, data: Array[Byte]) = {
    val record = new ProducerRecord(topic, key, data)
    producer.send(record)
  }

  def writeOutput(vec: Vector) = {
    writeToKafka(topic, key, MahoutUtils.vector2byteArray(vec))
  }



}


