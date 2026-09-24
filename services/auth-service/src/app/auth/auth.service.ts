import { ConflictException, Injectable, UnauthorizedException, UnprocessableEntityException } from '@nestjs/common';
import { logger } from '../common/logger.js';
import {
  accountExists,
  consumeRefreshToken,
  createCredential,
  createUser,
  findCredentialByUsername,
  findUserByAccountId,
  findUserById,
  findUserByUsername,
  saveRefreshToken,
  updateUserProfile,
  verifyPassword,
} from './repository/auth.repository.js';
import { ACCESS_TOKEN_LIFETIME_SECONDS, makeRefreshToken, signAccessToken } from './tokens/auth.tokens.js';
import type { AuthPayload } from './interfaces/auth-payload.interface.js';
import { LoginRequestDto } from './dto/login.dto.js';
import { RefreshRequestDto } from './dto/refresh.dto.js';
import { RegisterRequestDto } from './dto/register.dto.js';
import { UpdateUserProfileDto } from './dto/update-profile.dto.js';

@Injectable()
export class AuthService {
  async register(dto: RegisterRequestDto) {
    const existing = await findUserByUsername(dto.username);
    if (existing) {
      throw new ConflictException();
    }

    const hasAccount = await accountExists(dto.accountId);
    if (!hasAccount) {
      throw new UnprocessableEntityException();
    }

    const existingAccountUser = await findUserByAccountId(dto.accountId);
    if (existingAccountUser) {
      throw new ConflictException();
    }

    await createCredential(dto.accountId, dto.username, dto.password);
    const user = await createUser(dto.username, dto.accountId, ['CUSTOMER']);

    return {
      id: user.id,
      username: user.username,
      accountId: user.accountId,
      roles: user.roles,
      createdOn: user.createdOn,
    };
  }

  async login(dto: LoginRequestDto) {
    const credential = await findCredentialByUsername(dto.username);
    const passwordValid = await verifyPassword(credential, dto.password);

    if (!passwordValid) {
      logger.warn('login failed', { username: dto.username });
      throw new UnauthorizedException();
    }

    const user = await findUserByUsername(dto.username);
    if (!user) {
      logger.error('credential exists without user', { username: dto.username });
      throw new UnauthorizedException();
    }

    const accessToken = signAccessToken(user);
    const refreshToken = makeRefreshToken();
    await saveRefreshToken(refreshToken, user.id);

    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: ACCESS_TOKEN_LIFETIME_SECONDS,
    };
  }

  async refresh(dto: RefreshRequestDto) {
    const consumed = await consumeRefreshToken(dto.refreshToken);
    if (!consumed) {
      throw new UnauthorizedException();
    }

    const user = await findUserById(consumed.userId);
    if (!user) {
      throw new UnauthorizedException();
    }

    const accessToken = signAccessToken(user);
    const refreshToken = makeRefreshToken();
    await saveRefreshToken(refreshToken, user.id, consumed.sessionId, consumed.id);

    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: ACCESS_TOKEN_LIFETIME_SECONDS,
    };
  }

  async me(user: AuthPayload) {
    const current = await findUserById(user.sub);
    if (!current) {
      throw new UnauthorizedException();
    }

    return {
      id: current.id,
      username: current.username,
      accountId: current.accountId,
      firstName: current.firstName,
      lastName: current.lastName,
      email: current.email,
      phoneNo: current.phoneNo,
      roles: current.roles,
      createdOn: current.createdOn,
    };
  }

  async updateProfile(userId: string, dto: UpdateUserProfileDto) {
    const current = await findUserById(userId);
    if (!current) {
      throw new UnauthorizedException();
    }

    const updated = await updateUserProfile(userId, {
      firstName: dto.firstName,
      lastName: dto.lastName,
      email: dto.email,
      phoneNo: dto.phoneNo,
    });

    return {
      id: updated.id,
      username: updated.username,
      accountId: updated.accountId,
      firstName: updated.firstName,
      lastName: updated.lastName,
      email: updated.email,
      phoneNo: updated.phoneNo,
      roles: updated.roles,
      createdOn: updated.createdOn,
    };
  }
}


