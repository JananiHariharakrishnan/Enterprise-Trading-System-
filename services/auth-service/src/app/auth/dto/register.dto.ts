import { Type } from 'class-transformer';
import { ArrayMinSize, IsArray, IsEnum, IsInt, IsOptional, IsString, Matches, MaxLength, Min, MinLength } from 'class-validator';
import { Role } from './role.enum.js';

export class RegisterRequestDto {
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

  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(Role, { each: true })
  roles?: Role[];
}

