package com.tradingsystem.mapper;

import com.tradingsystem.model.AiSignal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for the ai_signals table.
 * Uses annotation-based SQL to avoid an extra XML mapper file.
 */
@Mapper
public interface AiSignalMapper {

    /**
     * Insert or update the latest signal for a symbol.
     * ON CONFLICT (symbol) ensures only one row per symbol is kept.
     */
    @Insert("""
            INSERT INTO tradingsystem.ai_signals (symbol, action, confidence, reasoning, generated_at)
            VALUES (#{symbol}, #{action}, #{confidence}, #{reasoning}, #{generatedAt})
            ON CONFLICT (symbol) DO UPDATE
              SET action       = EXCLUDED.action,
                  confidence   = EXCLUDED.confidence,
                  reasoning    = EXCLUDED.reasoning,
                  generated_at = EXCLUDED.generated_at
            """)
    void upsertSignal(AiSignal signal);

    /**
     * Returns all latest signals (one per symbol).
     */
    @Select("SELECT id, symbol, action, confidence, reasoning, generated_at AS generatedAt FROM tradingsystem.ai_signals ORDER BY generated_at DESC")
    List<AiSignal> findAll();

    /**
     * Returns the latest signal for a specific symbol.
     */
    @Select("SELECT id, symbol, action, confidence, reasoning, generated_at AS generatedAt FROM tradingsystem.ai_signals WHERE symbol = #{symbol}")
    Optional<AiSignal> findBySymbol(String symbol);
}
