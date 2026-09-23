import { HttpException, HttpStatus } from '@nestjs/common';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PlatformExceptionFilter } from './http-exception.filter.js';

type MockResponse = {
  status: ReturnType<typeof vi.fn>;
  json: ReturnType<typeof vi.fn>;
};

function createHost(response: MockResponse) {
  return {
    switchToHttp: () => ({
      getResponse: () => response,
    }),
  } as any;
}

describe('PlatformExceptionFilter', () => {
  let filter: PlatformExceptionFilter;
  let response: MockResponse;

  beforeEach(() => {
    filter = new PlatformExceptionFilter();
    response = {
      status: vi.fn().mockReturnThis(),
      json: vi.fn().mockReturnThis(),
    };
    process.env.NODE_ENV = 'test';
  });

  it('includes validation details in 422 responses', () => {
    const exception = new HttpException(
      {
        message: ['username must be longer than or equal to 3 characters'],
      },
      HttpStatus.UNPROCESSABLE_ENTITY,
    );

    filter.catch(exception, createHost(response));

    expect(response.status).toHaveBeenCalledWith(422);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'VAL-422',
      message: 'Invalid input: username must be longer than or equal to 3 characters',
    });
  });

  it('maps postgres unique violations to conflict responses with detail', () => {
    filter.catch(
      {
        code: '23505',
        message: 'duplicate key value violates unique constraint',
        detail: 'Key (username)=(Aakash_Test) already exists.',
        constraint: 'uq_credentials_username',
      },
      createHost(response),
    );

    expect(response.status).toHaveBeenCalledWith(409);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'AUTH-409',
      message:
        'Registration failed: [23505] | duplicate key value violates unique constraint | Key (username)=(Aakash_Test) already exists. | constraint=uq_credentials_username',
    });
  });

  it('maps unexpected runtime errors to a server error response instead of AUTH-401', () => {
    filter.catch(new Error('users.created_on column does not exist'), createHost(response));

    expect(response.status).toHaveBeenCalledWith(500);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'SRV-500',
      message: 'Unexpected server error: users.created_on column does not exist',
    });
  });

  it('turns SCRAM password-type failures into actionable DB configuration guidance', () => {
    filter.catch(
      new Error('SASL: SCRAM-SERVER-FIRST-MESSAGE: client password must be a string'),
      createHost(response),
    );

    expect(response.status).toHaveBeenCalledWith(500);
    expect(response.json).toHaveBeenCalledWith({
      errorCode: 'SRV-500',
      message:
        'Unexpected server error: database password is missing or invalid; set POSTGRES_PASSWORD, DB_PASSWORD, PGPASSWORD, or DATABASE_URL with a password',
    });
  });
});

