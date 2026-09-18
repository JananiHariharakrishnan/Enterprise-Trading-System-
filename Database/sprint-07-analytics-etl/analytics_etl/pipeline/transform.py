"""
Transform and validate extracted orders.

Command:

    python -m analytics_etl.pipeline.transform
"""

from __future__ import annotations

import argparse
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd


DEFAULT_INPUT_PATH = Path("data/extracted_orders.csv")
DEFAULT_VALID_OUTPUT_PATH = Path("data/valid_orders.csv")
DEFAULT_INVALID_OUTPUT_PATH = Path("data/invalid_orders.csv")

VALID_SIDES = {"BUY", "SELL"}

VALID_STATUSES = {
    "NEW",
    "FILLED",
    "REJECTED",
    "CANCELLED",
}


def ensure_parent_directory(path: Path) -> None:
    """Create the parent directory for a file if necessary."""
    path.parent.mkdir(parents=True, exist_ok=True)


def create_batch_id() -> str:
    """Create a unique batch identifier."""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    return f"orders-{timestamp}-{uuid.uuid4().hex[:8]}"


def first_existing_column(
        dataframe: pd.DataFrame,
        candidates: list[str],
) -> str | None:
    """Return the first candidate column present in a DataFrame."""
    for candidate in candidates:
        if candidate in dataframe.columns:
            return candidate

    return None


def normalize_columns(dataframe: pd.DataFrame) -> pd.DataFrame:
    """
    Normalize common source column aliases into canonical names.
    """
    dataframe = dataframe.copy()

    aliases: dict[str, list[str]] = {
        "source_order_id": [
            "source_order_id",
            "order_id",
            "id",
        ],
        "account_id": [
            "account_id",
            "account",
        ],
        "symbol": [
            "symbol",
            "instrument",
            "instrument_symbol",
        ],
        "side": [
            "side",
            "order_side",
        ],
        "quantity": [
            "quantity",
            "qty",
        ],
        "price": [
            "price",
            "order_price",
        ],
        "executed_price": [
            "executed_price",
            "execution_price",
            "fill_price",
        ],
        "status": [
            "status",
            "order_status",
        ],
        "created_at": [
            "created_at",
            "created_on",
            "created",
        ],
    }

    rename_map: dict[str, str] = {}

    for canonical_name, candidates in aliases.items():
        if canonical_name in dataframe.columns:
            continue

        source_column = first_existing_column(dataframe, candidates)

        if source_column is not None:
            rename_map[source_column] = canonical_name

    dataframe = dataframe.rename(columns=rename_map)

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
        if column not in dataframe.columns:
            dataframe[column] = None

    return dataframe


def normalize_values(dataframe: pd.DataFrame) -> pd.DataFrame:
    """
    Convert values to the expected types.
    """
    dataframe = dataframe.copy()

    dataframe["source_order_id"] = dataframe["source_order_id"].astype(
        "string"
    )

    dataframe["symbol"] = dataframe["symbol"].astype("string").str.strip()

    dataframe["side"] = (
        dataframe["side"]
        .astype("string")
        .str.strip()
        .str.upper()
    )

    dataframe["status"] = (
        dataframe["status"]
        .astype("string")
        .str.strip()
        .str.upper()
    )

    dataframe["account_id"] = pd.to_numeric(
        dataframe["account_id"],
        errors="coerce",
    )

    dataframe["quantity"] = pd.to_numeric(
        dataframe["quantity"],
        errors="coerce",
    )

    dataframe["price"] = pd.to_numeric(
        dataframe["price"],
        errors="coerce",
    )

    dataframe["executed_price"] = pd.to_numeric(
        dataframe["executed_price"],
        errors="coerce",
    )

    dataframe["created_at"] = pd.to_datetime(
        dataframe["created_at"],
        errors="coerce",
        utc=True,
    )

    dataframe["executed_price"] = dataframe["executed_price"].fillna(
        dataframe["price"]
    )

    return dataframe


def validate_row(row: pd.Series) -> str | None:
    """
    Validate one order row.

    Returns
    -------
    str | None
        Validation reason, or None when the row is valid.
    """
    if pd.isna(row["source_order_id"]) or not str(
            row["source_order_id"]
    ).strip():
        return "missing_source_order_id"

    if pd.isna(row["account_id"]):
        return "missing_account_id"

    if pd.isna(row["symbol"]) or not str(row["symbol"]).strip():
        return "missing_symbol"

    if pd.isna(row["side"]) or not str(row["side"]).strip():
        return "missing_side"

    if str(row["side"]).upper() not in VALID_SIDES:
        return "invalid_side"

    if pd.isna(row["quantity"]):
        return "invalid_quantity"

    if float(row["quantity"]) <= 0:
        return "invalid_quantity"

    if pd.isna(row["price"]):
        return "invalid_price"

    if float(row["price"]) <= 0:
        return "invalid_price"

    if pd.isna(row["executed_price"]):
        return "invalid_executed_price"

    if float(row["executed_price"]) <= 0:
        return "invalid_executed_price"

    if pd.isna(row["status"]) or not str(row["status"]).strip():
        return "missing_status"

    if str(row["status"]).upper() not in VALID_STATUSES:
        return "invalid_status"

    if pd.isna(row["created_at"]):
        return "invalid_created_at"

    return None


def serialize_value(value: Any) -> Any:
    """Convert pandas and datetime values to JSON-compatible values."""
    if pd.isna(value):
        return None

    if isinstance(value, pd.Timestamp):
        return value.isoformat()

    if hasattr(value, "item"):
        try:
            return value.item()
        except ValueError:
            pass

    return value


def row_to_json(row: pd.Series) -> str:
    """Serialize the original row for DLQ investigation."""
    payload = {
        str(key): serialize_value(value)
        for key, value in row.items()
    }

    return json.dumps(payload, default=str)


def transform_orders(
        input_path: Path = DEFAULT_INPUT_PATH,
        valid_output_path: Path = DEFAULT_VALID_OUTPUT_PATH,
        invalid_output_path: Path = DEFAULT_INVALID_OUTPUT_PATH,
        batch_id: str | None = None,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """
    Normalize, validate, and transform extracted orders.
    """
    if batch_id is None:
        batch_id = create_batch_id()

    if input_path.exists():
        orders = pd.read_csv(input_path)
    else:
        raise FileNotFoundError(
            f"Input file does not exist: {input_path}"
        )

    orders = normalize_columns(orders)
    orders = normalize_values(orders)

    valid_rows: list[dict[str, Any]] = []
    invalid_rows: list[dict[str, Any]] = []

    for _, row in orders.iterrows():
        reason = validate_row(row)

        if reason is not None:
            invalid_rows.append(
                {
                    "source_order_id": serialize_value(
                        row["source_order_id"]
                    ),
                    "account_id": serialize_value(row["account_id"]),
                    "symbol": serialize_value(row["symbol"]),
                    "side": serialize_value(row["side"]),
                    "quantity": serialize_value(row["quantity"]),
                    "price": serialize_value(row["price"]),
                    "executed_price": serialize_value(
                        row["executed_price"]
                    ),
                    "status": serialize_value(row["status"]),
                    "created_at": serialize_value(row["created_at"]),
                    "reason": reason,
                    "batch_id": batch_id,
                    "raw_payload": row_to_json(row),
                }
            )
            continue

        quantity = float(row["quantity"])
        executed_price = float(row["executed_price"])

        valid_rows.append(
            {
                "source_order_id": str(row["source_order_id"]),
                "account_id": int(row["account_id"]),
                "symbol": str(row["symbol"]),
                "side": str(row["side"]).upper(),
                "quantity": quantity,
                "price": float(row["price"]),
                "executed_price": executed_price,
                "status": str(row["status"]).upper(),
                "created_at": pd.Timestamp(
                    row["created_at"]
                ).tz_convert(None),
                "trade_value": round(
                    quantity * executed_price,
                    4,
                    ),
                "batch_id": batch_id,
            }
        )

    valid_orders = pd.DataFrame(valid_rows)
    invalid_orders = pd.DataFrame(invalid_rows)

    valid_columns = [
        "source_order_id",
        "account_id",
        "symbol",
        "side",
        "quantity",
        "price",
        "executed_price",
        "status",
        "created_at",
        "trade_value",
        "batch_id",
    ]

    invalid_columns = [
        "source_order_id",
        "account_id",
        "symbol",
        "side",
        "quantity",
        "price",
        "executed_price",
        "status",
        "created_at",
        "reason",
        "batch_id",
        "raw_payload",
    ]

    if valid_orders.empty:
        valid_orders = pd.DataFrame(columns=valid_columns)

    if invalid_orders.empty:
        invalid_orders = pd.DataFrame(columns=invalid_columns)

    ensure_parent_directory(valid_output_path)
    ensure_parent_directory(invalid_output_path)

    valid_orders.to_csv(valid_output_path, index=False)
    invalid_orders.to_csv(invalid_output_path, index=False)

    print(f"Batch ID: {batch_id}")
    print(f"Input rows: {len(orders)}")
    print(f"Valid rows: {len(valid_orders)}")
    print(f"Invalid rows: {len(invalid_orders)}")
    print(f"Valid output: {valid_output_path}")
    print(f"Invalid output: {invalid_output_path}")

    return valid_orders, invalid_orders


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate and transform extracted orders."
    )

    parser.add_argument(
        "--input",
        type=Path,
        default=Path(
            os.getenv(
                "EXTRACTED_ORDERS_PATH",
                str(DEFAULT_INPUT_PATH),
            )
        ),
        help="Extracted order CSV path.",
    )

    parser.add_argument(
        "--valid-output",
        type=Path,
        default=Path(
            os.getenv(
                "VALID_ORDERS_PATH",
                str(DEFAULT_VALID_OUTPUT_PATH),
            )
        ),
        help="Valid order CSV path.",
    )

    parser.add_argument(
        "--invalid-output",
        type=Path,
        default=Path(
            os.getenv(
                "INVALID_ORDERS_PATH",
                str(DEFAULT_INVALID_OUTPUT_PATH),
            )
        ),
        help="Invalid order CSV path.",
    )

    parser.add_argument(
        "--batch-id",
        default=None,
        help="Optional batch identifier.",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    transform_orders(
        input_path=args.input,
        valid_output_path=args.valid_output,
        invalid_output_path=args.invalid_output,
        batch_id=args.batch_id,
    )


if __name__ == "__main__":
    main()