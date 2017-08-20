#!/usr/bin/env bash

$SPARK_HOME/bin/spark-submit \
  --class org.rawkintrevo.cylon.eigenfaces.CalcEigenfacesApp \
  --master spark://$HOSTNAME:7077 \
  $CYLON_HOME/eigenfaces/target/spark-eigenfaces-1.0-SNAPSHOT-jar-with-dependencies.jar \
  -p 1
  #-p 150
# \
#  [application-arguments]