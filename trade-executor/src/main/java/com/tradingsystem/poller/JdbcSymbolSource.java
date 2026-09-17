package com.tradingsystem.poller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("jdbcSymbolSourcePoller")
public class JdbcSymbolSource implements SymbolSource {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSymbolSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findSymbols() {
        return jdbcTemplate.queryForList("""
                SELECT symbol
                FROM positions
                WHERE qty > 0

                UNION

                SELECT symbol
                FROM watchlist

                ORDER BY symbol
                """, String.class);
    }
}