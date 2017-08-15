#!/usr/bin/env bash

$SPARK_HOME/bin/spark-submit \
  --class org.rawkintrevo.cylon.eigenfaces.CalcEigenfacesApp \
  --master local[*] \
  /home/rawkintrevo/gits/cylon-blog/eigenfaces/target/spark-eigenfaces-1.0-SNAPSHOT-jar-with-dependencies.jar
# \
#  [application-arguments]