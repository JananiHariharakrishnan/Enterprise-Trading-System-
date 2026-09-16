Poller interval and Fauxnance daily quota calculation
===============================================

Goal: remain within Fauxnance API limit of 2,000 requests per day.

Definitions
- DAILY_REQUEST_QUOTA = 2,000 requests/day
- MAX_SYMBOLS_PER_REQUEST = 25 (we batch up to 25 symbols per HTTP call)
- SECONDS_PER_DAY = 86,400

Formulas

1) requestsPerPoll(symbolCount) = ceil(symbolCount / MAX_SYMBOLS_PER_REQUEST)

2) requestsPerDay(symbolCount, intervalSeconds) = requestsPerPoll(symbolCount) * SECONDS_PER_DAY / intervalSeconds

3) minimumIntervalSeconds(symbolCount) = ceil(requestsPerPoll(symbolCount) * SECONDS_PER_DAY / DAILY_REQUEST_QUOTA)

Examples

- If symbolCount = 8 and intervalSeconds = 60:
  - requestsPerPoll = ceil(8 / 25) = 1
  - requestsPerDay = 1 * 86,400 / 60 = 1,440 requests/day (inside 2,000 quota)

- If symbolCount = 8 and no batching (batch size = 1) at intervalSeconds = 30:
  - requestsPerPoll = 8
  - requestsPerDay = 8 * 86,400 / 30 = 23,040 requests/day (exceeds quota)

- If symbolCount = 100 and intervalSeconds = 60 with batch size 25:
  - requestsPerPoll = ceil(100/25) = 4
  - requestsPerDay = 4 * 86,400 / 60 = 5,760 requests/day (exceeds quota)
  - minimumIntervalSeconds(100) = ceil(4 * 86,400 / 2,000) = ceil(172.8) = 173 seconds

Repository location: trade-executor/design/POLLER_INTERVAL_CALCULATION.md

The implementation enforces the interval floor in code and validates each poll's projected daily requests. See class: com.tradingsystem.poller.MarketDataPoller

