package org.rawkintrevo.cylon.localengine

import java.awt.image.BufferedImage

import org.apache.mahout.math.Vector
import org.rawkintrevo.cylon.common.mahout.MahoutUtils


class KafkaFaceDecomposer(topic: String, key: String) extends AbstractKafkaLocalEngine with AbstractFaceDecomposer {

  def writeOutput(vec: Vector) = {
    writeToKafka(topic, key, MahoutUtils.vector2byteArray(vec))
  }



}


