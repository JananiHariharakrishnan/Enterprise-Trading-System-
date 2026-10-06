import { Body, Controller, Get, HttpException, HttpStatus, Param, ParseIntPipe, Put, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import type { AuthPayload } from '../auth/interfaces/auth-payload.interface.js';
import { ServiceAuthGuard } from './service-auth.guard.js';
import { UpsertPreferencesDto } from './dto/upsert-preferences.dto.js';
import { PreferencesService } from './preferences.service.js';

@ApiTags('preferences')
@Controller('auth')
export class PreferencesController {
  constructor(private readonly preferencesService: PreferencesService) {}

  @Get('accounts/:accountId/preferences')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  async getPreferences(
    @Param('accountId', ParseIntPipe) accountId: number,
    @CurrentUser() user: AuthPayload,
  ) {
    if (user.accountId !== accountId) {
      throw new HttpException({ errorCode: 'ACC-403', message: 'Access denied for account' }, HttpStatus.FORBIDDEN);
    }

    return this.preferencesService.getPreferences(accountId);
  }

  @Put('accounts/:accountId/preferences')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  async putPreferences(
    @Param('accountId', ParseIntPipe) accountId: number,
    @CurrentUser() user: AuthPayload,
    @Body() body: UpsertPreferencesDto,
  ) {
    if (user.accountId !== accountId) {
      throw new HttpException({ errorCode: 'ACC-403', message: 'Access denied for account' }, HttpStatus.FORBIDDEN);
    }

    return this.preferencesService.putPreferences(accountId, body);
  }

  @Get('internal/accounts/:accountId/notifications/resolve')
  @UseGuards(ServiceAuthGuard)
  @ApiBearerAuth()
  async resolve(
    @Param('accountId', ParseIntPipe) accountId: number,
    @Query('eventType') eventType: string,
  ) {
    const normalized = String(eventType ?? '').toUpperCase();
    const safeEventType = normalized === 'PRICE_ALERTS' ? 'PRICE_ALERTS' : 'TRADE_FILLS';
    return this.preferencesService.resolveNotification(accountId, safeEventType);
  }
}
