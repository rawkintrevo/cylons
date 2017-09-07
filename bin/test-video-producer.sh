#!/usr/bin/env bash


java -Dfile.encoding=UTF-8 \
-cp /home/rawkintrevo/gits/cylons/examples/target/examples-1.0-SNAPSHOT-jar-with-dependencies.jar \
 org.rawkintrevo.cylon.examples.flink.MetaDataToDisk \
-i http://bglive-a.bitgravity.com/ndtv/247hi/live/native \
-s http://localhost:8983/solr/cylonfaces \
-c /home/rawkintrevo/gits/opencv/data/haarcascades/haarcascade_frontalface_alt.xml \
-e /home/rawkintrevo/gits/cylons/data/eigenfaces-130_2.11/eigenfaces.mmat \
-m /home/rawkintrevo/gits/cylons/data/eigenfaces-130_2.11/colMeans.mmat


