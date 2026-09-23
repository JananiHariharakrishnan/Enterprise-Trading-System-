import {
  IsInt,
  IsOptional,
  IsEmail,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { Type } from 'class-transformer';

export class RegisterRequest {
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/)
  username!: string;

  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password!: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  accountId!: number;

  @IsString()
  @MinLength(1)
  @MaxLength(100)
  first_name!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(100)
  last_name!: string;

  @IsEmail()
  @MaxLength(255)
  email!: string;

  @IsOptional()
  @IsString()
  @MaxLength(30)
  phone_no?: string;
}