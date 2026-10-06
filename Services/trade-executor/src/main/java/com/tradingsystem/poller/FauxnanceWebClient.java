package com.tradingsystem.poller;

import com.tradingsystem.poller.dto.FauxnanceQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.List;

@Component
public class FauxnanceWebClient implements FauxnanceClient {

    private static final Logger log = LoggerFactory.getLogger(FauxnanceWebClient.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final WebClient webClient;
    private final String quoteEndpoint;
    private final String apiKey;

    public FauxnanceWebClient(
            @Value("${fauxnance.api.base-url}") String baseUrl,
            @Value("${fauxnance.api.quote-endpoint}") String quoteEndpoint,
            @Value("${fauxnance.api.key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "FAUXNANCE_API_KEY is missing. Set it in the executor's .env "
                            + "or process environment before starting trade-executor."
            );
        }

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

        FauxnanceQuoteResponse response = getQuoteResponse(symbols);

        if (response == null
                || response.data() == null
                || response.data().quotes() == null) {
            return List.of();
        }

        return response.data()
                .quotes()
                .stream()
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
                getQuoteResponse(List.of(symbol));

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

    /**
     * Calls Fauxnance and retries transient connection failures.
     *
     * A connection reset can happen because the remote server closes
     * an idle connection or because of a temporary network problem.
     */
    private FauxnanceQuoteResponse getQuoteResponse(List<String> symbols) {

        WebClientRequestException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            try {
                log.debug(
                        "Fetching Fauxnance quote for symbols {} (attempt {}/{})",
                        symbols,
                        attempt,
                        MAX_ATTEMPTS
                );

                FauxnanceQuoteResponse response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(quoteEndpoint)
                                .queryParam("symbols", String.join(",", symbols))
                                .build())
                        .header("X-API-Key", apiKey)
                        .retrieve()
                        .bodyToMono(FauxnanceQuoteResponse.class)
                        .block();

                log.debug(
                        "Successfully received Fauxnance response for symbols {}",
                        symbols
                );

                return response;

            } catch (WebClientRequestException ex) {

                lastException = ex;

                log.warn(
                        "Connection error while fetching Fauxnance quote for {} "
                                + "(attempt {}/{}): {}",
                        symbols,
                        attempt,
                        MAX_ATTEMPTS,
                        ex.getMessage()
                );

                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        log.error(
                "Unable to connect to Fauxnance after {} attempts for symbols {}",
                MAX_ATTEMPTS,
                symbols,
                lastException
        );

        throw lastException;
    }

    private void sleepBeforeRetry(int attempt) {

        long delay = RETRY_DELAY_MS * attempt;

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Thread interrupted while waiting to retry Fauxnance request",
                    ex
            );
        }
    }
}