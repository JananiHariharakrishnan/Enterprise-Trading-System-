package com.tradingsystem.poller;

import java.util.List;
import com.tradingsystem.poller.Quote;

public interface FauxnanceClient {

    List<Quote> getQuotes(List<String> symbols);
}