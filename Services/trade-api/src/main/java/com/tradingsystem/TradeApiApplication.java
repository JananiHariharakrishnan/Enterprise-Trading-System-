package com.tradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeApiApplication.class, args);
	}

}