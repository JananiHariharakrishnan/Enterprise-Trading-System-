import { HttpException, HttpStatus, Injectable } from '@nestjs/common';
import { findUserByAccountId } from '../auth/repository/auth.repository.js';
import { PreferencesRepository } from './preferences.repository.js';
import type { UpsertPreferencesDto } from './dto/upsert-preferences.dto.js';
import type { NotificationChannel, NotificationEventType } from './preferences.types.js';

@Injectable()
export class PreferencesService {
  constructor(private readonly repository: PreferencesRepository) {}

  async getPreferences(accountId: number) {
    const found = await this.repository.getByAccountId(accountId);
    if (!found) {
      throw new HttpException({ errorCode: 'PRF-404', message: 'Preferences not found' }, HttpStatus.NOT_FOUND);
    }

    return found;
  }

  async putPreferences(accountId: number, body: UpsertPreferencesDto) {
    return this.repository.upsert({
      accountId,
      defaultAccountId: body.defaultAccountId,
      trading: body.trading,
      notifications: {
        tradeFills: this.normalizeChannels(body.notifications?.tradeFills),
        priceAlerts: this.normalizeChannels(body.notifications?.priceAlerts),
      },
      display: body.display,
    });
  }

  async resolveNotification(accountId: number, eventType: NotificationEventType) {
    const user = await findUserByAccountId(accountId);
    if (!user) {
      throw new HttpException({ errorCode: 'ACC-404', message: 'Account not found' }, HttpStatus.NOT_FOUND);
    }

    const found = await this.repository.getByAccountId(accountId);
    if (!found) {
      return {
        accountId,
        eventType,
        channels: ['IN_APP'],
        source: 'DEFAULT',
      };
    }

    return {
      accountId,
      eventType,
      channels: eventType === 'PRICE_ALERTS' ? found.notifications.priceAlerts : found.notifications.tradeFills,
      source: 'PREFERENCE',
    };
  }

  private normalizeChannels(value: unknown): NotificationChannel[] {
    const allowed = new Set<NotificationChannel>(['PUSH', 'IN_APP', 'EMAIL', 'SMS']);
    const source = Array.isArray(value)
      ? value
      : typeof value === 'string'
        ? value.split(/[\s,;|]+/)
        : [];

    const channels = source
      .map((item) => String(item).trim().toUpperCase())
      .map((item) => (item === 'INAPP' ? 'IN_APP' : item))
      .filter((item): item is NotificationChannel => allowed.has(item as NotificationChannel));

    return channels.length > 0 ? [...new Set(channels)] : ['IN_APP'];
  }
}
