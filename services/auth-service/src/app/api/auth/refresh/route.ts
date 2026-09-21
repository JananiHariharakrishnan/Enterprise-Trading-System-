import 'reflect-metadata';

import { NextResponse } from 'next/server';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

import { RefreshRequest } from '../../../../lib/dto/refresh.dto';

import {
  consumeRefreshToken,
  findUserById,
  saveRefreshToken,
} from '../../../../lib/store';

import {
  signAccessToken,
  makeRefreshToken,
} from '../../../../lib/jwt';

export async function POST(req: Request) {
  try {
    const body = await req.json();

    const dto = plainToInstance(RefreshRequest, body);

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    if (errors.length > 0) {
      return NextResponse.json(
        {
          errorCode: 'VAL-422',
          message: 'Invalid input',
        },
        { status: 422 },
      );
    }

    const userId = consumeRefreshToken(dto.refreshToken);

    if (!userId) {
      return NextResponse.json(
        {
          errorCode: 'AUTH-401',
          message: 'Unauthorised',
        },
        { status: 401 },
      );
    }

    const user = await findUserById(userId);

    if (!user) {
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