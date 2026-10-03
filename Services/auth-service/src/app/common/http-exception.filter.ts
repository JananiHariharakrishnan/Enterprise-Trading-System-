import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus } from '@nestjs/common';
import type { Response } from 'express';

type ErrorEnvelope = {
  errorCode: 'VAL-422' | 'AUTH-409' | 'AUTH-401' | 'SRV-500';
  message: string;
};

type PostgresLikeError = {
  code?: string;
  detail?: string;
  table?: string;
  column?: string;
  constraint?: string;
  message: string;
};

export function envelope(status: number, exception?: unknown): ErrorEnvelope {
  if (status === HttpStatus.UNPROCESSABLE_ENTITY || status === HttpStatus.BAD_REQUEST) {
    return {
      errorCode: 'VAL-422',
      message: formatValidationMessage(exception instanceof HttpException ? exception : undefined),
    };
  }

  if (status === HttpStatus.CONFLICT) {
    return {
      errorCode: 'AUTH-409',
      message: formatConflictMessage(exception),
    };
  }

  if (status === HttpStatus.UNAUTHORIZED || status === HttpStatus.FORBIDDEN) {
    return { errorCode: 'AUTH-401', message: 'Unauthorised' };
  }

  if (status >= HttpStatus.INTERNAL_SERVER_ERROR) {
    return {
      errorCode: 'SRV-500',
      message: formatUnexpectedMessage(exception),
    };
  }

  return { errorCode: 'SRV-500', message: formatUnexpectedMessage(exception) };
}

export function formatValidationMessage(exception?: HttpException): string {
  if (!exception) {
    return 'Invalid input';
  }

  const response = exception.getResponse();
  if (typeof response === 'string') {
    return `Invalid input: ${response}`;
  }

  if (response && typeof response === 'object' && 'message' in response) {
    const validationMessage = response.message;

    if (Array.isArray(validationMessage)) {
      const messages = validationMessage
        .filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
        .map((value) => value.trim());

      if (messages.length > 0) {
        return `Invalid input: ${messages.join('; ')}`;
      }
    }

    if (typeof validationMessage === 'string' && validationMessage.trim().length > 0) {
      return `Invalid input: ${validationMessage.trim()}`;
    }
  }

  return 'Invalid input';
}

export function formatConflictMessage(exception?: unknown): string {
  if (isPostgresLikeError(exception) && process.env.NODE_ENV !== 'production') {
    return `Registration failed: ${formatPostgresMessage(exception)}`;
  }

  return 'Registration failed';
}

export function formatUnexpectedMessage(exception?: unknown): string {
  if (process.env.NODE_ENV === 'production') {
    return 'Unexpected server error';
  }

  if (isDatabasePasswordConfigError(exception)) {
    return 'Unexpected server error: database password is missing or invalid; set POSTGRES_PASSWORD, DB_PASSWORD, PGPASSWORD, or DATABASE_URL with a password';
  }

  if (isPostgresLikeError(exception)) {
    return `Unexpected server error: ${formatPostgresMessage(exception)}`;
  }

  if (hasMessage(exception) && exception.message.trim().length > 0) {
    return `Unexpected server error: ${exception.message.trim()}`;
  }

  return 'Unexpected server error';
}

export function isDatabasePasswordConfigError(exception: unknown): exception is Error {
  return (
    exception instanceof Error &&
    exception.message.includes('SASL: SCRAM-SERVER-FIRST-MESSAGE: client password must be a string')
  );
}

export function hasMessage(exception: unknown): exception is { message: string } {
  return Boolean(exception && typeof exception === 'object' && 'message' in exception && typeof exception.message === 'string');
}

export function isPostgresLikeError(exception: unknown): exception is PostgresLikeError {
  return Boolean(
    exception &&
      typeof exception === 'object' &&
      'message' in exception &&
      typeof exception.message === 'string' &&
      ('code' in exception || 'detail' in exception || 'constraint' in exception),
  );
}

export function formatPostgresMessage(exception: PostgresLikeError): string {
  const parts = [
    exception.code ? `[${exception.code}]` : null,
    exception.message?.trim() || null,
    exception.detail?.trim() || null,
    exception.table ? `table=${exception.table}` : null,
    exception.column ? `column=${exception.column}` : null,
    exception.constraint ? `constraint=${exception.constraint}` : null,
  ].filter((value): value is string => Boolean(value));

  return parts.length > 0 ? parts.join(' | ') : 'Database operation failed';
}

export function databaseStatus(exception: PostgresLikeError): number {
  if (exception.code === '23505') {
    return HttpStatus.CONFLICT;
  }

  if (exception.code === '23503' || exception.code === '23502' || exception.code === '22P02') {
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  return HttpStatus.INTERNAL_SERVER_ERROR;
}

@Catch()
export class PlatformExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      response.status(status).json(envelope(status, exception));
      return;
    }

    if (isPostgresLikeError(exception)) {
      const status = databaseStatus(exception);
      response.status(status).json(envelope(status, exception));
      return;
    }

    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json(envelope(HttpStatus.INTERNAL_SERVER_ERROR, exception instanceof Error ? exception : undefined));
  }
}

