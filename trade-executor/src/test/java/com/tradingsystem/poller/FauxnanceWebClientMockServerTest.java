// example: FauxnanceWebClientMockServerTest.java
package com.tradingsystem.poller;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FauxnanceWebClientMockServerTest {

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