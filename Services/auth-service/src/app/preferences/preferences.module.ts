import { Module } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { PreferencesController } from './preferences.controller.js';
import { PreferencesRepository } from './preferences.repository.js';
import { PreferencesService } from './preferences.service.js';
import { ServiceAuthGuard } from './service-auth.guard.js';

@Module({
  controllers: [PreferencesController],
  providers: [PreferencesService, PreferencesRepository, ServiceAuthGuard, JwtAuthGuard],
})
export class PreferencesModule {}
