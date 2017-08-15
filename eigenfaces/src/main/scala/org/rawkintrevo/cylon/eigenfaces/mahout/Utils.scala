package org.rawkintrevo.cylon.eigenfaces.mahout

import java.io.{FileOutputStream, ObjectOutputStream}

import org.apache.mahout.math.Matrix
import org.apache.mahout.math.scalabindings.MahoutCollections._

import scala.collection.JavaConversions._

object Utils {

  def matrixWriter(m: Matrix, path: String): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(path))
    oos.writeObject(m.toArray.map(v => v.toMap).toList)
    oos.close
  }

}
