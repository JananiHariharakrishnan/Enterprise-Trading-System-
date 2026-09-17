package com.tradingsystem.poller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcSymbolSourceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void findSymbolsReturnsSymbolsFromDatabase() {

        List<String> expected =
                List.of(
                        "AAPL",
                        "INFY.NS",
                        "RELIANCE.NS"
                );

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(expected);

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        List<String> result =
                source.findSymbols();

        assertEquals(expected, result);

        verify(jdbcTemplate).queryForList(
                anyString(),
                eq(String.class)
        );
    }

    @Test
    void findSymbolsReturnsEmptyListWhenDatabaseReturnsEmptyList() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        List<String> result =
                source.findSymbols();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void queryIncludesPositionsTable() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class)
        );

        assertTrue(
                sqlCaptor.getValue()
                        .contains("positions")
        );
    }

    @Test
    void queryOnlyIncludesPositivePositions() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class)
        );

        assertTrue(
                sqlCaptor.getValue()
                        .contains("qty > 0")
        );
    }

    @Test
    void queryIncludesWatchlist() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class)
        );

        assertTrue(
                sqlCaptor.getValue()
                        .contains("watchlist")
        );
    }

    @Test
    void queryUsesUnion() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class)
        );

        assertTrue(
                sqlCaptor.getValue()
                        .contains("UNION")
        );
    }

    @Test
    void queryOrdersSymbols() {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class)
        );

        assertTrue(
                sqlCaptor.getValue()
                        .contains("ORDER BY symbol")
        );
    }
}