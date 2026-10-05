#!/bin/bash

set -e

BOOTSTRAP_SERVER=localhost:9092

echo "========================================"
echo "Creating Kafka topics"
echo "========================================"

echo "Creating orders..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic orders \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

echo "Creating orders.DLT..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic orders.DLT \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000


echo "Creating trade-events..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic trade-events \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=2592000000

echo "Creating trade-events.DLT..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic trade-events.DLT \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=2592000000


echo "Creating market-data..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic market-data \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=86400000

echo "Creating market-data.DLT..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic market-data.DLT \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=86400000


echo ""
echo "========================================"
echo "Topics created successfully"
echo "========================================"

docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --list