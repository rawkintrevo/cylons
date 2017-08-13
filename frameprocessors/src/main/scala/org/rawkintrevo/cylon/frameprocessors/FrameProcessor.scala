package org.rawkintrevo.cylon.frameprocessors

import java.awt.image.{BufferedImage, DataBufferByte}

import org.opencv.core.{Core, CvType, Mat}
import org.rawkintrevo.cylon.NativeUtils

trait FrameProcessor {


  //NativeUtils.loadOpenCVLibFromJar() // works with this but copies native jar on each run
//  System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
  //org.rawkintrevo.cylon.opencv.LoadNative.loadNativeLibrary()


//}

//object FrameProcessor {
//  def loadNative = {
//    try {
//      new Mat(1,2, CvType.CV_8S)
//    } catch {
//      case e: java.lang.UnsatisfiedLinkError =>
//        try {
//          System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
//        } catch {
//          case e: java.lang.UnsatisfiedLinkError => "well which is it?!"
//          case _ => "i don't even know"
//      }
//      case _ => "Oh good, native library is already loaded."
//    }
//  }
}