package org.rawkintrevo.cylon.flinkengine.windowfns

import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.apache.mahout.math.Vector
import org.apache.mahout.math._
import org.apache.mahout.math.algorithms.preprocessing.MeanCenter
import org.apache.mahout.math.decompositions._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.Matrix
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.scalabindings.MahoutCollections._

import scala.collection.JavaConversions._
import org.apache.mahout.math.algorithms.clustering.CanopyFn
import org.apache.mahout.math.algorithms.common.distance.Cosine

class CanopyWindowFunction extends WindowFunction[(String, DenseVector),
  (String, Matrix),
  Tuple,
  TimeWindow] {

  def apply(key: org.apache.flink.api.java.tuple.Tuple,
            window: TimeWindow,
            input: Iterable[(String, DenseVector)],
            out: Collector[(String, Matrix)]) {

    val incoreMat = dense(input.toArray.map(t => t._2))
    val centers = CanopyFn.findCenters(incoreMat, Cosine, 0.1, 0.5)
    out.collect((key(0), centers))
  }
}


case class DecomposedFace(key: String
                          ,h : Int
                          ,w : Int
                          ,x : Int
                          ,y : Int
                          ,frame : Int
                          ,v : Vector)


class CanopyAssignmentCoProcessFunction extends CoProcessFunction[DecomposedFace,
  (String,Matrix),
  (Integer, Vector)] {

  var workingCanopyMatrix: Option[Matrix] = None

  def processElement1(in1: DecomposedFace,
    context: CoProcessFunction[DecomposedFace, (String, Matrix), (Integer, Vector)]#Context,
    collector: Collector[(Integer, Vector)]): Unit = {
    workingCanopyMatrix match {
      case Some(m) => {
        val cluster: Int = (0 until m.nrow).foldLeft(-1, 9999999999999999.9)((l, r) => {
          val dist = Cosine.distance(m(r, ::), in1.v)
          if ((dist) < l._2) {
            (r, dist)
          }
          else {
            l
          }
        })._1
        cluster
      }
      case None => -1  // If the workingCanopyMatrix isn't warmed up yet- then assign everything to -1 cluster.
    }
  }

  def processElement2(in2: (String, Matrix),
     context: CoProcessFunction[DecomposedFace, (String, Matrix), (Integer, Vector)]#Context,
     collector: Collector[(Integer, Vector)]): Unit = {
    workingCanopyMatrix = Some(in2._2)
  }

}