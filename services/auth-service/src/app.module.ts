import { Module } from '@nestjs/common';
import { AuthModule } from './app/auth/auth.module.js';

@Module({
  imports: [AuthModule],
})
export class AppModule {}
