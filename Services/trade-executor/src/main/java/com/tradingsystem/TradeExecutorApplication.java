package com.tradingsystem;

import com.tradingsystem.poller.MarketDataPollerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafka
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MarketDataPollerProperties.class)
public class TradeExecutorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeExecutorApplication.class, args);
	}
}
