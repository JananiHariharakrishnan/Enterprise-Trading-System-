package com.tradingsystem.poller;

import java.util.List;

public interface FauxnanceClient {

    List<Quote> getQuotes(List<String> symbols);

    Quote getQuote(String symbol);
}