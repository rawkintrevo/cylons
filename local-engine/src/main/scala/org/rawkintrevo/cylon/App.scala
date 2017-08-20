package org.rawkintrevo.cylon


import java.awt.Color
import java.time.{Instant, LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter

import org.apache.kafka.clients.producer.ProducerRecord
import org.opencv.core.{Core, Mat, MatOfByte, Size}
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.slf4j.{Logger, LoggerFactory}
import scopt.OptionParser
import org.apache.mahout.math.{DenseMatrix, DenseVector, Matrix}
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.rawkintrevo.cylon.frameprocessors.{FaceDetectorProcessor, ImageUtils}
import org.rawkintrevo.cylon.MahoutUtils

object App {

  val logger: Logger = LoggerFactory.getLogger(classOf[App])

  def main(args : Array[String]) {

    logger.info("Local Engine Started")
    logger.info("Loading Eigenfaces")
    val eigenfacesInCore: Matrix = MahoutUtils.listArrayToMatrix(
      MahoutUtils.matrixReader("/home/rawkintrevo/gits/cylon-blog/data/eigenfaces/eigenfaces.mmat"))
    val efRows = eigenfacesInCore.numRows()
    val efCols = eigenfacesInCore.numCols()
    logger.info(s"Loaded Eigenfaces matrix ${efRows}x${efCols}")

    val solrUrl = "http://localhost:8983/solr/foo2"
    val solrClient: SolrClient = new HttpSolrClient.Builder(solrUrl).build()


    val inputPath = "http://bglive-a.bitgravity.com/ndtv/247hi/live/native"
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    val videoCapture = new VideoCapture
    logger.info(s"Attempting to open video source at $inputPath")
    videoCapture.open(inputPath)

    if (!videoCapture.isOpened) logger.warn("Camera Error")
    else logger.info(s"Successfully opened video source at $inputPath")

    var mat = new Mat();
    // todo pull from cylon home
    FaceDetectorProcessor.initCascadeClassifier("/home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_frontalface_alt.xml")

    var frameNum = 0
    var facesInView = 0
    // Frame Reader
    while (videoCapture.read(mat)) {
      val faceRects = FaceDetectorProcessor.createFaceRects(mat)
      val nFaces = faceRects.toArray.length

      var triggerDeltaFaces = false
      // Has there been a change in the number of faces in view?
      if (nFaces > facesInView) {
        // we have a new face(s) execute code
        logger.info(s"where once there were $facesInView, now there are $nFaces")
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
          val size: Size = new Size(250,250)
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
          v.toMap.map{ case(k, v) => doc.addField(s"e${k.toString}_d", v) }
          solrClient.add(doc)
          logger.debug("Flushing new docs to solr")
          solrClient.commit()
        }

        val threshold: Double = 2000.0

        // todo: replace faceDecompVecArray(0) with for function and iterate
        val response = eigenFaceQuery(faceDecompVecArray(0))

        if (response.getResults.size() == 0) {
          logger.info("I'm a stupid baby- everyone is new to me.")
          insertNewFaceToSolr(faceDecompVecArray(0))
        }

        if (response.getResults.size() > 0) {
          val bestName = response.getResults.get(0).get("name_s").asInstanceOf[String]
          val bestDist = response.getResults.get(0).get("calc_dist").asInstanceOf[Double]
          logger.info(s"${bestName}: ${bestDist}")
          if (bestDist > threshold){
            logger.info("I never forget a face, and I don't think I'd forget this ugly mug.")
            insertNewFaceToSolr(faceDecompVecArray(0))
          } else {
            logger.info(s"I know you! You're $bestName")
          }
        }
      }

    }
  }
}


