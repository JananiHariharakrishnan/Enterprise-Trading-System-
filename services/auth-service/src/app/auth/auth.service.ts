import { ConflictException, Injectable, UnauthorizedException } from '@nestjs/common';
import { logger } from '../common/logger.js';
import {
  consumeRefreshToken,
  createCredential,
  createUser,
  deleteCredentialByUsername,
  deleteOrphanCredentials,
  findCredentialByUsername,
  findAvailableAccountId,
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

    const existingCredential = await findCredentialByUsername(dto.username);
    if (existingCredential) {
      throw new ConflictException();
    }

    const removedOrphans = await deleteOrphanCredentials();
    if (removedOrphans > 0) {
      logger.warn('cleaned orphan credentials before registration', {
        removedCount: removedOrphans,
      });
    }

    for (let attempt = 0; attempt < 3; attempt += 1) {
      const availableAccountId = await findAvailableAccountId();
      if (!availableAccountId) {
        throw new ConflictException();
      }

      try {
        await createCredential(availableAccountId, dto.username, dto.password);
      } catch (error) {
        if (!isAccountAllocationConflict(error)) {
          throw error;
        }

        logger.warn('account allocation conflict during credential insert, retrying', {
          username: dto.username,
          attempt: attempt + 1,
        });
        continue;
      }

      try {
        const user = await createUser(dto.username, availableAccountId, ['CUSTOMER']);
        if (!user) {
          await deleteCredentialByUsername(dto.username);
          logger.warn('account allocation conflict during user insert, retrying', {
            username: dto.username,
            attempt: attempt + 1,
          });
          continue;
        }

        return {
          id: user.id,
          username: user.username,
          accountId: user.accountId,
          roles: user.roles,
          createdOn: user.createdOn,
        };
      } catch (error) {
        if (!isAccountAllocationConflict(error)) {
          throw error;
        }

        // Roll back the credential row created in this attempt so the next attempt can reserve a new account.
        await deleteCredentialByUsername(dto.username);

        logger.warn('account allocation conflict during registration, retrying', {
          username: dto.username,
          attempt: attempt + 1,
        });
      }
    }

    throw new ConflictException();
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

function isAccountAllocationConflict(error: unknown): boolean {
  const dbError = error as { code?: string; constraint?: string; message?: string; cause?: unknown } | undefined;
  const constraint = dbError?.constraint;
  const message = dbError?.message ?? '';

  if (dbError?.code === '23505' && (constraint === 'uq_users_account_id' || constraint === 'uq_credentials_account_id')) {
    return true;
  }

  if (message.includes('uq_users_account_id') || message.includes('uq_credentials_account_id')) {
    return true;
  }

  const cause = dbError?.cause as { code?: string; constraint?: string; message?: string } | undefined;
  if (cause?.code === '23505' && (cause.constraint === 'uq_users_account_id' || cause.constraint === 'uq_credentials_account_id')) {
    return true;
  }

  return Boolean((cause?.message ?? '').includes('uq_users_account_id') || (cause?.message ?? '').includes('uq_credentials_account_id'));
}


