

## 1. Start Kafka

On the Kafka/EC2 machine, check that the Kafka container is running:
```
docker ps
```
You should see the kafka container in the list.

If it is stopped, start it:
```
docker start kafka
```
Then check again:
```
docker ps
```

## 2. Start Trade Executor

Open a terminal in the trade-executor project directory:

cd trade-executor

Start the Spring Boot application:
```
mvn clean spring-boot:run
```
Wait until you see:

Started TradeExecutorApplication

You should also see:
```
Market data poll started
```
This confirms that the market-data scheduler is running

## 3. Check Kafka is reachable

From the Kafka container, verify that Kafka responds:
```
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh   
--bootstrap-server localhost:9092   --list
```
You should see the available Kafka topics.

Make sure market-data  is present.

## 4. Listen to the market-data topic

Open a new terminal and run:
```
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh   
--bootstrap-server localhost:9092   --topic market-data   --from-beginning  
--property print.key=true

```

Keep this terminal running.

The consumer will display messages published to the market-data topic.

## 5. Verify the produced messages

After the Trade Executor poller runs, you should see messages similar to:

AAPL    {"eventType":"QUOTE", ...}
INFY.NS {"eventType":"QUOTE", ...}
RELIANCE.NS    {"eventType":"QUOTE", ...}

The value will contain the quote event.