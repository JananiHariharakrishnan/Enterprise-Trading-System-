Sprint 07: Incremental ETL for FACT_TRADES

This sprint pipeline moves incremental orders from PostgreSQL into DuckDB analytics tables.

## Setup

Use the sprint folder as current working directory:

`Database/sprint-07-analytics-etl`

Install dependencies:

```cmd
pip install -r requirements.txt
```

Create a `.env` file (or set env vars in shell):

```env
PG_CONN=postgresql+psycopg2://postgres:password@localhost:5432/trading
ANALYTICS_DATABASE_PATH=analytics.duckdb
EXTRACTED_ORDERS_PATH=data/extracted_orders.csv
VALID_ORDERS_PATH=data/valid_orders.csv
INVALID_ORDERS_PATH=data/invalid_orders.csv
```

## Three stage commands (run each stage independently)

1) Extract incremental rows from PostgreSQL

```cmd
python -m analytics_etl.pipeline.extract
```

2) Transform and split valid/invalid rows

```cmd
python -m analytics_etl.pipeline.transform
```

3) Run end-to-end ETL (extract -> transform -> load)

```cmd
python -m analytics_etl.pipeline.pipeline
```

Notes:
- `pipeline.py` defers watermark update in extract and advances it only after successful load.
- `load_orders.py` loads dimensions in this order: `dim_date` -> `dim_instrument` -> `dim_account` -> `fact_trades`.
- Invalid rows are written to `dlq_orders` with `batch_id` and `reason`.

## Optional direct loader usage

Run loader directly from a DataFrame/CSV:

```cmd
python -c "from analytics_etl.pipeline.load_orders import load_orders; import pandas as pd; df = pd.read_csv('data/extracted_orders.csv'); print(load_orders(df))"
```

Extract directly inside `load_orders` (without separate extract stage):

```cmd
python -c "from analytics_etl.pipeline.load_orders import load_orders; print(load_orders(None))"
```

## Inspect DuckDB results

```cmd
python inspect_db.py
```
