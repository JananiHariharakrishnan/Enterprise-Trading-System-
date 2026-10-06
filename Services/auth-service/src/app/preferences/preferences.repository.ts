import { Injectable } from '@nestjs/common';
import { db } from '../common/database.js';
import type { CustomerPreferencesRecord } from './preferences.types.js';

type PreferenceRow = {
  account_id: number;
  default_account_id: number;
  trading_default_order_type: string;
  trading_skip_order_confirmation: boolean;
  trading_default_trade_quantity: number;
  notifications_trade_fills: unknown;
  notifications_price_alerts: unknown;
  display_theme: string;
  display_base_currency: string;
  updated_at: Date | string;
};

@Injectable()
export class PreferencesRepository {
  private readonly memory = new Map<number, CustomerPreferencesRecord>();
  private readonly isTest = process.env.NODE_ENV === 'test' || process.env.VITEST === 'true';

  async getByAccountId(accountId: number): Promise<CustomerPreferencesRecord | null> {
    if (this.isTest) {
      return this.memory.get(accountId) ?? null;
    }

    const result = await db.query(
      `
      SELECT
        account_id,
        default_account_id,
        trading_default_order_type,
        trading_skip_order_confirmation,
        trading_default_trade_quantity,
        notifications_trade_fills,
        notifications_price_alerts,
        display_theme,
        display_base_currency,
        updated_at
      FROM tradingsystem.customer_preferences
      WHERE account_id = $1
      LIMIT 1
      `,
      [accountId],
    );

    if (result.rowCount === 0) {
      return null;
    }

    return this.mapRow(result.rows[0]);
  }

  async upsert(payload: Omit<CustomerPreferencesRecord, 'updatedAt'>): Promise<CustomerPreferencesRecord> {
    if (this.isTest) {
      const saved: CustomerPreferencesRecord = {
        ...payload,
        updatedAt: new Date().toISOString(),
      };
      this.memory.set(payload.accountId, saved);
      return saved;
    }

    const result = await db.query(
      `
      INSERT INTO tradingsystem.customer_preferences (
        account_id,
        default_account_id,
        trading_default_order_type,
        trading_skip_order_confirmation,
        trading_default_trade_quantity,
        notifications_trade_fills,
        notifications_price_alerts,
        display_theme,
        display_base_currency,
        updated_at
      )
      VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7::jsonb,$8,$9,NOW())
      ON CONFLICT (account_id)
      DO UPDATE SET
        default_account_id = EXCLUDED.default_account_id,
        trading_default_order_type = EXCLUDED.trading_default_order_type,
        trading_skip_order_confirmation = EXCLUDED.trading_skip_order_confirmation,
        trading_default_trade_quantity = EXCLUDED.trading_default_trade_quantity,
        notifications_trade_fills = EXCLUDED.notifications_trade_fills,
        notifications_price_alerts = EXCLUDED.notifications_price_alerts,
        display_theme = EXCLUDED.display_theme,
        display_base_currency = EXCLUDED.display_base_currency,
        updated_at = NOW()
      RETURNING
        account_id,
        default_account_id,
        trading_default_order_type,
        trading_skip_order_confirmation,
        trading_default_trade_quantity,
        notifications_trade_fills,
        notifications_price_alerts,
        display_theme,
        display_base_currency,
        updated_at
      `,
      [
        payload.accountId,
        payload.defaultAccountId,
        payload.trading.defaultOrderType,
        payload.trading.skipOrderConfirmation,
        payload.trading.defaultTradeQuantity,
        JSON.stringify(payload.notifications.tradeFills),
        JSON.stringify(payload.notifications.priceAlerts),
        payload.display.theme,
        payload.display.baseCurrency,
      ],
    );

    return this.mapRow(result.rows[0]);
  }

  private mapRow(row: PreferenceRow): CustomerPreferencesRecord {
    const tradeFills = Array.isArray(row.notifications_trade_fills)
      ? row.notifications_trade_fills
      : JSON.parse(String(row.notifications_trade_fills));
    const priceAlerts = Array.isArray(row.notifications_price_alerts)
      ? row.notifications_price_alerts
      : JSON.parse(String(row.notifications_price_alerts));

    return {
      accountId: Number(row.account_id),
      defaultAccountId: Number(row.default_account_id),
      trading: {
        defaultOrderType: String(row.trading_default_order_type) as CustomerPreferencesRecord['trading']['defaultOrderType'],
        skipOrderConfirmation: Boolean(row.trading_skip_order_confirmation),
        defaultTradeQuantity: Number(row.trading_default_trade_quantity),
      },
      notifications: {
        tradeFills: tradeFills as CustomerPreferencesRecord['notifications']['tradeFills'],
        priceAlerts: priceAlerts as CustomerPreferencesRecord['notifications']['priceAlerts'],
      },
      display: {
        theme: String(row.display_theme) as CustomerPreferencesRecord['display']['theme'],
        baseCurrency: String(row.display_base_currency) as CustomerPreferencesRecord['display']['baseCurrency'],
      },
      updatedAt: new Date(row.updated_at).toISOString(),
    };
  }
}
