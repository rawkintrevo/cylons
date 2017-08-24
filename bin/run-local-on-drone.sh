#!/usr/bin/env bash

echo "Make sure to set CYLON_HOME to the directory where you cloned this, OPEN_CV to the directory where\
you built OpenCV 3.x and Solr is running on localhost:8983 with the collection 'cylonfaces' available\n\n\
AND MAKE SURE YOUR DRONE IS ON AND CONNECTED!"

java -Dfile.encoding=UTF-8 -jar $CYLON_HOME/local-engine/target/localengine-1.0-SNAPSHOT-jar-with-dependencies.jar \
	-c $OPEN_CV/data/haarcascades/haarcascade_frontalface_default.xml \
	-e $CYLON_HOME/data/eigenfaces/eigenfaces.mmat \
	-s http://localhost:8983/solr/cylonfaces \
	-i