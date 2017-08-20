package org.rawkintrevo.cylon.eigenfaces.mahout

import java.io.{FileOutputStream, ObjectOutputStream}

import org.apache.mahout.math.Matrix
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.scalabindings.MahoutCollections._
import org.apache.mahout.math.scalabindings.{dense, dvec}

import scala.collection.JavaConversions._

object MahoutUtils {

  def matrixWriter(m: Matrix, path: String): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(path))
    oos.writeObject(m.toArray.map(v => v.toArray).toList)
    oos.close
  }


}
