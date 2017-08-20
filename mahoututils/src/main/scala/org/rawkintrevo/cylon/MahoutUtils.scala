package org.rawkintrevo.cylon


import org.apache.mahout.math._
import org.apache.mahout.math.algorithms.preprocessing.MeanCenter
import org.apache.mahout.math.decompositions._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings._

import org.apache.mahout.math.Matrix
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.scalabindings.MahoutCollections._


import java.io.{FileInputStream, ObjectInputStream}

object MahoutUtils {
  def matrixReader(path: String): List[Array[Double]] ={
    val ois = new ObjectInputStream(new FileInputStream(path))
    // for row in matrix
    val m = ois.readObject.asInstanceOf[List[Array[Double]]]
    ois.close
    m
  }

  def listArrayToMatrix(la: List[Array[Double]]): Matrix = {
    dense(la.map(m => dvec(m)):_*)
  }

  def decomposeImgVecWithEigenfaces(v: Vector, m: Matrix): Vector = {
    // Basically just OLS- prob put the meancentering here too.
    val XtX = m.t %*% m
    val Xty = m.t %*% v
    solve(XtX, Xty)
  }
}
