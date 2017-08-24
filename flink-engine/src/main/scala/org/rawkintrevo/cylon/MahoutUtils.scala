package org.rawkintrevo.cylon


import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}

import org.apache.mahout.math.{Matrix, _}
import org.apache.mahout.math.algorithms.preprocessing.MeanCenter
import org.apache.mahout.math.decompositions._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.scalabindings.MahoutCollections._

import scala.collection.JavaConversions._


object MahoutUtils {

  def matrixReader(path: String): List[Array[Double]] ={
    val ois = new ObjectInputStream(new FileInputStream(path))
    // for row in matrix
    val m = ois.readObject.asInstanceOf[List[Array[Double]]]
    ois.close
    m
  }

  def matrixWriter(m: Matrix, path: String): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(path))
    oos.writeObject(m.toArray.map(v => v.toArray).toList)
    oos.close
  }

  def listArrayToMatrix(la: List[Array[Double]]): Matrix = {
    dense(la.map(m => dvec(m)):_*)
  }


}
