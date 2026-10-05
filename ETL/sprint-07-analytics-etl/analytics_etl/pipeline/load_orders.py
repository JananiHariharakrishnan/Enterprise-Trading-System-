from __future__ import annotations

import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import duckdb
import pandas as pd


# Default database path
DATABASE_PATH = os.getenv(
    "ANALYTICS_DATABASE_PATH",
    str(Path(__file__).resolve().parents[2] / "analytics.duckdb"),
)


# ---------------------------------------------------------------------
# Business constants
# ---------------------------------------------------------------------
ALLOWED_SIDES = {"BUY", "SELL"}
ALLOWED_STATUSES = {"NEW", "FILLED", "REJECTED", "CANCELLED"}

# DLQ reason constants (keeps strings consistent across code/tests)
REASON_MISSING_SOURCE_ORDER_ID = "missing_source_order_id"
REASON_MISSING_ACCOUNT_ID = "missing_account_id"
REASON_MISSING_SYMBOL = "missing_symbol"
REASON_MISSING_SIDE = "missing_side"
REASON_INVALID_QUANTITY = "invalid_quantity"
REASON_INVALID_PRICE = "invalid_price"
REASON_MISSING_STATUS = "missing_status"
REASON_INVALID_STATUS = "invalid_status"
REASON_INVALID_SIDE = "invalid_side"
REASON_INVALID_CREATED_AT = "invalid_created_at"
REASON_TRANSFORM_ERROR = "transform_error"
REASON_MISSING_DIM_ACCOUNT = "missing_dim_account"
REASON_MISSING_DIM_INSTRUMENT = "missing_dim_instrument"
REASON_MISSING_DIM_DATE = "missing_dim_date"


# ---------------------------------------------------------------------
# Utility functions
# ---------------------------------------------------------------------

def _utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _stable_key(value: Any) -> int:
    """
    Generate a deterministic positive BIGINT-compatible identifier.
    """
    digest = hashlib.sha256(str(value).encode("utf-8")).hexdigest()
    # use a 15 hex-digit slice (fits comfortably and deterministic)
    return int(digest[:15], 16)


def _is_null(value: Any) -> bool:
    """
    Safely check None/NaN/NaT values, including numpy scalar values.
    """
    if value is None:
        return True

    try:
        result = pd.isna(value)

        if isinstance(result, bool):
            return result

        if hasattr(result, "item"):
            try:
                return bool(result.item())
            except (ValueError, TypeError):
                return False

        return False
    except (TypeError, ValueError):
        return False


def _to_int(value: Any) -> int | None:
    if _is_null(value):
        return None

    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _to_decimal(value: Any) -> float | None:
    if _is_null(value):
        return None

    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _to_datetime(value: Any) -> datetime | None:
    if _is_null(value):
        return None

    try:
        parsed = pd.to_datetime(value, errors="coerce", utc=True)

        if pd.isna(parsed):
            return None

        # Convert pandas Timestamp to a timezone-naive UTC datetime
        return parsed.to_pydatetime().replace(tzinfo=None)
    except (TypeError, ValueError, OverflowError):
        return None


def _json_payload(row: dict[str, Any]) -> str:
    """
    Serialize a row safely for the dead-letter table.
    """

    def default_serializer(value: Any) -> str:
        if isinstance(value, (datetime, pd.Timestamp)):
            return value.isoformat()

        return str(value)

    return json.dumps(
        row,
        default=default_serializer,
        ensure_ascii=False,
        sort_keys=True,
    )


# ---------------------------------------------------------------------
# Schema
# ---------------------------------------------------------------------

def _create_schema(conn: duckdb.DuckDBPyConnection) -> None:
    """
    Create all required tables.

    DuckDB versions may not support GENERATED AS IDENTITY constraints,
    therefore IDs are generated explicitly in Python.
    """

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS dim_date (
                                                date_key DATE PRIMARY KEY,
                                                year INTEGER,
                                                month INTEGER,
                                                day INTEGER,
                                                quarter INTEGER,
                                                week INTEGER,
                                                day_of_week INTEGER,
                                                loaded_at TIMESTAMP
        )
        """
    )

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS dim_instrument (
                                                      symbol VARCHAR PRIMARY KEY,
                                                      instrument_name VARCHAR,
                                                      loaded_at TIMESTAMP
        )
        """
    )

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS dim_account (
                                                   account_id BIGINT PRIMARY KEY,
                                                   loaded_at TIMESTAMP
        )
        """
    )

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS fact_trades (
                                                   trade_id BIGINT PRIMARY KEY,
                                                   source_order_id VARCHAR UNIQUE,
                                                   account_id BIGINT,
                                                   symbol VARCHAR,
                                                   side VARCHAR,
                                                   quantity BIGINT,
                                                   price DECIMAL(18, 4),
            executed_price DECIMAL(18, 4),
            trade_value DECIMAL(18, 4),
            status VARCHAR,
            created_at TIMESTAMP,
            loaded_at TIMESTAMP
            )
        """
    )

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS dlq_orders (
                                                  id BIGINT,
                                                  batch_id VARCHAR,
                                                  reason VARCHAR,
                                                  source_order_id VARCHAR,
                                                  payload VARCHAR,
                                                  created_at TIMESTAMP
        )
        """
    )

    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS etl_watermark (
                                                     pipeline_name VARCHAR PRIMARY KEY,
                                                     last_created_at TIMESTAMP,
                                                     updated_at TIMESTAMP
        )
        """
    )


# ---------------------------------------------------------------------
# Watermark
# ---------------------------------------------------------------------

def _get_watermark(
        conn: duckdb.DuckDBPyConnection,
        pipeline_name: str = "orders",
) -> datetime | None:
    row = conn.execute(
        """
        SELECT last_created_at
        FROM etl_watermark
        WHERE pipeline_name = ?
        """,
        [pipeline_name],
    ).fetchone()

    if row is None:
        return None

    return row[0]


def _set_watermark(
        conn: duckdb.DuckDBPyConnection,
        watermark: datetime,
        pipeline_name: str = "orders",
) -> None:
    conn.execute(
        """
        INSERT INTO etl_watermark (
            pipeline_name,
            last_created_at,
            updated_at
        )
        VALUES (?, ?, ?)
            ON CONFLICT (pipeline_name)
        DO UPDATE SET
            last_created_at = excluded.last_created_at,
                           updated_at = excluded.updated_at
        """,
        [
            pipeline_name,
            watermark,
            _utc_now(),
        ],
    )


# ---------------------------------------------------------------------
# Dimension loading
# ---------------------------------------------------------------------

def _upsert_dim_date_range(
        conn: duckdb.DuckDBPyConnection,
        dates: pd.Series,
) -> None:
    valid_dates = []

    for value in dates:
        parsed = _to_datetime(value)

        if parsed is not None:
            valid_dates.append(parsed.date())

    for date_value in sorted(set(valid_dates)):
        conn.execute(
            """
            INSERT INTO dim_date (
                date_key,
                year,
                month,
                day,
                quarter,
                week,
                day_of_week,
                loaded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (date_key)
            DO UPDATE SET
                                   year = excluded.year,
                                   month = excluded.month,
                                   day = excluded.day,
                                   quarter = excluded.quarter,
                                   week = excluded.week,
                                   day_of_week = excluded.day_of_week,
                                   loaded_at = excluded.loaded_at
            """,
            [
                date_value,
                date_value.year,
                date_value.month,
                date_value.day,
                ((date_value.month - 1) // 3) + 1,
                date_value.isocalendar().week,
                date_value.weekday(),
                _utc_now(),
                ],
        )


def _upsert_dim_instrument(
        conn: duckdb.DuckDBPyConnection,
        orders: pd.DataFrame,
) -> None:
    if "symbol" not in orders.columns:
        return

    symbols = (
        orders["symbol"]
        .dropna()
        .astype(str)
        .str.strip()
    )

    for symbol in sorted(set(symbols)):
        if not symbol:
            continue

        conn.execute(
            """
            INSERT INTO dim_instrument (
                symbol,
                instrument_name,
                loaded_at
            )
            VALUES (?, ?, ?)
                ON CONFLICT (symbol)
            DO UPDATE SET
                instrument_name = excluded.instrument_name,
                                   loaded_at = excluded.loaded_at
            """,
            [
                symbol,
                symbol,
                _utc_now(),
            ],
        )


def _upsert_dim_account(
        conn: duckdb.DuckDBPyConnection,
        orders: pd.DataFrame,
) -> None:
    if "account_id" not in orders.columns:
        return

    for value in orders["account_id"]:
        account_id = _to_int(value)

        if account_id is None:
            continue

        conn.execute(
            """
            INSERT INTO dim_account (
                account_id,
                loaded_at
            )
            VALUES (?, ?)
                ON CONFLICT (account_id)
            DO UPDATE SET
                loaded_at = excluded.loaded_at
            """,
            [
                account_id,
                _utc_now(),
            ],
        )


# ---------------------------------------------------------------------
# Dead-letter handling
# ---------------------------------------------------------------------

def _dead_letter(
        conn: duckdb.DuckDBPyConnection,
        row: dict[str, Any],
        reason: str,
        batch_id: str,
) -> None:
    source_order_id = (
            row.get("source_order_id")
            or row.get("id")
            or row.get("order_id")
    )

    payload = _json_payload(row)

    dead_letter_id = _stable_key(
        f"{batch_id}|{source_order_id}|{reason}|{payload}"
    )

    conn.execute(
        """
        INSERT INTO dlq_orders (
            id,
            batch_id,
            reason,
            source_order_id,
            payload,
            created_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            dead_letter_id,
            batch_id,
            reason,
            None if _is_null(source_order_id) else str(source_order_id),
            payload,
            _utc_now(),
        ],
    )


# ---------------------------------------------------------------------
# Input normalization
# ---------------------------------------------------------------------

def _normalize_columns(orders: pd.DataFrame) -> pd.DataFrame:
    df = orders.copy()

    aliases = {
        "created_on": "created_at",
        "createdAt": "created_at",
        "order_id": "source_order_id",
        "id": "source_order_id",
        "qty": "quantity",
        "executedPrice": "executed_price",
    }

    for source, target in aliases.items():
        if target not in df.columns and source in df.columns:
            df[target] = df[source]

    required_columns = [
        "source_order_id",
        "account_id",
        "symbol",
        "side",
        "quantity",
        "price",
        "executed_price",
        "status",
        "created_at",
    ]

    for column in required_columns:
        if column not in df.columns:
            df[column] = None

    return df


# ---------------------------------------------------------------------
# Validation and transformation
# ---------------------------------------------------------------------

def _validate_and_prepare(
        row: dict[str, Any],
) -> tuple[dict[str, Any] | None, str | None]:
    """
    Validate and prepare a single raw row.

    Returns (prepared_row_dict, None) on success, or (None, reason) on validation failure.
    """

    try:
        source_order_id = row.get("source_order_id")
        account_id = _to_int(row.get("account_id"))
        symbol = row.get("symbol")
        side = row.get("side")
        quantity = _to_int(row.get("quantity"))
        price = _to_decimal(row.get("price"))
        executed_price = _to_decimal(row.get("executed_price"))
        status = row.get("status")
        created_at = _to_datetime(row.get("created_at"))

        if _is_null(source_order_id) or str(source_order_id).strip() == "":
            return None, REASON_MISSING_SOURCE_ORDER_ID

        if account_id is None:
            return None, REASON_MISSING_ACCOUNT_ID

        if _is_null(symbol) or str(symbol).strip() == "":
            return None, REASON_MISSING_SYMBOL

        if _is_null(side) or str(side).strip() == "":
            return None, REASON_MISSING_SIDE

        if quantity is None or quantity <= 0:
            return None, REASON_INVALID_QUANTITY

        # require strictly positive price
        if price is None or price <= 0:
            return None, REASON_INVALID_PRICE

        if _is_null(status) or str(status).strip() == "":
            return None, REASON_MISSING_STATUS

        if created_at is None:
            return None, REASON_INVALID_CREATED_AT

        # Normalise and enforce allowed side/status
        symbol = str(symbol).strip()
        side = str(side).strip().upper()
        if side not in ALLOWED_SIDES:
            return None, REASON_INVALID_SIDE

        status = str(status).strip().upper()
        # We still accept rejected/cancelled etc as valid statuses (per acceptance),
        # but enforce the set listed in ALLOWED_STATUSES.
        if status not in ALLOWED_STATUSES:
            return None, REASON_INVALID_STATUS

        if executed_price is None:
            executed_price = price

        trade_value = round(quantity * executed_price, 4)

        prepared = {
            "source_order_id": str(source_order_id),
            "account_id": account_id,
            "symbol": symbol,
            "side": side,
            "quantity": quantity,
            "price": price,
            "executed_price": executed_price,
            "trade_value": trade_value,
            "status": status,
            "created_at": created_at,
        }

        return prepared, None

    except Exception as exc:
        # Any unexpected transform error should dead-letter the row with the error message
        return None, f"{REASON_TRANSFORM_ERROR}: {str(exc)}"


# ---------------------------------------------------------------------
# Fact loading
# ---------------------------------------------------------------------

def _upsert_fact_trade(
        conn: duckdb.DuckDBPyConnection,
        trade: dict[str, Any],
) -> None:
    trade_id = _stable_key(trade["source_order_id"])

    conn.execute(
        """
        INSERT INTO fact_trades (
            trade_id,
            source_order_id,
            account_id,
            symbol,
            side,
            quantity,
            price,
            executed_price,
            trade_value,
            status,
            created_at,
            loaded_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_order_id)
        DO UPDATE SET
            account_id = excluded.account_id,
                           symbol = excluded.symbol,
                           side = excluded.side,
                           quantity = excluded.quantity,
                           price = excluded.price,
                           executed_price = excluded.executed_price,
                           trade_value = excluded.trade_value,
                           status = excluded.status,
                           created_at = excluded.created_at,
                           loaded_at = excluded.loaded_at
        """,
        [
            trade_id,
            trade["source_order_id"],
            trade["account_id"],
            trade["symbol"],
            trade["side"],
            trade["quantity"],
            trade["price"],
            trade["executed_price"],
            trade["trade_value"],
            trade["status"],
            trade["created_at"],
            _utc_now(),
        ],
    )


# ---------------------------------------------------------------------
# Main pipeline (supports optional Postgres extraction)
# ---------------------------------------------------------------------

def load_orders(
        orders: pd.DataFrame | None = None,
        database_path: str = DATABASE_PATH,
        batch_id: str | None = None,
        pg_conn: str | None = None,
        pg_query: str | None = None,
        apply_watermark_filter: bool = True,
        advance_watermark: bool = True,
) -> tuple[int, int]:
    """
    Load orders incrementally into DuckDB.

    - If `orders` is a pandas.DataFrame it is loaded directly.
    - If `orders` is None, this function will extract incremental rows from PostgreSQL
      when `pg_conn` is provided (or env PG_CONN is set). Extraction uses the current
      watermark stored in the DuckDB analytics DB to fetch only new rows:
        WHERE created_on IS NULL OR created_on > last_watermark

    apply_watermark_filter:
        When True, filter incoming rows by the stored watermark.
        Set to False when rows were already incrementally extracted upstream.

    advance_watermark:
        When True, update watermark after successful load.

    Returns:
        processed_count, dead_letter_count
    """

    # Validate/normalize orders argument
    if orders is not None and not isinstance(orders, pd.DataFrame):
        orders = pd.DataFrame(orders)

    if batch_id is None:
        batch_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")

    # Open DuckDB connection (used for schema, watermark, dims, and writes)
    conn = duckdb.connect(database_path)

    try:
        # Ensure schema exists (creates etl_watermark etc.)
        _create_schema(conn)

        # If orders were not supplied, try to extract from Postgres
        pg_extracted = False
        if orders is None:
            # Discover PG connection string
            pg_conn_str = pg_conn or os.getenv("PG_CONN")
            if not pg_conn_str:
                raise ValueError(
                    "No orders provided and no Postgres connection string found. "
                    "Provide `orders` DataFrame or set PG_CONN / pass `pg_conn`."
                )

            # Get last watermark from DuckDB etl table
            last_wm = _get_watermark(conn)

            # Compose default query if none provided. We keep created_on as the name
            if pg_query is None:
                pg_query = """
                           SELECT
                               id AS id,
                               id AS source_order_id,
                               account_id,
                               symbol,
                               side,
                               qty,
                               price,
                               executed_price,
                               status,
                               created_on
                           FROM orders
                           WHERE 1=1 \
                           """

            # Add incremental predicate (keep NULL created_on rows for DLQ)
            params = {}
            if last_wm is not None:
                pg_query = (
                        pg_query.strip().rstrip(";")
                        + " AND (created_on IS NULL OR created_on > :last_wm) "
                )
                # Ensure last_wm is an ISO string / timezone-aware pandas-compatible value
                params["last_wm"] = pd.to_datetime(last_wm, utc=True)

            # Import SQLAlchemy lazily (only needed when extracting from Postgres)
            try:
                from sqlalchemy import create_engine, text  # type: ignore
            except Exception as exc:  # pragma: no cover - runtime error if module missing
                raise RuntimeError(
                    "To extract from Postgres you must install sqlalchemy and a Postgres driver: "
                    "'pip install sqlalchemy psycopg2-binary'"
                ) from exc

            engine = create_engine(pg_conn_str)
            # Read into pandas DataFrame
            with engine.connect() as pg_conn_conn:
                orders = pd.read_sql_query(text(pg_query), pg_conn_conn, params=params, parse_dates=["created_on"])
            pg_extracted = True

        # If we still have no rows after attempt to fetch -> nothing to do
        if orders is None or orders.empty:
            return 0, 0

        # Normalize input columns and timestamps (same logic as before)
        orders = _normalize_columns(orders)

        orders["created_at"] = pd.to_datetime(
            orders["created_at"],
            errors="coerce",
            utc=True,
        )

        # If extraction came from PG we already filtered by watermark. Avoid double-filter.
        if apply_watermark_filter and not pg_extracted:
            last_watermark = _get_watermark(conn)
            if last_watermark is not None:
                last_watermark = pd.to_datetime(last_watermark, errors="coerce", utc=True)
                if pd.notna(last_watermark):
                    valid_timestamp = orders["created_at"].notna()
                    orders = orders[
                        (~valid_timestamp)
                        | (orders["created_at"] > last_watermark)
                        ].copy()

        if orders.empty:
            return 0, 0

        # Continue with the existing pipeline: load dims, validate, upsert fact, watermark
        _upsert_dim_date_range(conn, orders["created_at"])
        _upsert_dim_instrument(conn, orders)
        _upsert_dim_account(conn, orders)

        processed_count = 0
        dead_letter_count = 0
        maximum_created_at = None

        for _, pandas_row in orders.iterrows():
            raw_row = pandas_row.to_dict()
            prepared, reason = _validate_and_prepare(raw_row)

            if prepared is None:
                _dead_letter(
                    conn=conn,
                    row=raw_row,
                    reason=reason or REASON_TRANSFORM_ERROR,
                    batch_id=batch_id,
                )
                dead_letter_count += 1
                continue

            # Referential integrity checks: ensure dims exist
            # 1) account
            acct_res = conn.execute(
                "SELECT account_id FROM dim_account WHERE account_id = ?",
                [prepared["account_id"]],
            ).fetchone()
            if acct_res is None:
                _dead_letter(
                    conn=conn,
                    row=raw_row,
                    reason=REASON_MISSING_DIM_ACCOUNT,
                    batch_id=batch_id,
                )
                dead_letter_count += 1
                continue

            # 2) instrument
            instr_res = conn.execute(
                "SELECT symbol FROM dim_instrument WHERE symbol = ?",
                [prepared["symbol"]],
            ).fetchone()
            if instr_res is None:
                _dead_letter(
                    conn=conn,
                    row=raw_row,
                    reason=REASON_MISSING_DIM_INSTRUMENT,
                    batch_id=batch_id,
                )
                dead_letter_count += 1
                continue

            # 3) date
            date_value = prepared["created_at"].date()
            date_res = conn.execute(
                "SELECT date_key FROM dim_date WHERE date_key = ?",
                [date_value],
            ).fetchone()
            if date_res is None:
                _dead_letter(
                    conn=conn,
                    row=raw_row,
                    reason=REASON_MISSING_DIM_DATE,
                    batch_id=batch_id,
                )
                dead_letter_count += 1
                continue

            # All checks passed -> upsert fact
            _upsert_fact_trade(conn, prepared)
            processed_count += 1

            created_at = prepared["created_at"]

            if maximum_created_at is None or created_at > maximum_created_at:
                maximum_created_at = created_at

        # Advance watermark to the maximum created_at seen (if any)
        if advance_watermark and maximum_created_at is not None:
            _set_watermark(conn, maximum_created_at)

        conn.commit()
        return processed_count, dead_letter_count

    except Exception:
        conn.rollback()
        raise

    finally:
        conn.close()