package org.rawkintrevo.cylon

import scopt.OptionParser

object Main extends App {
  case class Config(
                   inputPath: String = "rtsp://192.168.100.1:554/cam1/mpeg4",
                   bootStrapServers: String = "localhost:9092",
                   topic: String = "cylon-raw-video-feed",
                   droneName: String = "."
                   )

  val parser = new scopt.OptionParser[Config]("scopt") {
    head("KafkaVideoProducer", "1.0-SNAPSHOT")

    opt[String]('i', "inputPath").optional()
      .action( (x, c) => c.copy(inputPath = x) )
      .text("inputPath of video feed. Default: rtsp://192.168.100.1:554/cam1/mpeg4")

    opt[String]('b', "bootstrapServers").optional()
      .action( (x, c) => c.copy(bootStrapServers = x) )
      .text("Kafka Bootstrap Servers. Default: localhost:9092")

    opt[String]('t', "topic").optional()
      .action( (x, c) => c.copy(topic = x) )
      .text("Kafka Topic. Default: cylon-raw-video-feed")

    opt[String]('k', "kafkaKey").required()
      .action( (x, c) => c.copy(droneName = x) )
      .text("Kafka Key for this Feed. e.g. 'Gold1' if that was the call sign of this drone.")

    help("help").text("prints this usage text")
  }

  parser.parse(args, Config()) map { config =>
    val bootStrapServers = config.bootStrapServers
    val kvp = new KafkaVideoProducer(config.topic, config.droneName, config.bootStrapServers)
    kvp.setupKafkaProducer()
    kvp.fromVideoFeed(config.inputPath)

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

}
