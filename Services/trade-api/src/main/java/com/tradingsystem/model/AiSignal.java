package com.tradingsystem.model;

import java.time.Instant;

/**
 * MyBatis model for the ai_signals table.
 * Stores the latest AI-generated trading signal per symbol.
 * Informs a customer who trades; it does not trade.
 */
public class AiSignal {

    private Long id;
    private String symbol;
    private String action;        // BUY | SELL | HOLD
    private double confidence;    // 0.0 – 1.0
    private String reasoning;
    private Instant generatedAt;

    // In-memory technical indicators associated with this signal computation
    private Double rsi;
    private Double ema9;
    private Double ema21;
    private Double currentPrice;

    public AiSignal() {}

    public AiSignal(String symbol, String action, double confidence, String reasoning) {
        this.symbol = symbol;
        this.action = action;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.generatedAt = Instant.now();
    }

    public AiSignal(String symbol, String action, double confidence, String reasoning,
                    Double rsi, Double ema9, Double ema21, Double currentPrice) {
        this(symbol, action, confidence, reasoning);
        this.rsi = rsi;
        this.ema9 = ema9;
        this.ema21 = ema21;
        this.currentPrice = currentPrice;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Double getRsi() { return rsi; }
    public void setRsi(Double rsi) { this.rsi = rsi; }

    public Double getEma9() { return ema9; }
    public void setEma9(Double ema9) { this.ema9 = ema9; }

    public Double getEma21() { return ema21; }
    public void setEma21(Double ema21) { this.ema21 = ema21; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
}
