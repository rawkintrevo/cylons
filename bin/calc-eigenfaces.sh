#!/usr/bin/env bash

$SPARK_HOME/bin/spark-submit \
  --class org.rawkintrevo.cylon.eigenfaces.CalcEigenfacesApp \
  --master spark://tower1:7077 \
  /home/rawkintrevo/gits/cylon-blog/eigenfaces/target/spark-eigenfaces-1.0-SNAPSHOT-jar-with-dependencies.jar \
  -p 150
# \
#  [application-arguments]