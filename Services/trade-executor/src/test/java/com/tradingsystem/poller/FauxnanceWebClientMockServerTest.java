package com.tradingsystem.poller;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;



class FauxnanceWebClientMockServerTest {

    @Test
    void getQuotes_mapsResponseAndFiltersNulls() throws Exception {
        String body;
        try (InputStream is = getClass().getResourceAsStream("/fauxnance-quote-success.json")) {
            assertNotNull(is, "fixture not found");
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(body).setHeader("Content-Type", "application/json"));
            server.start();

            String baseUrl = server.url("/").toString();
            FauxnanceWebClient client = new FauxnanceWebClient(baseUrl, "/quotes", "key");

            List<Quote> quotes = client.getQuotes(List.of("AAPL","MSFT"));

            assertNotNull(quotes);
            assertFalse(quotes.isEmpty());
            // Only one non-null quote should be returned from fixture
            assertEquals(1, quotes.size());
            Quote q = quotes.get(0);
            assertEquals("AAPL", q.symbol());
            assertEquals("USD", q.currency());
        }
    }

    @Test
    void getQuote_emptyQuotes_returnsNull() throws Exception {
        String body = "{ \"data\": { \"quotes\": [] } }";

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(body).setHeader("Content-Type", "application/json"));
            server.start();

            FauxnanceWebClient client = new FauxnanceWebClient(server.url("/").toString(), "/quotes", "key");
            assertNull(client.getQuote("AAPL"));
        }
    }


    @Test
    void getQuotes_mapsResponseToQuoteList_andFiltersNullQuotes() throws Exception {
        String body = new String(getClass().getResourceAsStream("/fauxnance-quote-success.json")
                         .readAllBytes(), StandardCharsets.UTF_8);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(body).setHeader("Content-Type", "application/json"));
            server.start();

            String baseUrl = server.url("/").toString();
            FauxnanceWebClient client = new FauxnanceWebClient(baseUrl, "/quotes", "test-key");

            List<Quote> quotes = client.getQuotes(List.of("AAPL,MSFT"));

            assertNotNull(quotes);
            // assert filtering (only non-null quote objects present)
            assertTrue(quotes.stream().allMatch(q -> q.symbol().equals("AAPL") || q.symbol().equals("MSFT")));
            // check a field
            assertEquals("USD", quotes.get(0).currency());
        }
    }

    @Test
    void getQuote_emptyData_returnsNull() throws Exception {
        String body = "{\"data\": {\"quotes\": []}}";
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(body).setHeader("Content-Type", "application/json"));
            server.start();

            FauxnanceWebClient client = new FauxnanceWebClient(server.url("/").toString(), "/quotes", "k");
            assertNull(client.getQuote("AAPL"));
        }
    }
}