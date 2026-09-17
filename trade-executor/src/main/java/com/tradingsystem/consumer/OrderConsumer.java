package com.tradingsystem.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.execution.ExecutionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OrderConsumer.class);

    private final ObjectMapper objectMapper;

    private final ExecutionService executionService;

    public OrderConsumer(
            ObjectMapper objectMapper,
            ExecutionService executionService
    ){

        this.objectMapper = objectMapper;

        this.executionService = executionService;
    }

    @KafkaListener(
            topics = "orders",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrder(
            String message,
            Acknowledgment acknowledgment
    ){


        try {
            KafkaEvent<OrderPlacedPayload> event =
                    objectMapper.readValue(
                            message,
                            new TypeReference<
                                    KafkaEvent<OrderPlacedPayload>
                                    >(){}
                    );



            log.info(
                    "ORDER_PLACED received: {}",
                    event.getPayload().getOrderId()
            );
         executionService.execute(
                    event.getPayload()
            );

            acknowledgment.acknowledge();

        }
        catch(Exception e){
            log.error("Order execution failed",e);
        }
    }
}