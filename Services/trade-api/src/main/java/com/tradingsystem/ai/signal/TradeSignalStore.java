package com.tradingsystem.ai.signal;

import com.tradingsystem.mapper.AiSignalMapper;
import com.tradingsystem.model.AiSignal;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory latest-signal store backed by the ai_signals DB table.
 * The in-memory map acts as a fast read cache.
 * Writes always go to the DB (upsert) as well as the cache.
 */
@Component
public class TradeSignalStore {

    private static final Logger log = LoggerFactory.getLogger(TradeSignalStore.class);

    private final AiSignalMapper aiSignalMapper;
    private final DataSource dataSource;

    /** In-memory cache: symbol → latest AiSignal */
    private final ConcurrentHashMap<String, AiSignal> cache = new ConcurrentHashMap<>();

    private static final java.util.Set<String> BLACKLISTED_SYMBOLS = java.util.Set.of(
            "EMA", "RSI", "SMA", "MACD", "VWAP", "ATR", "BOLL", "STOCH", "OBV", "CCI", "ADX",
            "YOUR", "YOU", "MY", "ME", "WE", "US", "THEM", "THEIR", "OUR", "OURS",
            "BUY", "SELL", "HOLD", "TRADE", "TRADING", "TRADER", "TRADES",
            "ORDER", "ORDERS", "LIMIT", "MARKET", "STOP",
            "POSITION", "POSITIONS", "PORTFOLIO", "ACCOUNT", "ACCOUNTS",
            "CASH", "BALANCE", "CALL", "PUT", "STOCK", "STOCKS", "SHARE", "SHARES",
            "WHAT", "WHEN", "WHERE", "WHICH", "WHO", "WHY", "HOW",
            "SHOW", "VIEW", "ALL", "ANY", "TELL", "GIVE", "HELP", "PLEASE",
            "THE", "AND", "WITH", "FOR", "FROM", "ABOUT", "THIS", "THAT", "THESE", "THOSE",
            "YES", "NO", "NOT", "PRICE", "PRICES", "CANDLE", "CANDLES",
            "AI", "LLM", "MODEL", "QUANT", "ENGINE", "SYSTEM", "INFO",
            "HIGH", "LOW", "OPEN", "CLOSE", "AVG", "AVERAGE", "USD", "INR", "EUR",
            "IN", "ON", "AT", "TO", "OF", "BY", "AN", "AS", "IF", "OR", "SO", "DO", "GO",
            "IS", "ARE", "WAS", "WERE", "BE", "BEING", "BEEN", "HAVE", "HAS", "HAD", "AM",
            "IT", "ITS", "UP", "DOWN", "OUT", "INTO", "ONTO", "THAN", "THEN", "ALSO", "JUST",
            "TODAY", "NOW", "DAY", "DAILY", "WEEK", "WEEKLY", "MONTH", "YEAR", "DATE", "TIME",
            "TOMORROW", "YESTERDAY", "SOON", "LATER", "CAN", "COULD", "WOULD", "SHOULD", "WILL",
            "SIGNAL", "SIGNALS", "ALERT", "ALERTS", "BOT", "CHAT", "GOOD", "BAD", "BEST"
    );

    /**
     * Checks if a string represents a valid financial instrument symbol.
     * Rejects indicator abbreviations, grammar tokens, conversational words, and non-ticker strings.
     */
    public static boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        String clean = symbol.trim().toUpperCase();
        if (clean.length() < 1 || clean.length() > 15) return false;
        if (BLACKLISTED_SYMBOLS.contains(clean)) return false;
        return clean.matches("^[A-Z0-9]+([.:\\-_][A-Z0-9]+)*$");
    }

    public boolean hasValidSymbol(String symbol) {
        if (!isValidSymbol(symbol)) return false;
        return cache.containsKey(symbol.trim().toUpperCase());
    }

    public TradeSignalStore(AiSignalMapper aiSignalMapper, DataSource dataSource) {
        this.aiSignalMapper = aiSignalMapper;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tradingsystem.ai_signals (
                    id            BIGSERIAL PRIMARY KEY,
                    symbol        VARCHAR(20)    NOT NULL,
                    action        VARCHAR(10)    NOT NULL,
                    confidence    DECIMAL(5,4)   NOT NULL,
                    reasoning     TEXT           NOT NULL,
                    generated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_ai_signals_symbol UNIQUE (symbol)
                );
                CREATE INDEX IF NOT EXISTS idx_ai_signals_symbol ON tradingsystem.ai_signals (symbol);
                CREATE INDEX IF NOT EXISTS idx_ai_signals_generated_at ON tradingsystem.ai_signals (generated_at DESC);
                
                -- Purge any erroneous non-ticker entries from legacy runs
                DELETE FROM tradingsystem.ai_signals
                WHERE UPPER(symbol) IN (
                    'EMA', 'RSI', 'SMA', 'MACD', 'VWAP', 'ATR', 'BOLL', 'STOCH', 'OBV', 'CCI', 'ADX',
                    'YOUR', 'YOU', 'MY', 'ME', 'WE', 'US', 'THEM', 'THEIR', 'OUR', 'OURS',
                    'BUY', 'SELL', 'HOLD', 'TRADE', 'TRADING', 'TRADER', 'TRADES',
                    'ORDER', 'ORDERS', 'LIMIT', 'MARKET', 'STOP',
                    'POSITION', 'POSITIONS', 'PORTFOLIO', 'ACCOUNT', 'ACCOUNTS',
                    'CASH', 'BALANCE', 'CALL', 'PUT', 'STOCK', 'STOCKS', 'SHARE', 'SHARES',
                    'WHAT', 'WHEN', 'WHERE', 'WHICH', 'WHO', 'WHY', 'HOW',
                    'SHOW', 'VIEW', 'ALL', 'ANY', 'TELL', 'GIVE', 'HELP', 'PLEASE',
                    'THE', 'AND', 'WITH', 'FOR', 'FROM', 'ABOUT', 'THIS', 'THAT', 'THESE', 'THOSE',
                    'YES', 'NO', 'NOT', 'PRICE', 'PRICES', 'CANDLE', 'CANDLES',
                    'AI', 'LLM', 'MODEL', 'QUANT', 'ENGINE', 'SYSTEM', 'INFO',
                    'IN', 'ON', 'AT', 'TO', 'OF', 'BY', 'AN', 'AS', 'IF', 'OR', 'SO', 'DO', 'GO',
                    'IS', 'ARE', 'WAS', 'WERE', 'BE', 'BEING', 'BEEN', 'HAVE', 'HAS', 'HAD', 'AM',
                    'IT', 'ITS', 'UP', 'DOWN', 'OUT', 'INTO', 'ONTO', 'THAN', 'THEN', 'ALSO', 'JUST',
                    'TODAY', 'NOW', 'DAY', 'DAILY', 'WEEK', 'WEEKLY', 'MONTH', 'YEAR', 'DATE', 'TIME',
                    'TOMORROW', 'YESTERDAY', 'SOON', 'LATER', 'CAN', 'COULD', 'WOULD', 'SHOULD', 'WILL',
                    'SIGNAL', 'SIGNALS', 'ALERT', 'ALERTS', 'BOT', 'CHAT', 'GOOD', 'BAD', 'BEST'
                );
            """);

            try {
                stmt.execute("""
                    DELETE FROM tradingsystem.ai_signals
                    WHERE symbol NOT IN (SELECT symbol FROM tradingsystem.instruments)
                      AND symbol NOT IN ('AAPL', 'SPY', 'INFY.NS', 'RELIANCE.NS', 'HDFCBANK.NS', 'TATASTEEL.BO');
                """);
            } catch (Exception ignored) {
                // Ignore if instruments table doesn't exist in current environment
            }

            // Remove any blacklisted keys that might be in cache
            cache.keySet().removeIf(k -> !isValidSymbol(k));

            log.info("Verified/initialized tradingsystem.ai_signals table successfully and purged invalid symbols.");
        } catch (Exception e) {
            log.warn("Could not auto-verify tradingsystem.ai_signals table: {}", e.getMessage());
        }
    }

    /**
     * Save a signal to both the in-memory cache and the database if symbol is valid.
     */
    public void save(AiSignal signal) {
        if (signal == null || !isValidSymbol(signal.getSymbol())) {
            log.warn("TradeSignalStore: Refusing to save invalid/blacklisted symbol '{}'",
                    signal != null ? signal.getSymbol() : "null");
            return;
        }
        String normalized = signal.getSymbol().trim().toUpperCase();
        cache.put(normalized, signal);
        try {
            aiSignalMapper.upsertSignal(signal);
        } catch (Exception e) {
            log.warn("Failed to persist signal for {} to DB: {}", normalized, e.getMessage());
        }
    }

    /**
     * Returns the latest signal for a symbol, checking cache first then DB.
     */
    public Optional<AiSignal> getBySymbol(String symbol) {
        if (!isValidSymbol(symbol)) return Optional.empty();
        String normalized = symbol.trim().toUpperCase();
        AiSignal cached = cache.get(normalized);
        if (cached != null) return Optional.of(cached);
        try {
            Optional<AiSignal> fromDb = aiSignalMapper.findBySymbol(normalized);
            fromDb.ifPresent(s -> {
                if (isValidSymbol(s.getSymbol())) {
                    cache.put(normalized, s);
                }
            });
            return fromDb.filter(s -> isValidSymbol(s.getSymbol()));
        } catch (Exception e) {
            log.warn("Failed to read signal for {} from DB: {}", normalized, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns all latest signals. Reads from DB to ensure freshness, filtering out invalid symbols.
     */
    public List<AiSignal> getAll() {
        try {
            List<AiSignal> dbSignals = aiSignalMapper.findAll();
            List<AiSignal> valid = new ArrayList<>();
            for (AiSignal s : dbSignals) {
                if (isValidSymbol(s.getSymbol())) {
                    cache.put(s.getSymbol().trim().toUpperCase(), s);
                    valid.add(s);
                }
            }
            return valid;
        } catch (Exception e) {
            log.warn("Failed to read signals from DB, returning cache: {}", e.getMessage());
            return cache.values().stream().filter(s -> isValidSymbol(s.getSymbol())).toList();
        }
    }

    /**
     * Returns a summary string of all valid signals for use in LLM context prompts.
     */
    public String buildSummary() {
        List<AiSignal> signals = getAll();
        if (signals.isEmpty()) return "No signals available yet.";
        StringBuilder sb = new StringBuilder();
        for (AiSignal s : signals) {
            if (isValidSymbol(s.getSymbol())) {
                sb.append(String.format("  %s: %s (confidence=%.0f%%) — %s%n",
                        s.getSymbol(), s.getAction(), s.getConfidence() * 100, s.getReasoning()));
            }
        }
        return sb.toString();
    }
}
