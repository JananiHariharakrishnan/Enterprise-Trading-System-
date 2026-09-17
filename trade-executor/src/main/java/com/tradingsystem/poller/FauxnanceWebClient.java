package com.tradingsystem.poller;

import com.tradingsystem.poller.dto.FauxnanceQuoteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class FauxnanceWebClient implements FauxnanceClient {

    private final WebClient webClient;
    private final String quoteEndpoint;
    private final String apiKey;

    public FauxnanceWebClient(
            @Value("${fauxnance.api.base-url}") String baseUrl,
            @Value("${fauxnance.api.quote-endpoint}") String quoteEndpoint,
            @Value("${fauxnance.api.key}") String apiKey) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.quoteEndpoint = quoteEndpoint;
        this.apiKey = apiKey;
    }

    @Override
    public List<Quote> getQuotes(List<String> symbols) {

        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        FauxnanceQuoteResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(quoteEndpoint)
                        .queryParam("symbols", String.join(",", symbols))
                        .build())
                .header("X-API-Key", apiKey)
                .retrieve()
                .bodyToMono(FauxnanceQuoteResponse.class)
                .block();

        System.out.println("FAUXNANCE MAPPED RESPONSE: " + response);

        if (response == null
                || response.data() == null
                || response.data().quotes() == null) {
            return List.of();
        }

        return response.data().quotes().stream()
                .filter(q -> q.quote() != null)
                .map(q -> new Quote(
                        q.quote().symbol(),
                        q.quote().price(),
                        q.quote().currency(),
                        q.quote().change(),
                        q.quote().changePercent(),
                        q.quote().previousClose(),
                        q.quote().marketState(),
                        q.stale(),
                        q.quote().asOf()
                ))
                .toList();
    }

    @Override
    public Quote getQuote(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        FauxnanceQuoteResponse response =
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(quoteEndpoint)
                                .queryParam("symbols", symbol)
                                .build())
                        .header("X-API-Key", apiKey)
                        .retrieve()
                        .bodyToMono(FauxnanceQuoteResponse.class)
                        .block();


        if (response == null
                || response.data() == null
                || response.data().quotes() == null
                || response.data().quotes().isEmpty()) {

            return null;
        }


        return response.data()
                .quotes()
                .stream()
                .filter(q -> q.quote() != null)
                .findFirst()
                .map(q -> new Quote(
                        q.quote().symbol(),
                        q.quote().price(),
                        q.quote().currency(),
                        q.quote().change(),
                        q.quote().changePercent(),
                        q.quote().previousClose(),
                        q.quote().marketState(),
                        q.stale(),
                        q.quote().asOf()
                ))
                .orElse(null);
    }
}