import 'reflect-metadata';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

import { RegisterRequest } from '../lib/dto/register.dto';

describe('RegisterRequest DTO', () => {
  test('accepts accountId provided as numeric string', async () => {
    const dto = plainToInstance(RegisterRequest, {
      username: 'priya.menon',
      password: 'correct horse battery staple',
      accountId: '1',
    });

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    expect(errors).toHaveLength(0);
    expect(dto.accountId).toBe(1);
  });
});
