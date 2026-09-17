package com.tradingsystem.execution;


import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.event.payload.TradeEventPayload;
import com.tradingsystem.poller.FauxnanceClient;
import com.tradingsystem.poller.Quote;
import com.tradingsystem.producer.TradeEventProducer;

import org.springframework.stereotype.Service;


import java.util.List;


@Service
public class ExecutionService {



    private final ExecutionRule executionRule;

    private final FauxnanceClient fauxnanceClient;

    private final TradeEventProducer producer;



    public ExecutionService(
            ExecutionRule executionRule,
            FauxnanceClient fauxnanceClient,
            TradeEventProducer producer
    ){

        this.executionRule = executionRule;

        this.fauxnanceClient = fauxnanceClient;

        this.producer = producer;
    }




    public void execute(
            OrderPlacedPayload order
    ){


        List<Quote> quotes =
                fauxnanceClient.getQuotes(
                        List.of(order.getSymbol())
                );



        if(quotes == null || quotes.isEmpty()){


            producer.publishOrderRejected(
                    rejectedPayload(
                            order,
                            "Quote unavailable"
                    )
            );


            return;
        }



        Quote quote = quotes.get(0);



        ExecutionDecision decision =
                executionRule.decide(
                        order.getSide(),
                        order.getPrice(),
                        quote.price()
                );



        if(decision.getStatus()
                == ExecutionDecision.Status.FILLED){


            TradeEventPayload payload =
                    new TradeEventPayload();


            payload.setOrderId(
                    order.getOrderId()
            );

            payload.setAccountId(
                    order.getAccountId()
            );

            payload.setSymbol(
                    order.getSymbol()
            );

            payload.setSide(
                    order.getSide()
            );

            payload.setQuantity(
                    order.getQuantity()
            );


            payload.setPrice(
                    order.getPrice()
            );


            payload.setExecutedPrice(
                    decision.getExecutedPrice()
            );


            payload.setStatus(
                    "FILLED"
            );



            producer.publishOrderFilled(payload);



        }
        else{


            producer.publishOrderRejected(
                    rejectedPayload(
                            order,
                            decision.getReason()
                    )
            );
        }

    }




    private TradeEventPayload rejectedPayload(
            OrderPlacedPayload order,
            String reason
    ){

        TradeEventPayload payload =
                new TradeEventPayload();


        payload.setOrderId(
                order.getOrderId()
        );

        payload.setAccountId(
                order.getAccountId()
        );

        payload.setSymbol(
                order.getSymbol()
        );

        payload.setSide(
                order.getSide()
        );

        payload.setQuantity(
                order.getQuantity()
        );

        payload.setPrice(
                order.getPrice()
        );

        payload.setStatus(
                "REJECTED"
        );


        payload.setReason(
                reason
        );


        return payload;
    }

}