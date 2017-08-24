package org.rawkintrevo.cylon.examples.localfacialrecognition

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.apache.mahout.math.{DenseVector, Vector}
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.opencv.core.{Mat, Size}
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.rawkintrevo.cylon.common.mahout.MahoutUtils
import org.rawkintrevo.cylon.frameprocessors.{FaceDetectorProcessor, ImageUtils}
import org.rawkintrevo.cylon.localengine.KafkaFaceDecomposer
class SimpleKafkaFacialRecognition(topic: String, key: String)
  extends KafkaFaceDecomposer(topic: String, key: String) {

  var threshold: Double = 2000.0

  override def run(): Unit = {
    Class.forName("org.rawkintrevo.cylon.common.opencv.LoadNative")

    val videoCapture = new VideoCapture
    logger.info(s"Attempting to open video source at ${inputPath}")
    videoCapture.open(inputPath)

    if (!videoCapture.isOpened) logger.warn("Camera Error")
    else logger.info(s"Successfully opened video source at ${inputPath}")

    // Create Cascade Filter /////////////////////////////////////////////////////////////////////////////////////////
    FaceDetectorProcessor.initCascadeClassifier(cascadeFilterPath)

    // Init variables needed /////////////////////////////////////////////////////////////////////////////////////////
    var mat = new Mat()

    var facesInView = 0

    var lastRecognizedHuman = ""
    var stateCounter = new Array[Int](5)
    while (videoCapture.read(mat)) {
      val faceRects = FaceDetectorProcessor.createFaceRects(mat)

      val faceArray = faceRects.toArray

      // Scale faces to 250x250 and convert to Mahout DenseVector
      val faceVecArray: Array[DenseVector] = faceArray.map(r => {
        val faceMat = new Mat(mat, r)
        val size: Size = new Size(250, 250)
        val resizeMat = new Mat(size, faceMat.`type`())
        Imgproc.resize(faceMat, resizeMat, size)
        val faceVec = new DenseVector(ImageUtils.matToPixelArray(ImageUtils.grayAndEqualizeMat(resizeMat)))
        faceVec
      })

      // Decompose Image into linear combo of eigenfaces (which were calulated offline)
      val faceDecompVecArray: Array[Vector] = faceVecArray
        .map(v => MahoutUtils.decomposeImgVecWithEigenfaces(v.minus(colCentersV), eigenfacesInCore))

//      for (vec <- faceDecompVecArray) {
//        writeOutput(vec)
//      }

      val nFaces = faceRects.toArray.length

      var triggerDeltaFaces = false
      // Has there been a change in the number of faces in view?
      if (nFaces > facesInView) {
        // we have a new face(s) execute code
        logger.debug(s"where once there were $facesInView, now there are $nFaces")
        triggerDeltaFaces = true
      } else if (nFaces < facesInView) {
        // someone left the frame
        facesInView = nFaces
        triggerDeltaFaces = false
      } else {
        // things to do in the condition of no change
        triggerDeltaFaces = false
      }

      if (triggerDeltaFaces) {
        val faceArray = faceRects.toArray

        // Scale faces to 250x250 and convert to Mahout DenseVector
        val faceVecArray: Array[DenseVector] = faceArray.map(r => {
          val faceMat = new Mat(mat, r)
          val size: Size = new Size(250, 250)
          val resizeMat = new Mat(size, faceMat.`type`())
          Imgproc.resize(faceMat, resizeMat, size)
          val faceVec = new DenseVector(ImageUtils.matToPixelArray(ImageUtils.grayAndEqualizeMat(resizeMat)))
          faceVec
        })

        // Decompose Image into linear combo of eigenfaces (which were calulated offline)
        // todo: don't forget to meanCenter faceVects
        val faceDecompVecArray = faceVecArray.map(v => MahoutUtils.decomposeImgVecWithEigenfaces(v, eigenfacesInCore))
        // drop first 3 elements as they represent 3 dimensional light

        // Query Solr
        import org.apache.solr.client.solrj.SolrQuery
        import org.apache.solr.client.solrj.SolrQuery.SortClause
        import org.apache.mahout.math.scalabindings.MahoutCollections._
        import org.apache.solr.client.solrj.response.QueryResponse

        def eigenFaceQuery(v: org.apache.mahout.math.Vector): QueryResponse = {
          val query = new SolrQuery
          query.setRequestHandler("/select")
          val currentPointStr = v.toArray.mkString(",")
          val eigenfaceFieldNames = (0 until faceDecompVecArray(0).size()).map(i => s"e${i}_d").mkString(",")
          val distFnStr = s"dist(2, ${eigenfaceFieldNames},${currentPointStr})"
          query.setQuery("*:*")
          query.setSort(new SortClause(distFnStr, SolrQuery.ORDER.asc))
          query.setFields("name_s", "calc_dist:" + distFnStr, "last_seen_pdt")
          query.setRows(10)


          val response: QueryResponse = solrClient.query(query)
          response
        }

        def insertNewFaceToSolr(v: org.apache.mahout.math.Vector) = {
          val doc = new SolrInputDocument()
          val humanName = "human-" + scala.util.Random.alphanumeric.take(5).mkString("").toUpperCase
          logger.info(s"I think I'll call you '$humanName'")
          doc.addField("name_s", humanName)
          doc.addField("last_seen_pdt", ZonedDateTime.now.format(DateTimeFormatter.ISO_INSTANT)) // YYYY-MM-DDThh:mm:ssZ   DateTimeFormatter.ISO_INSTANT, ISO-8601
          v.toMap.map { case (k, v) => doc.addField(s"e${k.toString}_d", v) }
          solrClient.add(doc)
          logger.debug("Flushing new docs to solr")
          solrClient.commit()
          humanName
        }

        def getDocsArray(response: QueryResponse): Array[SolrDocument] = {
          val a = new Array[SolrDocument](response.getResults.size())
          for (i <- 0 until response.getResults.size()) {
            a(i) = response.getResults.get(i)
          }
          a
        }

        def lastRecognizedHumanStillPresent(response: QueryResponse): Boolean ={
          val a = getDocsArray(response)
          a.exists(_.get("name_s") == lastRecognizedHuman)
        }

        def lastRecognizedHumanDistance(response: QueryResponse): Double ={
          val a = getDocsArray(response)
          var output: Double = 1000000000
          if (lastRecognizedHumanStillPresent(response)) {
            output = a.filter(_.get("name_s") == lastRecognizedHuman)(0).get("calc_dist").asInstanceOf[Double]
          }
          output
        }


        // todo: replace faceDecompVecArray(0) with for function and iterate
        val response = eigenFaceQuery(faceDecompVecArray(0))

        // in essence canopy clustering....
        /**
          * // (1) Orig Point: new center
          * If next dist(p1, p2) < d1 -> Same Point
          * If next dist(p1, p2) < d2 -> maybe same point
          * Else -> new center
          */
        // Need a buffer- e.g. needs to be outside looseTolerance for n-frames
        val tightTolerance = 1500
        val looseTolerance = 5500
        val minFramesInState = 10

        // (1)
        if (response.getResults.size() == 0) {
          logger.info("I'm a stupid baby- everyone is new to me.")
          insertNewFaceToSolr(faceDecompVecArray(0))
        }


        if (response.getResults.size() > 0) {
          val bestName: String = response.getResults.get(0).get("name_s").asInstanceOf[String]
          val bestDist = response.getResults.get(0).get("calc_dist").asInstanceOf[Double]
          if (lastRecognizedHuman.equals("")) {
            lastRecognizedHuman = bestName
          }
          if (lastRecognizedHumanDistance(response) < tightTolerance){   // State 0
            stateCounter = new Array[Int](5)
            logger.info(s"still $bestName")
          } else if (lastRecognizedHumanDistance(response) < looseTolerance) { // State 1
            stateCounter(1) += 1
            logger.info(s"looks like $bestName")
          } else if (bestDist < tightTolerance) {  // State 2
            if (stateCounter(2) > minFramesInState){
              lastRecognizedHuman = bestName
              logger.info(s"oh hai $bestName ")
              stateCounter = new Array[Int](5)
            } else {stateCounter(2) += 1}
          } else if (bestDist < looseTolerance) {  // State 3
            stateCounter(3) += 1
            logger.info(s"oh god it looks like $bestName, must be the clouds in eyes... $lastRecognizedHuman - ${lastRecognizedHumanDistance(response)} vs $bestDist")
          } else { // State 4
            if (stateCounter(4) > minFramesInState) {
              lastRecognizedHuman = insertNewFaceToSolr(faceDecompVecArray(0))
              stateCounter = new Array[Int](5)
            } else {
              //logger.info(s"no idea, but its been ${stateCounter(4)} frames")
              stateCounter(4) += 1
            }
            // logging handled in subcall

          }

          println(s"$lastRecognizedHuman " + stateCounter.mkString(","))

        }
      }
    }

  }
}
