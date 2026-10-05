# Kafka Docker Setup Guide

This guide provides step-by-step instructions for setting up Apache Kafka in Docker for the Trade API project.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation Steps](#installation-steps)
- [Verification](#verification)
- [Configuration](#configuration)

---

## Prerequisites

- Docker installed and running
- Access to terminal/command prompt
- Linux IP address (for network configuration)

---

## Installation Steps

### 1. Clean Up Old Containers

Remove any existing Kafka containers:

```bash
docker rm -f kafka
```

Verify the container is removed:

```bash
docker ps
```

If the container might not be active , alternatively use
```bash
docker ps -a
```

### 2. Run Kafka Container

Replace `<LINUX_IP_ADDRESS>` with your actual Linux/Server IP address before running:

```bash
docker run -d --name kafka \
  -p <LINUX_IP_ADDRESS>:9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://<LINUX_IP_ADDRESS>:9092 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@127.0.0.1:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
  -e CLUSTER_ID=4L6g3nShT-eMCtK--X86sw \
  apache/kafka:latest
```

### 3. Create Topics

Navigate to the trade-api directory:

```bash
cd trade-api
```

Make the topic creation script executable:

```bash
chmod +x scripts/create-kafka-topic.sh
```

Run the script:

```bash
./scripts/create-kafka-topic.sh
```

**Expected output:**

```
========================================
Topics created successfully
========================================
market-data
market-data.DLT
orders
orders.DLT
trade-events
trade-events.DLT
```

---

## Verification

### List All Topics

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### Check Partition Count

For the `orders` topic:

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic orders
```

For the `market-data` topic:

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic market-data
```

### Verify Retention Policy

```bash
docker exec kafka /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server localhost:9092 \
  --entity-type topics \
  --entity-name orders \
  --describe
```

---

## Configuration

### Update Application Properties

On Windows, update your `application.properties` file with the following Kafka configuration:

```properties
spring.application.name=kafkademo

# Kafka Bootstrap Servers
# Replace 192.168.1.50 with your actual server IP address
spring.kafka.bootstrap-servers=192.168.1.50:9092

# Consumer Configuration
spring.kafka.consumer.group-id=food-consumer-group
spring.kafka.consumer.auto-offset-reset=earliest

# Producer Serializers
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

# Consumer Deserializers
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

# Server Port
server.port=8080
```

### Configuration Notes

| Property | Description |
|----------|-------------|
| `spring.kafka.bootstrap-servers` | Kafka broker address (update with your IP) |
| `spring.kafka.consumer.group-id` | Consumer group identifier |
| `spring.kafka.consumer.auto-offset-reset` | Strategy for resetting offset (`earliest` or `latest`) |
| `server.port` | Application server port |

---

## Topics Overview

The following topics are created and used by the Trade API:

| Topic | Purpose |
|-------|---------|
| `orders` | Order events and updates |
| `orders.DLT` | Dead Letter Topic for order processing failures |
| `market-data` | Real-time market data feed |
| `market-data.DLT` | Dead Letter Topic for market data failures |
| `trade-events` | Trade execution events |
| `trade-events.DLT` | Dead Letter Topic for trade event failures |

---

## Troubleshooting

### Container Won't Start
- Ensure Docker daemon is running
- Check available disk space
- Verify port 9092 is not in use: `netstat -an \| find "9092"`

### Cannot Connect to Kafka
- Verify the container is running: `docker ps`
- Check if your IP address in `application.properties` matches the server IP
- Ensure firewall allows traffic on port 9092

### Topics Not Created
- Verify the Kafka container is running
- Check script permissions: `ls -l scripts/create-kafka-topics.sh`
- Run the script with explicit path: `bash ./scripts/create-kafka-topics.sh`

---

## Additional Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Docker Hub - Apache Kafka](https://hub.docker.com/_/apache-kafka)
- [Spring Kafka Reference](https://spring.io/projects/spring-kafka)

