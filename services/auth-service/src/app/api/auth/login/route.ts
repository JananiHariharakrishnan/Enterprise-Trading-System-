import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

import { LoginRequest } from '../../../../lib/dto/login.dto';
import {
  findUserByUsername,
  saveRefreshToken,
} from '../../../../lib/store';
import { findCredentialByUsername, verifyPassword as verifyCredentialPassword } from '../../../../lib/credentialsStore';
import { logger } from '../../../../lib/logger';

import {
  signAccessToken,
  makeRefreshToken,
} from '../../../../lib/jwt';

export async function POST(req: Request) {
  try {
    const body = await req.json();

    const dto = plainToInstance(LoginRequest, body);

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    if (errors.length) {
      return NextResponse.json(
        {
          errorCode: 'VAL-422',
          message: 'Invalid input',
        },
        { status: 422 },
      );
    }
    const credential = await findCredentialByUsername(dto.username);

    const passwordValid = await verifyCredentialPassword(credential, dto.password);

    if (!passwordValid) {
      // Keep response identical for unknown user vs wrong password
      return NextResponse.json(
        {
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        },
        { status: 401 },
      );
    }

    const user = await findUserByUsername(dto.username);
    if (!user) {
      logger.error('credential exists without user', { username: dto.username });
      return NextResponse.json(
        {
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        },
        { status: 401 },
      );
    }

    const accessToken = signAccessToken(user);
    const refreshToken = makeRefreshToken();

    saveRefreshToken(refreshToken, user.id);

    return NextResponse.json(
      {
        accessToken,
        refreshToken,
        tokenType: 'Bearer',
        expiresIn: 900,
      },
      { status: 200 },
    );
  } catch (err) {
    logger.error('login route error', { message: (err as Error)?.message });

    return NextResponse.json(
      {
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      },
 
 
      { status: 401 },
    );
  }
}