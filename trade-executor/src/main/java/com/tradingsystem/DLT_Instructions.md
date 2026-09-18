# DLT Checking Commands

## Start Kafka

```bash
docker start kafka
```

## Start Kafka Console Producer

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --property "parse.key=true" \
  --property "key.separator=:"
```

## Send Test Messages

```text
1:{not valid json
```

```text
1:{"eventId":"test-1","eventType":"ORDER_PLACED","eventTime":"2026-09-18T10:00:00Z","source":"test","schemaVersion":1,"payload":{"orderId":"uuid-123"}}
```

```text
1:{"eventId":"test-2","eventType":"ORDER_PLACED","eventTime":"2026-09-18T10:00:00Z","source":"test","schemaVersion":1,"payload":{"orderId":null,"accountId":null,"symbol":"AAPL","side":"BUY","quantity":100,"price":150.00,"idempotencyKey":"key-123","createdOn":"2026-09-18T10:00:00Z"}}
```

## List Kafka Topics

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

## Consume DLT

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic <DLT_TOPIC> \
  --from-beginning \
  --property print.key=true
```
