```text
windows-kafka-setup.md
```

```md
# Kafka Windows Setup Guide
## Spring Boot (IntelliJ Windows) Connecting to Kafka on Linux Docker

---

## Current Architecture

Kafka is already running on a Linux machine inside Docker.


Kafka topics are already created on Linux.

Do not create topics again from Windows.

---

# Step 1: Get Linux Machine IP

On the Linux machine:

```bash
hostname -I
````

Example output:

```
192.168.1.50
```

This IP will be used by the Windows Spring Boot application.

---

# Step 2: Verify Kafka Container Is Running

On Linux:

```bash
docker ps
```

Expected:

```
CONTAINER ID   IMAGE             NAMES
xxxxxx         apache/kafka      kafka
```

If Kafka is not running:

```bash
docker start kafka
```

Check logs:

```bash
docker logs kafka
```

---

# Step 3: Test Connection From Windows

Open PowerShell on Windows.

Run:

```powershell
Test-NetConnection <LINUX_IP> -Port 9092
```

Example:

```powershell
Test-NetConnection 192.168.1.50 -Port 9092
```

Expected:

```
TcpTestSucceeded : True
```

If this is false:

Check:

* Linux firewall
* Docker port mapping
* Kafka advertised listener
* Network connectivity

Do not debug Spring Boot until this works.

---

# Step 4: Configure Spring Boot Application

Open:

```
src/main/resources/application.properties
```

Add:

```properties
# Kafka broker
spring.kafka.bootstrap-servers=<LINUX_IP>:9092


# Producer Configuration

# JSON events
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Delivery semantics
spring.kafka.producer.acks=all
spring.kafka.producer.enable-idempotence=true

# Retry configuration
spring.kafka.producer.retries=10
spring.kafka.producer.properties.max.in.flight.requests.per.connection=5


# Consumer Configuration

# IMPORTANT:
# orders has one logical consumer group
# trade-events/market-data have separate groups
# Change group id per service
spring.kafka.consumer.group-id=trade-executor

spring.kafka.consumer.auto-offset-reset=earliest


# Manual offset commit
spring.kafka.consumer.enable-auto-commit=false


# JSON events
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer


# Allow event classes
spring.kafka.consumer.properties.spring.json.trusted.packages=com.tradingsystem.*


# Listener Configuration

# Commit offset after successful processing
spring.kafka.listener.ack-mode=manual

```

Example:

```properties
spring.kafka.bootstrap-servers=192.168.1.50:9092
```

Replace:

```
<LINUX_IP>
```

with your Linux machine IP.

---

# Step 5: Add Kafka Dependency

Check your `pom.xml`.

Spring Kafka dependency should exist:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

After adding:

```
Reload Maven Project
```

from IntelliJ.

---

# Step 6: Start Spring Boot Application

Run from IntelliJ:

```
Run Application
```

Successful startup:

```
Started Application
```

Possible errors:

## Connection refused

Example:

```
Connection refused
```

Means:

* Kafka is not running
* Wrong IP
* Wrong port

## Bootstrap broker disconnected

Check:

```
spring.kafka.bootstrap-servers
```

and verify Linux IP.

---

# Step 7: Create Test Producer

Example:

```java
@Autowired
private KafkaTemplate<String, String> kafkaTemplate;


public void sendMessage(){

    kafkaTemplate.send(
        "orders",
        "1",
        "hello kafka"
    );

}
```

Explanation:

```
topic       key       value
--------------------------------
orders      1         hello kafka
```

The second argument is the Kafka message key.

---

# Step 8: Create Test Consumer

Example:

```java
@KafkaListener(
    topics = "orders",
    groupId = "test-consumer"
)
public void consume(String message){

    System.out.println(message);

}
```

Start application.

Send a message.

Expected output:

```
hello kafka
```

---

# Step 9: Verify Kafka Topics

Topics are already created on Linux:

```
orders
orders.DLT

trade-events
trade-events.DLT

market-data
market-data.DLT
```

Windows application only connects and uses them.

---

# Step 10: Sprint Kafka Implementation Requirements

After connectivity is confirmed, implement the actual trading Kafka flow.

---

## Producer Keys

Kafka keys must follow:

```
orders
key = accountId
```

Example:

```java
kafkaTemplate.send(
    "orders",
    String.valueOf(accountId),
    event
);
```

---

```
trade-events
key = accountId
```

Example:

```java
kafkaTemplate.send(
    "trade-events",
    String.valueOf(accountId),
    event
);
```

---

```
market-data
key = symbol
```

Example:

```java
kafkaTemplate.send(
    "market-data",
    symbol,
    event
);
```

---

# Why Keys Matter

Kafka uses the message key to decide the partition.

The same key always maps to the same partition.

Kafka guarantees ordering only inside a partition.

Therefore:

```
accountId
     |
     v
Kafka partition
     |
     v
Ordering maintained
```

Orders must not use:

```
orderId
```

as the key.

Reason:

```
Order 1 -> Partition 0
Order 2 -> Partition 2
Order 3 -> Partition 1
```

The account's history gets split and ordering is lost.

---

# Step 11: Message Envelope

Every Kafka message must contain:

```json
{
  "eventId": "uuid",
  "eventType": "ORDER_PLACED",
  "eventTime": "2026-09-28T09:14:22Z",
  "source": "trade-api",
  "schemaVersion": 1,
  "payload": {}
}
```

Fields:

| Field         | Purpose           |
| ------------- | ----------------- |
| eventId       | Unique message ID |
| eventType     | Event type        |
| eventTime     | Creation time     |
| source        | Producer service  |
| schemaVersion | Schema version    |
| payload       | Actual event data |

---

# Step 12: Consumer Compatibility

Consumers must ignore unknown fields.

Example:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {

}
```

This allows:

Producer:

```json
{
  "eventId":"123",
  "newField":"abc"
}
```

Old consumer:

```
Ignore newField
Continue processing
```

---

# Step 13: Consumer Groups

Each logical consumer should have its own group.

Examples:

```
trade-executor

portfolio-service

notification-service

watchlist-service

strategy-service
```

Do not use the same group ID for unrelated consumers.

---

# Step 14: Final Checklist

## Kafka Infrastructure

```
[ ] Kafka running on Linux Docker
[ ] Port 9092 reachable from Windows
[ ] Topics created
[ ] Auto topic creation disabled
```

---

## Spring Boot

```
[ ] bootstrap-servers points to Linux IP
[ ] Kafka dependency added
[ ] Application starts successfully
[ ] Producer works
[ ] Consumer works
```

---

## Trading Kafka Implementation

```
[ ] orders uses accountId key
[ ] trade-events uses accountId key
[ ] market-data uses symbol key

[ ] Five-field envelope implemented

[ ] Consumers ignore unknown fields

[ ] DLT handling implemented
```

---

# Final Architecture

```
Windows
 |
 | IntelliJ
 |
 | Spring Boot
 |
 | <LINUX_IP>:9092
 |
Linux
 |
 | Docker
 |
 | Kafka
 |
 +-- orders
 |
 +-- trade-events
 |
 +-- market-data
 |
 +-- *.DLT
```

Kafka infrastructure is complete once the Windows application successfully connects to the Linux Kafka broker.

```

This is the complete Windows-side setup document.
```
