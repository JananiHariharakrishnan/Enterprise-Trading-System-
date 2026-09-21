import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

import { LoginRequest } from '../../../../lib/dto/login.dto';
import {
  findUserByUsername,
  verifyPassword,
  saveRefreshToken,
} from '../../../../lib/store';

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

    const user = await findUserByUsername(dto.username);

    // Same response for unknown username and wrong password
    if (!user) {
      return NextResponse.json(
        {
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        },
        { status: 401 },
      );
    }

    const passwordValid = await verifyPassword(
      user,
      dto.password,
    );

    if (!passwordValid) {
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
    console.error(err);

    return NextResponse.json(
      {
        errorCode: 'AUTH-401',
        message: 'Unauthorised',
      },
      { status: 401 },
    );
  }
}