import duckdb

conn = duckdb.connect("analytics.duckdb")
print("dim_account:", conn.execute("SELECT count(*) FROM dim_account").fetchall())
print("dim_instrument:", conn.execute("SELECT count(*) FROM dim_instrument").fetchall())
print("dim_date:", conn.execute("SELECT count(*) FROM dim_date").fetchall())
print("fact_trades:", conn.execute("SELECT count(*) FROM fact_trades").fetchall())
print("dlq_orders:", conn.execute("SELECT id, reason, source_order_id FROM dlq_orders LIMIT 10").fetchall())
conn.close()