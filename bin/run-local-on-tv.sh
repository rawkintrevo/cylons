#!/usr/bin/env bash

echo "Make sure to set CYLON_HOME to the directory where you cloned this, OPEN_CV to the directory where\
you built OpenCV 3.x and Solr is running on localhost:8983 with the collection 'cylonfaces' available"

java -Dfile.encoding=UTF-8 -jar $CYLON_HOME/local-engine/target/localengine-1.0-SNAPSHOT-jar-with-dependencies.jar \
	-c $OPEN_CV/data/haarcascades/haarcascade_frontalface_alt.xml \
	-e $CYLON_HOME/data/eigenfaces-130/eigenfaces.mmat \
	-i http://bglive-a.bitgravity.com/ndtv/247hi/live/native \
	-s http://localhost:8983/solr/cylonfaces