import { Pool } from 'pg';

const databaseUrl = process.env.DB_URL;

if (!databaseUrl) {
  throw new Error('DB_URL is not configured');
}

export const db = new Pool({
  host: 'localhost',
  port: 5432,
  database: 'trading',
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
});