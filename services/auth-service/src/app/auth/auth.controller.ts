import { Body, Controller, Get, HttpCode, Patch, Post, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AuthService } from './auth.service.js';
import { LoginRequestDto } from './dto/login.dto.js';
import { RefreshRequestDto } from './dto/refresh.dto.js';
import { RegisterRequestDto } from './dto/register.dto.js';
import { UpdateUserProfileDto } from './dto/update-profile.dto.js';
import { CurrentUser } from './decorators/current-user.decorator.js';
import { JwtAuthGuard } from './guards/jwt-auth.guard.js';
import type { AuthPayload } from './interfaces/auth-payload.interface.js';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('register')
  register(@Body() body: RegisterRequestDto) {
    return this.authService.register(body);
  }

  @Post('login')
  @Throttle({ default: { limit: 10, ttl: 60000 } })
  @HttpCode(200)
  login(@Body() body: LoginRequestDto) {
    return this.authService.login(body);
  }

  @Post('refresh')
  @Throttle({ default: { limit: 30, ttl: 60000 } })
  @HttpCode(200)
  refresh(@Body() body: RefreshRequestDto) {
    return this.authService.refresh(body);
  }

  @Get('me')
  @Throttle({ default: { limit: 120, ttl: 60000 } })
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  me(@CurrentUser() user: AuthPayload) {
    return this.authService.me(user);
  }

  @Patch('profile')
  @Throttle({ default: { limit: 100, ttl: 3600000 } }) // 100 updates per hour (very permissive)
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  updateProfile(
    @CurrentUser() user: AuthPayload,
    @Body() body: UpdateUserProfileDto,
  ) {
    return this.authService.updateProfile(user.sub, body);
  }
}

