import {
  IsInt,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

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

  @IsInt()
  @Min(1)
  accountId!: number;
}