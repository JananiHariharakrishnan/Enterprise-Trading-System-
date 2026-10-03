"""
Extract orders from PostgreSQL into a CSV file.

The extraction is incremental using the ETL watermark stored in DuckDB.

Command:

    python -m analytics_etl.pipeline.extract

Example:

    python -m analytics_etl.pipeline.extract ^
        --pg-conn "postgresql+psycopg2://postgres:password@localhost:5432/trading"
"""

from __future__ import annotations

import argparse
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import duckdb
import pandas as pd
from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine


# ============================================================
# Configuration
# ============================================================

BASE_DIR = Path(__file__).resolve().parents[2]

DEFAULT_DATABASE_PATH = BASE_DIR / "analytics.duckdb"
DEFAULT_OUTPUT_PATH = BASE_DIR / "data" / "orders_raw.csv"
DEFAULT_PIPELINE_NAME = "orders"


# ============================================================
# PostgreSQL extraction query
# ============================================================
#
# IMPORTANT:
# orders does NOT have executed_price.
#
# Actual execution price comes from:
#
#     executions.price
#
# Therefore we LEFT JOIN executions.
#
# LEFT JOIN is intentional because an order can exist without
# an execution yet, for example NEW, REJECTED or CANCELLED.
#
# ============================================================

DEFAULT_QUERY = """
                SELECT
                    o.id AS source_order_id,
                    o.account_id,
                    o.symbol,
                    o.side,
                    o.qty AS quantity,
                    o.price,
                    e.price AS executed_price,
                    o.status,
                    o.created_on AS created_at
                FROM orders o
                         LEFT JOIN executions e
                                   ON e.order_id = o.id
                WHERE
                    (
                        :last_watermark IS NULL
                            OR o.created_on > CAST(:last_watermark AS TIMESTAMP)
                        )
                ORDER BY
                    o.created_on,
                    o.id,
                    e.id \
                """


EXPECTED_COLUMNS = [
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


# ============================================================
# Watermark table
# ============================================================

CREATE_WATERMARK_TABLE_SQL = """
                             CREATE TABLE IF NOT EXISTS etl_watermark (
                                                                          pipeline_name VARCHAR PRIMARY KEY,
                                                                          last_created_at TIMESTAMP,
                                                                          updated_at TIMESTAMP
                             ) \
                             """


# ============================================================
# Database helpers
# ============================================================

def get_duckdb_connection(database_path: Path) -> duckdb.DuckDBPyConnection:
    """
    Open the DuckDB analytics database.
    """
    database_path.parent.mkdir(parents=True, exist_ok=True)

    return duckdb.connect(str(database_path))


def ensure_watermark_table(
        connection: duckdb.DuckDBPyConnection,
) -> None:
    """
    Create the watermark table if it does not already exist.
    """

    connection.execute(CREATE_WATERMARK_TABLE_SQL)


def get_last_watermark(
        connection: duckdb.DuckDBPyConnection,
        pipeline_name: str,
) -> datetime | None:
    """
    Get the last successful extraction timestamp.

    Returns None when the pipeline has never run.
    """

    ensure_watermark_table(connection)

    result = connection.execute(
        """
        SELECT last_created_at
        FROM etl_watermark
        WHERE pipeline_name = ?
        """,
        [pipeline_name],
    ).fetchone()

    if result is None:
        return None

    return result[0]


def update_watermark(
        connection: duckdb.DuckDBPyConnection,
        pipeline_name: str,
        last_created_at: datetime,
) -> None:
    """
    Update the ETL watermark.

    The timestamp is generated in Python instead of using
    CURRENT_TIMESTAMP inside DuckDB SQL. This avoids DuckDB
    binding issues around CURRENT_TIMESTAMP.
    """

    ensure_watermark_table(connection)

    updated_at = datetime.now(timezone.utc).replace(tzinfo=None)

    connection.execute(
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
            last_created_at,
            updated_at,
        ],
    )


# ============================================================
# PostgreSQL helpers
# ============================================================

def create_postgres_engine(pg_conn: str) -> Engine:
    """
    Create SQLAlchemy PostgreSQL engine.
    """

    if not pg_conn:
        raise ValueError(
            "PostgreSQL connection string is required. "
            "Use --pg-conn or PG_CONN."
        )

    return create_engine(
        pg_conn,
        pool_pre_ping=True,
    )


def mask_connection_string(connection_string: str) -> str:
    """
    Hide the password when displaying the connection string.
    """

    if "@" not in connection_string:
        return connection_string

    try:
        prefix, suffix = connection_string.split("@", 1)

        if "://" in prefix and ":" in prefix:
            scheme, credentials = prefix.split("://", 1)

            if ":" in credentials:
                username = credentials.split(":", 1)[0]

                return (
                    f"{scheme}://"
                    f"{username}:****@{suffix}"
                )
    except Exception:
        pass

    return connection_string


# ============================================================
# Extraction
# ============================================================

def extract_orders(
        pg_conn: str,
        database_path: Path,
        output_path: Path,
        pipeline_name: str = DEFAULT_PIPELINE_NAME,
        advance_watermark: bool = True,
) -> pd.DataFrame:
    """
    Extract orders incrementally from PostgreSQL.

    Returns the extracted DataFrame.
    """

    # --------------------------------------------------------
    # Open DuckDB
    # --------------------------------------------------------

    duckdb_connection = get_duckdb_connection(database_path)

    try:
        ensure_watermark_table(duckdb_connection)

        last_watermark = get_last_watermark(
            duckdb_connection,
            pipeline_name,
        )

        print(
            f"Last watermark: {last_watermark}"
        )

        # ----------------------------------------------------
        # PostgreSQL connection
        # ----------------------------------------------------

        print(
            "Connecting to PostgreSQL:",
            mask_connection_string(pg_conn),
        )

        engine = create_postgres_engine(pg_conn)

        try:
            # ------------------------------------------------
            # Execute extraction query
            # ------------------------------------------------

            with engine.connect() as connection:

                result = connection.execute(
                    text(DEFAULT_QUERY),
                    {
                        "last_watermark": last_watermark,
                    },
                )

                rows = result.fetchall()

                columns = list(result.keys())

            # ------------------------------------------------
            # Build DataFrame
            # ------------------------------------------------

            df = pd.DataFrame(
                rows,
                columns=columns,
            )

            # ------------------------------------------------
            # Validate columns
            # ------------------------------------------------

            missing_columns = [
                column
                for column in EXPECTED_COLUMNS
                if column not in df.columns
            ]

            if missing_columns:
                raise RuntimeError(
                    "Extraction returned unexpected columns. "
                    f"Missing: {missing_columns}. "
                    f"Received: {list(df.columns)}"
                )

            # Keep only the expected columns in a predictable order.

            df = df[EXPECTED_COLUMNS]

            # ------------------------------------------------
            # Create output directory
            # ------------------------------------------------

            output_path.parent.mkdir(
                parents=True,
                exist_ok=True,
            )

            # ------------------------------------------------
            # Write CSV
            # ------------------------------------------------

            df.to_csv(
                output_path,
                index=False,
            )

            print(
                f"Extracted {len(df)} rows."
            )

            print(
                f"Raw CSV written to: {output_path}"
            )

            # ------------------------------------------------
            # Update watermark
            # ------------------------------------------------
            #
            # Only update the watermark when rows were actually
            # extracted.
            #
            # ------------------------------------------------

            if not df.empty and advance_watermark:

                df["created_at"] = pd.to_datetime(
                    df["created_at"],
                    errors="coerce",
                )

                max_created_at = df["created_at"].max()

                if pd.notna(max_created_at):

                    # Convert pandas Timestamp to Python datetime.
                    max_created_at_python = max_created_at.to_pydatetime()

                    update_watermark(
                        duckdb_connection,
                        pipeline_name,
                        max_created_at_python,
                    )

                    print(
                        "Watermark updated to:",
                        max_created_at_python,
                    )

            elif df.empty:
                print(
                    "No new orders found. "
                    "Watermark was not changed."
                )
            else:
                print(
                    "Rows extracted successfully. "
                    "Watermark update deferred to downstream load step."
                )

            return df

        finally:
            engine.dispose()

    finally:
        duckdb_connection.close()


# ============================================================
# CLI
# ============================================================

def parse_args() -> argparse.Namespace:
    """
    Parse command-line arguments.
    """

    parser = argparse.ArgumentParser(
        description="Extract orders from PostgreSQL."
    )

    parser.add_argument(
        "--pg-conn",
        default=os.getenv("PG_CONN"),
        help=(
            "PostgreSQL SQLAlchemy connection string. "
            "Can also be supplied through PG_CONN."
        ),
    )

    parser.add_argument(
        "--database",
        type=Path,
        default=DEFAULT_DATABASE_PATH,
        help="DuckDB analytics database path.",
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help="Output CSV path.",
    )

    parser.add_argument(
        "--pipeline-name",
        default=DEFAULT_PIPELINE_NAME,
        help="Name of the ETL pipeline.",
    )

    return parser.parse_args()


# ============================================================
# Main
# ============================================================

def main() -> None:
    """
    CLI entry point.
    """

    load_dotenv()

    args = parse_args()

    if not args.pg_conn:
        raise SystemExit(
            "ERROR: PostgreSQL connection string not provided.\n"
            "Set PG_CONN or use --pg-conn."
        )

    try:

        extract_orders(
            pg_conn=args.pg_conn,
            database_path=args.database,
            output_path=args.output,
            pipeline_name=args.pipeline_name,
        )

        print("Extraction completed successfully.")

    except Exception as exc:

        print(
            f"Extraction failed: {exc}"
        )

        raise SystemExit(1) from exc


if __name__ == "__main__":
    main()