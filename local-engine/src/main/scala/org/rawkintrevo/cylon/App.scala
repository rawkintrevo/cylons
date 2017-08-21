package org.rawkintrevo.cylon



import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.opencv.core.{Core, Mat, Size}
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture

import org.slf4j.{Logger, LoggerFactory}

import scopt.OptionParser

import org.apache.mahout.math.{DenseVector, Matrix}

import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.common.{SolrDocument, SolrInputDocument}

import org.rawkintrevo.cylon.frameprocessors.{FaceDetectorProcessor, ImageUtils}
import org.rawkintrevo.cylon.MahoutUtils

object App {

  val logger: Logger = LoggerFactory.getLogger(classOf[App])


  def main(args : Array[String]) {

    case class Config(
                       eigenfacesPath: String = "",
                       solrURL: String = "",
                       videoStreamURL: String = "rtsp://192.168.100.1:554/cam1/mpeg4",
                       cascadeFilterPath: String = "",
                       distanceTolerance: Double = 2000.0
                     )


    val parser = new scopt.OptionParser[Config]("scopt") {
      head("Local Engine", "1.0-SNAPSHOT")

      opt[String]('c', "cascadeFilterPath").required()
        .action((x, c) => c.copy(cascadeFilterPath = x))
        .text("Path to OpenCV Cascade Filter to use, e.g. $OPENCV_3_0/data/haarcascades/haarcascade_frontalface_alt.xml")

      opt[String]('e', "eigenfacesPath").required()
        .action((x, c) => c.copy(eigenfacesPath = x))
        .text("Path to output of eigenfaces file, e.g. $CYLON_HOME/data/eigenfaces.mmat")

      opt[String]('i', "inputVideoURL").optional()
        .action((x, c) => c.copy(videoStreamURL = x))
        .text("URL of input video, use 'http://bglive-a.bitgravity.com/ndtv/247hi/live/native' for testing, defaults to 'rtsp://192.168.100.1:554/cam1/mpeg4' (drone cam address)")

      opt[String]('s', "solrURL").required()
        .action((x, c) => c.copy(solrURL = x))
        .text("URL of Solr, e.g. http://localhost:8983/solr/cylonfaces")

      opt[Double]('t', "distanceTolerance").optional()
        .action((x, c) => c.copy(distanceTolerance = x))
        .text("Double, higher is more tollerant. See Readme. Default: 2000.0")

      help("help").text("prints this usage text")
    }

    parser.parse(args, Config()) map { config =>
      logger.info("Local Engine Started")

      // Load Eigenfaces ///////////////////////////////////////////////////////////////////////////////////////////////
      logger.info(s"Loading Eigenfaces from ${config.eigenfacesPath}")
      val eigenfacesInCore: Matrix = MahoutUtils.listArrayToMatrix(
        MahoutUtils.matrixReader(config.eigenfacesPath))

      val efRows = eigenfacesInCore.numRows()
      val efCols = eigenfacesInCore.numCols()

      logger.info(s"Loaded Eigenfaces matrix ${efRows}x${efCols}")

      // Connect to SOLR ///////////////////////////////////////////////////////////////////////////////////////////////
      logger.info(s"Establishing connectiong to Solr instance at ${config.solrURL}")
      val solrClient: SolrClient = new HttpSolrClient.Builder(config.solrURL).build()

      // Connect to Video Stream ///////////////////////////////////////////////////////////////////////////////////////
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

      val videoCapture = new VideoCapture
      logger.info(s"Attempting to open video source at ${config.videoStreamURL}")
      videoCapture.open(config.videoStreamURL)

      if (!videoCapture.isOpened) logger.warn("Camera Error")
      else logger.info(s"Successfully opened video source at ${config.videoStreamURL}")

      // Create Cascade Filter /////////////////////////////////////////////////////////////////////////////////////////
      FaceDetectorProcessor.initCascadeClassifier(config.cascadeFilterPath)

      // Init variables needed /////////////////////////////////////////////////////////////////////////////////////////
      var mat = new Mat()
      var frameNum = 0
      var facesInView = 0

      var framesSinceRecognizedFace = 0
      var lastRecognizedHuman = ""

      var stateCounter = new Array[Int](5)
      // Frame Reader
      while (videoCapture.read(mat)) {
        val faceRects = FaceDetectorProcessor.createFaceRects(mat)
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

          val threshold: Double = config.distanceTolerance



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
//            if (lastRecognizedHuman.equals(bestName)) {
//              logger.debug(s"still $lastRecognizedHuman")
//              framesSinceRecognizedFace = 0
//            } else if (lastRecognizedHumanStillPresent(response)){
//              // idk do nothing
//              framesSinceRecognizedFace = 0
//              logger.info(s"$lastRecognizedHuman is that you? Did you change your hair? $bestDist")
//            } else if (framesSinceRecognizedFace < 10) { // at this point, last recognized human is gone, how long have they been gone?
//              // todo make that a config variable (how long we wait)
//              framesSinceRecognizedFace += 1
//            } else if (bestDist < threshold) { // Now, it's been a while since we've seen the last human we knew- check for someone else we know.
//              // todo could make this better- maybe keeping track of people we've seen at previous step
//              lastRecognizedHuman = bestName
//              framesSinceRecognizedFace = 0
//              logger.info(s"oh hai $bestName")
//            } else {
//              // This is a serious event- need to be more sure (makes a mess real quick too), on frame out of threshhold and boom.
//              logger.info("I never forget a face, and I don't think I'd forget this ugly mug.")
//              insertNewFaceToSolr(faceDecompVecArray(0))
//            }

          }
        }
      }
      sys.exit(1)
    } getOrElse {
      // arguments are bad, usage message will have been displayed
    }
  }
}


