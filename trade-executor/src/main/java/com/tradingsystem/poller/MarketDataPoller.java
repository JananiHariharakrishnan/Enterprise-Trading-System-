package com.tradingsystem.poller;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnBean(MarketDataProducer.class)
public class MarketDataPoller {

    private static final int MAX_SYMBOLS_PER_REQUEST = 25;
    private static final int DAILY_REQUEST_QUOTA = 2_000;
    private static final long SECONDS_PER_DAY = 86_400;

    private final SymbolSource symbolSource;
    private final FauxnanceClient fauxnanceClient;

    private final MarketDataProducer marketDataProducer;
    private final long intervalSeconds;

    public MarketDataPoller(
            SymbolSource symbolSource,
            FauxnanceClient fauxnanceClient,
            MarketDataProducer marketDataProducer,
            MarketDataPollerProperties properties) {

        this.symbolSource = symbolSource;
        this.fauxnanceClient = fauxnanceClient;
        this.marketDataProducer = marketDataProducer;
        this.intervalSeconds = properties.getIntervalSeconds();

        validateInterval();
    }

    @Scheduled(
            fixedDelayString = "${market-data.poller.interval-seconds}",
            timeUnit = TimeUnit.SECONDS
    )
    public void pollMarketData() {
        System.out.println("Market data poll started");
        List<String> symbols = symbolSource.findSymbols();

        if (symbols.isEmpty()) {
            return;
        }

        validateQuota(symbols.size());

        for (List<String> batch : partition(symbols, MAX_SYMBOLS_PER_REQUEST)) {

            List<Quote> quotes = fauxnanceClient.getQuotes(batch);

            for (Quote quote : quotes) {
                marketDataProducer.publishQuote(quote);
            }
        }
    }

    private void validateInterval() {

        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "POLL_INTERVAL_SECONDS must be greater than zero"
            );
        }

        long minimumInterval = minimumIntervalSeconds(1);

        if (intervalSeconds < minimumInterval) {
            throw new IllegalArgumentException(
                    "POLL_INTERVAL_SECONDS must be at least "
                            + minimumInterval
                            + " seconds to remain within the daily quota"
            );
        }
    }

    private void validateQuota(int symbolCount) {

        long batches = requestCountPerPoll(symbolCount);
        long requestsPerDay = requestsPerDay(symbolCount);

        if (requestsPerDay > DAILY_REQUEST_QUOTA) {
            throw new IllegalStateException(
                    "Configured polling interval exceeds Fauxnance daily quota: "
                            + batches
                            + " request(s)/poll × "
                            + SECONDS_PER_DAY
                            + " / "
                            + intervalSeconds
                            + " = "
                            + requestsPerDay
                            + " requests/day"
            );
        }
    }

    static long requestCountPerPoll(int symbolCount) {
        return (symbolCount + MAX_SYMBOLS_PER_REQUEST - 1)
                / MAX_SYMBOLS_PER_REQUEST;
    }

    static long requestsPerDay(int symbolCount, long intervalSeconds) {
        long batches = requestCountPerPoll(symbolCount);

        return batches * SECONDS_PER_DAY / intervalSeconds;
    }

    private long requestsPerDay(int symbolCount) {
        return requestsPerDay(symbolCount, intervalSeconds);
    }

    static long minimumIntervalSeconds(int symbolCount) {

        long batches = requestCountPerPoll(symbolCount);

        return (long) Math.ceil(
                (double) batches * SECONDS_PER_DAY / DAILY_REQUEST_QUOTA
        );
    }

    private static List<List<String>> partition(
            List<String> symbols,
            int batchSize) {

        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < symbols.size(); i += batchSize) {
            batches.add(
                    symbols.subList(
                            i,
                            Math.min(i + batchSize, symbols.size())
                    )
            );
        }

        return batches;
    }
}