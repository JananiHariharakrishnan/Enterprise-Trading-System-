import {
  IsString,
  MaxLength,
  IsNotEmpty
} from 'class-validator';

export class LoginRequest {
  @IsString()
  @IsNotEmpty()
  @MaxLength(64)
  username!: string;

  @IsString()
   @IsNotEmpty()
  @MaxLength(128)
  password!: string;
}