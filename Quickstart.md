
# Build

It goes without saying you should be running Linux, like a grown up. 

I'm on Ubuntu 17.04 for whatever that is worth.

### Build OpenCV 3.3.0

Helpful links.

[K.M.A.G. Y.O.Y.O](http://www.urbandictionary.com/define.php?term=KMAG%20YOYO)

[May the odds forever be in your favor](http://opencv-java-tutorials.readthedocs.io/en/latest/01-installing-opencv-for-java.html)

### Build Cylon

	git clone https://github.com/rawkintrevo/cylons
	export CYLON_HOME=/path/to/cylons
	cd $CYLON_HOME
	mvn clean package

### Start Apache Kafka 

`$KAFKA_HOME/bin/zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties`

*Hint: Change set* `log.retention.minutes=1` *in* `$KAFKA_HOME/config/server.properties` 

`$KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_HOME/config/server.properties`


### Start Apache Flink

`$FLINK_HOME/bin/start-cluster.sh`

(You can now go to [http://localhost:8080](http://localhost:8080) to see the Flink GUI, if you didn't already know that.)

# Run Sample Scripts

### Testing Kafka Video Producer

`$CYLON_HOME/bin/test-video-producer.sh`

The test will connect to an RSTP video stream, which is the same type of video that the webcam generates.

Drones get about 8 minutes each run, and there is some other quirks to connecting to them, we test our video/markup/face
 detection ability with this video feed.
 
### Viewing the Output

Start the http-server with 

`$CYLON_HOME/bin/test-http-server.sh`

The video server URLs are 

`http://localhost:8090/cylon/cam/<topic>/<key>`

So to check the test feed that was just set up

[http://localhost:8090/cylon/cam/test/test](http://localhost:8090/cylon/cam/test/test)

### Flink

Copy OpenCV jar and binary to Flink Libraries Folder as well as the static loader

	cp $OPENCV_HOME/build/bin/opencv-330.jar $FLINK_HOME/lib
	cp $OPENCV_HOME/build/lib/libopencv_java330.so $FLINK_HOME/lib
	cp $CYLON_HOME/opencv/target/opencv-1.0-SNAPSHOT.jar $FLINK_HOME/lib

	
This is required to avoid a variety of experiences of the dreaded
`java.lang.UnsatisfiedLinkError`
	
Now you can run the Flink face detection demo (which marks up with detected faces)

`$CYLON_HOME/bin/test-flink-faces.sh`

# Observer

You should be able to see some interesting things at:

[http://localhost:8090/cylon/cam/test/test](http://localhost:8090/cylon/cam/test/test)

[http://localhost:8090/cylon/cam/test-flink/test](http://localhost:8090/cylon/cam/test-flink/test)

