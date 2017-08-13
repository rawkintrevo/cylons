package org.rawkintrevo.cylon.frameprocessors

import java.awt.image.{BufferedImage, DataBufferByte}

import org.opencv.core.{CvType, Mat}


trait FrameProcessor extends Serializable {

  Class.forName("org.rawkintrevo.cylon.opencv.LoadNative")


  var inputRawImage: BufferedImage = _
  var inputMarkupImage: Option[BufferedImage] = _
  var outputMarkupImage: BufferedImage = _

  var mat: Mat = _
  //val mat: Mat = bufferedImageToMat(inputRawImage)

  def bufferedImageToMat(bi: BufferedImage): Unit = {
    // https://stackoverflow.com/questions/14958643/converting-bufferedimage-to-mat-in-opencv
    mat= new Mat(bi.getHeight, bi.getWidth, CvType.CV_8UC3)
    val data = bi.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData
    mat.put(0, 0, data)

  }
}