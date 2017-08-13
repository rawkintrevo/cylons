package org.rawkintrevo.cylon

import java.io._

object NativeUtils {
  // heavily based on https://github.com/adamheinrich/native-utils/blob/master/src/main/java/cz/adamh/utils/NativeUtils.java
  def loadOpenCVLibFromJar() = {

    val temp = File.createTempFile("libopencv_java330", ".so")
    temp.deleteOnExit()

    val inputStream= getClass().getResourceAsStream("/libopencv_java330.so")

    import java.io.FileOutputStream
    import java.io.OutputStream
    val os = new FileOutputStream(temp)
    var readBytes: Int = 0
    var buffer = new Array[Byte](1024)
    try {
      while ({(readBytes = inputStream.read(buffer))
               readBytes != -1}) {
        os.write(buffer, 0, readBytes)
      }
    }
    finally {
      // If read/write fails, close streams safely before throwing an exception
      os.close()
      inputStream.close
    }

    System.load(temp.getAbsolutePath)
  }

}
