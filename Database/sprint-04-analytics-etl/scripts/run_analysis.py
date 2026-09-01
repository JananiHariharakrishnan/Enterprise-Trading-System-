from pathlib import Path

import duckdb

from analytics_etl.analysis import compare_securities
from analytics_etl.charts import create_market_dashboard


DATABASE_PATH = Path("analytics.duckdb")

SYMBOLS = [
    "INFY.NS",
    "RELIANCE.NS",
    "TATASTEEL.BO",
]


def load_market_data(
    database_path: Path = DATABASE_PATH,
) -> dict:
    """Load market candle data from DuckDB for analysis."""

    if not database_path.exists():
        raise FileNotFoundError(
            f"DuckDB database not found: {database_path}. "
            "Run the ETL pipeline first."
        )

    connection = duckdb.connect(str(database_path))

    try:
        df = connection.execute(
            """
            SELECT
                symbol,
                date,
                close
            FROM market_candles
            WHERE symbol IN (?, ?, ?)
            ORDER BY symbol, date
            """,
            SYMBOLS,
        ).df()
    finally:
        connection.close()

    if df.empty:
        raise ValueError(
            "No market candle data found for the requested symbols."
        )

    data = {}

    for symbol in SYMBOLS:
        symbol_df = df[df["symbol"] == symbol].copy()

        if symbol_df.empty:
            raise ValueError(
                f"No market data found for {symbol}."
            )

        data[symbol] = symbol_df

    return data


def main():
    data = load_market_data()

    metrics = compare_securities(data)

    print("\nAnalysis results:")
    print(metrics.to_string(index=False))

    create_market_dashboard(
        metrics,
        "artefacts/market_dashboard.html",
    )

    print("\nDashboard created:")
    print("artefacts/market_dashboard.html")


if __name__ == "__main__":
    main()