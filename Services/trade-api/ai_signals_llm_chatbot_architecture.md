# End-to-End AI Signals, Kafka Pipeline & LLM Chatbot Architecture

## 1. Executive Summary & Core Design Philosophy

The AI Trading Intelligence system embedded in the platform provides **institutional-grade technical analysis and decision-support trade advice** to self-directed traders.

### Guiding Principles
* **Decision Support (Informational Only)**: *"It informs a customer who trades; it does not trade."* The system never executes transactions autonomously; the customer retains full control and execution responsibility.
* **One Defensible Methodology**: Rather than generating speculative or black-box predictions, the engine calculates two classic, auditable indicators:
  * **RSI(14)**: 14-period Wilder Relative Strength Index for overbought/oversold momentum exhaustion.
  * **EMA(9/21)**: 9-period fast EMA vs 21-period slow EMA crossover for trend direction.
* **Cadence vs Noise**: Recomputing on every tick produces noise and whipsaws. The system enforces a **60-second fixed cadence interval** per instrument.
* **Quota & Cost Management**: End-Of-Day (EOD) daily candlestick data from Fauxnance does not change during the trading day. Candles are cached for **24 hours (86,400s)**, eliminating redundant API calls.

---

## 2. End-to-End Architectural Blueprint

```mermaid
flowchart TD
    subgraph Market Data & Candle Ingestion
        MD["Kafka Topic: market-data (Live Quotes)"] --> KListener["KafkaListener (trade-api-llm-group)"]
        FX["Fauxnance API (GET /candles/{symbol})"] --> CCM["CandleCacheManager (24h Cache)"]
    end

    subgraph Quantitative Signal Engine
        KListener --> SCM["SignalCadenceManager (60s Cadence Throttle)"]
        SCM --> MCS["MarketContextStore (Rolling Closes Window)"]
        CCM -.->|Hydrate EOD lookback| MCS
        MCS --> TI["TechnicalIndicators (RSI-14, EMA-9, EMA-21)"]
        TI --> SE["SignalEngine"]
        SE --> LLMGate["LlmGateway (Gemini Flash LLM)"]
        LLMGate -.->|Fallback if offline| RuleEng["Deterministic Quantitative Rule Engine"]
    end

    subgraph Storage & Real-time Distribution
        SE --> TSS["TradeSignalStore (In-Memory Fast Cache)"]
        TSS -->|Upsert on symbol| PG[("PostgreSQL: tradingsystem.ai_signals")]
        SE -->|Publish JSON message| KSignal["Kafka Topic: trade-signals"]
    end

    subgraph Frontend Delivery & UI
        TSS --> SigCtrl["SignalController (/api/v1/signals)"]
        SigCtrl --> SigUI["Signals Dashboard (Table & Card Views)"]
        KSignal -.->|Real-time alerts| SigUI
    end

    subgraph Chatbot Conversational RAG Pipeline
        UserPrompt["User Chat Query"] --> CW["Chatbot Widget (Angular)"]
        CW --> CS["ChatService (/api/v1/chat)"]
        CS --> CCB["ChatContextBuilder"]
        
        AccountDB[("User Accounts / Positions / Orders DB")] -->|Portfolio Context| CCB
        TSS -->|signalStore.buildSummary()| CCB
        MDS["MarketDataService (Live Prices)"] -->|Price Context| CCB
        WLS["WatchlistService"] -->|Watchlist Context| CCB
        
        CCB -->|Grounded System Prompt| ChatLLM["LlmGateway (Gemini Flash Chatbot)"]
        ChatLLM --> CS
        CS -->|ChatMessageResponse (Reply + Related Signals)| CW
    end
```

---

## 3. Core Architectural Components & Responsibilities

### 3.1. Ingestion Pipeline
* **Live Quotes**: Arrive via the Kafka topic `market-data` (and related execution events on `orders` and `trade-events`). Handled by `SignalEngine.onMarketData()`.
* **Historical Lookback**: Managed by `FauxnanceCandleClient` and cached by `CandleCacheManager`. Once daily candles are fetched for a ticker, they are held in an in-memory cache with an 86,400-second TTL.

### 3.2. Quantitative Calculation (`SignalEngine`)
1. **Cadence & Cooldown**: Evaluates if 60 seconds have elapsed since the instrument's last recomputation via `SignalCadenceManager` and `SignalCooldownManager`.
2. **Context Assembly**: Retrieves lookback closes from `MarketContextStore` and appends the live price.
3. **Indicator Calculation**: Calculates Wilder RSI(14) and EMAs (9-period fast, 21-period slow).
4. **Synthesis**:
   * Evaluates trend: Bullish alignment ($EMA_9 \ge EMA_{21}$) vs Bearish breakdown ($EMA_9 < EMA_{21}$).
   * Evaluates momentum: Oversold ($RSI < 30$), Neutral ($30 \le RSI \le 70$), Overbought ($RSI > 70$).
   * Calls `LlmGateway` to draft an institutional 1-sentence thesis citing exact numbers. If the LLM is offline or returns generic responses, it falls back to the deterministic quantitative engine.

### 3.3. Persistence: PostgreSQL `tradingsystem.ai_signals` Table
The PostgreSQL table serves as the primary system of record for active signals across service reboots:

```sql
CREATE TABLE IF NOT EXISTS tradingsystem.ai_signals (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20)    NOT NULL,
    action        VARCHAR(10)    NOT NULL,  -- BUY | SELL | HOLD
    confidence    DECIMAL(5,4)   NOT NULL,  -- e.g. 0.8500 (85%)
    reasoning     TEXT           NOT NULL,  -- Quantitative 1-sentence thesis
    generated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_signals_symbol UNIQUE (symbol)
);

CREATE INDEX idx_ai_signals_symbol ON tradingsystem.ai_signals (symbol);
CREATE INDEX idx_ai_signals_generated_at ON tradingsystem.ai_signals (generated_at DESC);
```

* **Upsert Semantics**: Through `AiSignalMapper.upsertSignal()`, inserts write new symbols and update existing ones on conflict (`ON CONFLICT (symbol) DO UPDATE`).
* **Cache Hydration**: `TradeSignalStore` maintains a write-through `ConcurrentHashMap<String, AiSignal>`. Reads check memory first and fall back to PostgreSQL.

### 3.4. Kafka Topic: `trade-signals`
Whenever a signal is generated or updated, `SignalEngine.publishSignal()` broadcasts a message to Kafka:
* **Topic Name**: `trade-signals`
* **Partition Key**: `symbol` (ensuring ordering per ticker)
* **Payload Structure**:
  ```json
  {
    "symbol": "AAPL",
    "action": "BUY",
    "confidence": 0.88,
    "reasoning": "A BUY signal was generated due to bullish EMA alignment with the 9-period EMA at 155.20 exceeding the 21-period EMA at 151.10, supported by healthy momentum as RSI sits at 54.3 while the current price trades at 158.00.",
    "generatedAt": "2026-10-07T04:45:00Z",
    "rsi": 54.3,
    "ema9": 155.20,
    "ema21": 151.10,
    "currentPrice": 158.00,
    "methodology": "Wilder's RSI(14) & EMA(9/21) trend crossover on Fauxnance daily candles + live market-data quote",
    "disclaimer": "Informational trade advice only — informs a customer who trades; it does not trade or constitute legal financial advice.",
    "candleCacheDuration": "24 hours (86,400s) — static EOD candles"
  }
  ```

---

## 4. Chatbot Architecture & LLM RAG Grounding

The conversational trading assistant is not an isolated chatbot; it is **grounded in live platform state** via Retrieval-Augmented Generation (RAG).

```
User Query: "Should I buy more AAPL?"
       │
       ▼
ChatContextBuilder.build(accountId, userMessage)
       │
       ├─► Account & Portfolio: Cash ($480,000), Positions (100 AAPL @ $150.00), Orders
       ├─► ai_signals Table: AAPL BUY (88%) — EMA(9) > EMA(21), RSI 54.3
       ├─► Live Market Data: AAPL $158.00
       └─► User Watchlists: Tech Titans (AAPL, MSFT)
       │
       ▼
System Prompt sent to Gemini Flash LLM Gateway
       │
       ▼
Consistent, Auditable Answer (Referencing exact portfolio & platform signal)
```

### 4.1. Context Injection (`ChatContextBuilder`)
When a user submits a chat message, `ChatContextBuilder` builds a comprehensive prompt containing:
1. **Core Philosophy & Legal Guardrails**: Instructing the model that it only provides decision support and never guarantees returns or trades autonomously.
2. **User Portfolio State**: Buying power, cash balance, open positions, and last 5 order history entries.
3. **Active Signals Summary**: Formatted via `signalStore.buildSummary()`.
4. **Current Cached Market Prices**: From `MarketDataService`.
5. **Active User Watchlists**: From `WatchlistService`.

### 4.2. Actionable Signal Chips in Chatbot UI
When `ChatService` returns an answer:
1. It extracts recognized ticker symbols from the user message and LLM response.
2. It looks up the corresponding `AiSignal` from `TradeSignalStore`.
3. The Angular UI renders structured cards beneath the message bubble with quick-action **"Trade BUY"** or **"Trade SELL"** buttons pre-filling the order ticket.

### 4.3. Enterprise AI Guardrails Layer (`ChatGuardrailService`)
To guarantee regulatory compliance, system safety, and prompt defense, all chat interactions pass through a dedicated guardrail pipeline before reaching or leaving the LLM:

1. **Input Sanitization & Buffer Defense**: Messages are bounded to 1,000 characters and stripped of dangerous payloads to prevent prompt bloating and buffer exhaustion.
2. **Prompt Injection & Jailbreak Defense**: Detects adversarial injection patterns (e.g. `ignore previous instructions`, `DAN`, `reveal system prompt`) and immediately serves a secure compliance response without exposing tokens.
3. **Autonomous Execution Interceptor**: Users attempting to place orders via chat (*"Buy 50 shares of AAPL for me"*) are programmatically intercepted with an explanation that the assistant is decision support only (*"It informs a customer who trades; it does not trade"*). It directs them to the Order Ticket while still extracting the target stock's signal chip for convenience.
4. **Guaranteed Return / Risk-Free Disclaimer**: Refuses queries demanding guaranteed profits with SEC/FINRA-compliant disclaimers affirming that all trading involves risk and that AI signals are probabilistic momentum/trend models.
5. **Output Auditing**: Post-processes LLM replies to catch and append regulatory disclaimers if the model ever hallucinates having executed an order.

---

## 5. Data Integrity & Discrepancy Prevention

To prevent technical indicators (`EMA`, `RSI`, `SMA`, `MACD`, `VWAP`) or common English words (`YOUR`, `YOU`, `BUY`, `SELL`, `IN`, `TODAY`, `NOW`) from being mistaken for stock symbols:

1. **Strict Symbol Validation**: `TradeSignalStore.isValidSymbol()` maintains an extensive blacklist of indicator acronyms, order actions, prepositions, temporal words, and grammar tokens.
2. **Database Purge on Startup**: `TradeSignalStore.initTable()` runs automatic `DELETE` queries to clear legacy invalid symbols from `tradingsystem.ai_signals` and purges non-instrument rows.
3. **Generation Guard**: `SignalEngine` ignores non-ticker candidates before attempting calculations or fallback price seeding.
4. **Instrument Universe Check**: `ChatService.isRecognizedInstrument()` verifies that candidate symbols actually exist in `tradingsystem.instruments` or the reference universe before pre-generating or referencing them.
5. **Frontend Safety Filter**: `ChatbotWidgetComponent` filters out non-tickers so chips like `Trade IN` or `Trade TODAY` are never displayed.

---

## 6. Summary Comparison

| Aspect | `SignalEngine` | `ai_signals` Table | `trade-signals` Topic | Chatbot Assistant |
| :--- | :--- | :--- | :--- | :--- |
| **Type** | Processing Service | PostgreSQL Table | Kafka Event Stream | Conversational RAG Service |
| **Cadence** | 60s per instrument | Continuous Upserts | Event-driven on recomputation | On user interaction |
| **Input** | Quotes + EOD Candles | `AiSignal` entities | Serialized JSON events | User text + Enriched Context |
| **Consumers** | Kafka listeners | `TradeSignalStore` & API | Real-time subscribers / SSE | Traders via Web UI |
| **Role** | Computes indicators | Stores latest state | Real-time broadcast | Explains rationale & context |
