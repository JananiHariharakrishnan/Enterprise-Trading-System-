import { Module } from '@nestjs/common';
import { ThrottlerModule } from '@nestjs/throttler';
import { AuthModule } from './app/auth/auth.module.js';

@Module({
  imports: [
    ThrottlerModule.forRoot([
      {
        name: 'global',
        ttl: 60000, // 60 seconds
        limit: 5, // 5 attempts
      },
    ]),
    AuthModule,
  ],
})
export class AppModule {}
