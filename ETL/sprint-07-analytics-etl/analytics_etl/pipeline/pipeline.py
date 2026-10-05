"""
Run the complete analytics ETL pipeline.

Command:

    python -m analytics_etl.pipeline.pipeline
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

from dotenv import load_dotenv

from analytics_etl.pipeline.extract import extract_orders
from analytics_etl.pipeline.load_orders import load_orders
from analytics_etl.pipeline.transform import transform_orders


DEFAULT_DATABASE_PATH = Path("analytics.duckdb")
DEFAULT_EXTRACTED_PATH = Path("data/extracted_orders.csv")
DEFAULT_VALID_PATH = Path("data/valid_orders.csv")
DEFAULT_INVALID_PATH = Path("data/invalid_orders.csv")


def run_pipeline(
        pg_conn: str,
        database_path: Path = DEFAULT_DATABASE_PATH,
        extracted_path: Path = DEFAULT_EXTRACTED_PATH,
        valid_path: Path = DEFAULT_VALID_PATH,
        invalid_path: Path = DEFAULT_INVALID_PATH,
) -> tuple[int, int]:
    """
    Run extract, transform, and load in sequence.
    """
    print("\n========== EXTRACT ==========")

    extracted_orders = extract_orders(
        pg_conn=pg_conn,
        database_path=database_path,
        output_path=extracted_path,
        advance_watermark=False,
    )

    print("\n========== TRANSFORM ==========")

    transform_orders(
        input_path=extracted_path,
        valid_output_path=valid_path,
        invalid_output_path=invalid_path,
    )

    print("\n========== LOAD ==========")

    result = load_orders(
        orders=extracted_orders,
        database_path=str(database_path),
        apply_watermark_filter=False,
        advance_watermark=True,
    )

    print("\n========== PIPELINE COMPLETE ==========")
    print(result)

    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the complete PostgreSQL-to-DuckDB ETL pipeline."
    )

    parser.add_argument(
        "--pg-conn",
        default=os.getenv("PG_CONN"),
        help="PostgreSQL SQLAlchemy connection string.",
    )

    parser.add_argument(
        "--database",
        type=Path,
        default=Path(
            os.getenv(
                "ANALYTICS_DATABASE_PATH",
                str(DEFAULT_DATABASE_PATH),
            )
        ),
        help="DuckDB database path.",
    )

    parser.add_argument(
        "--extracted",
        type=Path,
        default=DEFAULT_EXTRACTED_PATH,
        help="Extracted CSV path.",
    )

    parser.add_argument(
        "--valid",
        type=Path,
        default=DEFAULT_VALID_PATH,
        help="Valid transformed CSV path.",
    )

    parser.add_argument(
        "--invalid",
        type=Path,
        default=DEFAULT_INVALID_PATH,
        help="Invalid transformed CSV path.",
    )

    return parser.parse_args()


def main() -> None:
    load_dotenv()

    args = parse_args()

    if not args.pg_conn:
        raise SystemExit(
            "Missing PostgreSQL connection string. "
            "Set PG_CONN or pass --pg-conn."
        )

    run_pipeline(
        pg_conn=args.pg_conn,
        database_path=args.database,
        extracted_path=args.extracted,
        valid_path=args.valid,
        invalid_path=args.invalid,
    )


if __name__ == "__main__":
    main()