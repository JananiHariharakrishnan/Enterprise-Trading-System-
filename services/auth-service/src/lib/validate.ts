import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

export async function validateRequest<T extends object>(
  type: new () => T,
  body: unknown,
): Promise<T | null> {
  const dto = plainToInstance(type, body);

  const errors = await validate(dto, {
    whitelist: true,
    forbidNonWhitelisted: true,
  });

  if (errors.length > 0) {
    return null;
  }

  return dto;
}