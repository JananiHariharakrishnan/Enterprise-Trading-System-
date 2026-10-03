import { IsEmail, IsNotEmpty, IsOptional, IsString, MaxLength, Matches } from 'class-validator';

export class UpdateUserProfileDto {
  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  firstName?: string;

  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  lastName?: string;

  @IsOptional()
  @IsEmail()
  @MaxLength(255)
  email?: string;

  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(30)
  @Matches(/^\+?[0-9\-\s()]+$/, {
    message: 'phone_no must be a valid phone number format',
  })
  phoneNo?: string;
}
