package com.tradingsystem.poller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "positions",
            "qty > 0",
            "watchlist",
            "UNION",
            "ORDER BY symbol"
    })
    void queryContainsExpectedSqlPart(String expectedSqlPart) {

        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class)
        )).thenReturn(List.of());

        JdbcSymbolSource source =
                new JdbcSymbolSource(jdbcTemplate);

        source.findSymbols();

        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains(expectedSqlPart)),
                eq(String.class)
        );
    }
}