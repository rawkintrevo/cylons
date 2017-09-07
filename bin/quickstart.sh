#!/usr/bin/env bash

echo "Starting Docker"
sudo docker run --name cylon_solr -d -p 8983:8983 -t solr
sudo docker exec -it --user=solr cylon_solr bin/solr create_core -c cylonfaces

echo "Starting ZooKeeper (nohup)"
nohup $KAFKA_HOME/bin/zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties &
echo "Giving Zookeeper 3 seconds to spool up"
sleep 3

echo "Starting Kafka Server"
nohup $KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_HOME/config/server.properties &

echo "Starting Flink"
$FLINK_HOME/bin/start-cluster.sh

echo "Setting up env for rawkintrevo's computer (edit bin/quickstart.sh for your system)"
export CYLON_HOME=~/gits/cylons/
export OPEN_CV=~/gits/opencv/

echo "Running $CYLON_HOME/bin/examples/producers/f2v-on-tv.sh"
$CYLON_HOME/bin/examples/producers/f2v-on-tv.sh
