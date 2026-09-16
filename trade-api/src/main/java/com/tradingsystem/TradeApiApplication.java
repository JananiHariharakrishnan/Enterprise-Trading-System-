package com.tradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka; // add this
@SpringBootApplication
public class TradeApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeApiApplication.class, args);
	}

}