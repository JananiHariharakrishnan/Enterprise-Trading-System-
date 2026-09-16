package com.tradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class TradeExecutorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeExecutorApplication.class, args);
	}
}
