import { Module } from '@nestjs/common';
import { ThrottlerModule } from '@nestjs/throttler';
import { AuthModule } from './app/auth/auth.module.js';
import { PreferencesModule } from './app/preferences/preferences.module.js';

@Module({
  imports: [
    ThrottlerModule.forRoot([
      {
        name: 'global',
        ttl: 60000, // 60 seconds
        limit: 60, // 60 requests per minute for regular authenticated traffic
      },
    ]),
    AuthModule,
    PreferencesModule,
  ],
})
export class AppModule {}
