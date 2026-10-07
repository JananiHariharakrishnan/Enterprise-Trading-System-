package com.tradingsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * REST response DTO for a single AI-generated trade signal.
 * Represents an informational decision-support view on an instrument:
 * - Direction: action (BUY | SELL | HOLD)
 * - Strength: confidence (0.0 – 1.0)
 * - Sentence saying what produced it: reasoning
 * - Numbers behind it: rsi, ema9, ema21, currentPrice
 * - Methodology: single defensible quantitative methodology
 * - Legal disclaimer: informs a customer who trades; does not trade.
 */
public class TradeSignalResponse {

    private String symbol;
    private String action;       // BUY | SELL | HOLD
    private double confidence;   // 0.0 – 1.0
    private String reasoning;    // Sentence stating what produced it and the numbers behind it

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant generatedAt;

    private String methodology = "Wilder's RSI(14) & EMA(9/21) trend crossover on Fauxnance daily candles + live market-data quote";
    private String disclaimer = "Informational trade advice only — informs a customer who trades; it does not trade or constitute legal financial advice.";
    private Double rsi;
    private Double ema9;
    private Double ema21;
    private Double currentPrice;
    private String candleCacheDuration = "24 hours (86,400s) — static EOD candles";

    public TradeSignalResponse() {}

    public TradeSignalResponse(String symbol, String action, double confidence,
                               String reasoning, Instant generatedAt) {
        this.symbol = symbol;
        this.action = action;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.generatedAt = generatedAt;
    }

    public TradeSignalResponse(String symbol, String action, double confidence,
                               String reasoning, Instant generatedAt,
                               Double rsi, Double ema9, Double ema21, Double currentPrice,
                               String methodology, String disclaimer, String candleCacheDuration) {
        this.symbol = symbol;
        this.action = action;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.generatedAt = generatedAt;
        this.rsi = rsi;
        this.ema9 = ema9;
        this.ema21 = ema21;
        this.currentPrice = currentPrice;
        if (methodology != null) this.methodology = methodology;
        if (disclaimer != null) this.disclaimer = disclaimer;
        if (candleCacheDuration != null) this.candleCacheDuration = candleCacheDuration;
    }

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

    public String getMethodology() { return methodology; }
    public void setMethodology(String methodology) { this.methodology = methodology; }

    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }

    public Double getRsi() { return rsi; }
    public void setRsi(Double rsi) { this.rsi = rsi; }

    public Double getEma9() { return ema9; }
    public void setEma9(Double ema9) { this.ema9 = ema9; }

    public Double getEma21() { return ema21; }
    public void setEma21(Double ema21) { this.ema21 = ema21; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

    public String getCandleCacheDuration() { return candleCacheDuration; }
    public void setCandleCacheDuration(String candleCacheDuration) { this.candleCacheDuration = candleCacheDuration; }
}
