# Database/sprint-07-analytics-etl/tests/test_load_orders.py
import sys
from pathlib import Path

# Make the project root importable when this file is run directly.
PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
import duckdb
import pandas as pd

from analytics_etl.pipeline.load_orders import (
    load_orders,
    DATABASE_PATH,
)

def _db_path_in_temp(tmpdir):
    return Path(tmpdir) / "analytics.duckdb"

def test_incremental_load_populates_fact(tmp_path):
    db_path = tmp_path / "analytics.duckdb"
    # build a simple orders DataFrame
    df = pd.DataFrame([
        {
            "id": "order-1",
            "source_order_id": "order-1",
            "account_id": 100,
            "symbol": "FOO.NS",
            "side": "BUY",
            "qty": 10,
            "price": 50.0,
            "status": "FILLED",
            "executed_price": 49.5,
            "created_on": "2026-09-01 10:00:00"
        }
    ])
    processed, dead = load_orders(df, database_path=str(db_path))
    assert processed == 1
    assert dead == 0

    conn = duckdb.connect(str(db_path))
    try:
        cnt = conn.execute("SELECT count(*) FROM fact_trades").fetchone()[0]
        assert cnt == 1
    finally:
        conn.close()

def test_second_load_with_no_new_data_adds_no_rows(tmp_path):
    db_path = tmp_path / "analytics.duckdb"
    df = pd.DataFrame([
        {
            "id": "order-2",
            "source_order_id": "order-2",
            "account_id": 200,
            "symbol": "BAR.NS",
            "side": "SELL",
            "qty": 5,
            "price": 20.0,
            "status": "NEW",
            "created_on": "2026-09-02 09:00:00"
        }
    ])
    processed1, dead1 = load_orders(df, database_path=str(db_path))
    assert processed1 == 1
    assert dead1 == 0

    # re-run same data -> should not create duplicate rows
    processed2, dead2 = load_orders(df, database_path=str(db_path))
    # processed2 may be 1 (upsert performed) but count should remain 1 in table
    conn = duckdb.connect(str(db_path))
    try:
        cnt = conn.execute("SELECT count(*) FROM fact_trades").fetchone()[0]
        assert cnt == 1
    finally:
        conn.close()

def test_transform_handles_nulls_and_type_mismatches(tmp_path):
    db_path = tmp_path / "analytics.duckdb"
    df = pd.DataFrame([
        {
            "id": "order-3",
            "source_order_id": "order-3",
            "account_id": 300,
            "symbol": "BAZ.NS",
            "side": "BUY",
            "qty": "2",            # string - should coerce
            "price": "15.5",      # string - should coerce
            "status": "FILLED",
            "executed_price": None,
            "created_on": "2026-09-03 12:00:00"
        }
    ])
    processed, dead = load_orders(df, database_path=str(db_path))
    assert processed == 1
    assert dead == 0

    conn = duckdb.connect(str(db_path))
    try:
        rv = conn.execute("SELECT quantity, price, trade_value FROM fact_trades WHERE source_order_id = 'order-3'").fetchall()
        assert rv and rv[0][0] == 2
        assert float(rv[0][1]) == float(15.5)
        assert float(rv[0][2]) == round(2 * 15.5, 2)
    finally:
        conn.close()

def test_invalid_row_dead_lettered_and_load_continues(tmp_path):
    db_path = tmp_path / "analytics.duckdb"
    df = pd.DataFrame([
        {
            "id": "order-4",
            "source_order_id": "order-4",
            "account_id": None,   # invalid -> should be dead-lettered
            "symbol": "QUX.NS",
            "side": "BUY",
            "qty": 1,
            "price": 10.0,
            "status": "FILLED",
            "created_on": "2026-09-04 11:00:00"
        },
        {
            "id": "order-5",
            "source_order_id": "order-5",
            "account_id": 500,
            "symbol": "QIX.NS",
            "side": "SELL",
            "qty": 3,
            "price": 12.0,
            "status": "FILLED",
            "created_on": "2026-09-04 11:05:00"
        }
    ])
    processed, dead = load_orders(df, database_path=str(db_path))
    # one valid row, one dead-lettered
    assert processed == 1
    assert dead == 1

    conn = duckdb.connect(str(db_path))
    try:
        fact_cnt = conn.execute("SELECT count(*) FROM fact_trades").fetchone()[0]
        dlq_cnt = conn.execute("SELECT count(*) FROM dlq_orders").fetchone()[0]
        assert fact_cnt == 1
        assert dlq_cnt == 1
        reason = conn.execute("SELECT reason FROM dlq_orders LIMIT 1").fetchone()[0]
        assert "missing_account_id" in reason or reason == "missing_account_id"
    finally:
        conn.close()