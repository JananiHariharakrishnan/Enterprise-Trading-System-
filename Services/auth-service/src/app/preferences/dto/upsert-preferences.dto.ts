import { Type } from 'class-transformer';
import { ArrayMinSize, ArrayUnique, IsArray, IsBoolean, IsEnum, IsInt, IsNotEmptyObject, IsObject, Min, ValidateNested } from 'class-validator';

class TradingPreferencesDto {
  @IsEnum(['MARKET', 'LIMIT', 'STOP'])
  defaultOrderType!: 'MARKET' | 'LIMIT' | 'STOP';

  @IsBoolean()
  skipOrderConfirmation!: boolean;

  @IsInt()
  @Min(1)
  defaultTradeQuantity!: number;
}

class NotificationsDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayUnique()
  @IsEnum(['PUSH', 'IN_APP', 'EMAIL', 'SMS'], { each: true })
  tradeFills!: Array<'PUSH' | 'IN_APP' | 'EMAIL' | 'SMS'>;

  @IsArray()
  @ArrayMinSize(1)
  @ArrayUnique()
  @IsEnum(['PUSH', 'IN_APP', 'EMAIL', 'SMS'], { each: true })
  priceAlerts!: Array<'PUSH' | 'IN_APP' | 'EMAIL' | 'SMS'>;
}

class DisplayDto {
  @IsEnum(['LIGHT', 'DARK', 'SYSTEM'])
  theme!: 'LIGHT' | 'DARK' | 'SYSTEM';

  @IsEnum(['USD', 'INR'])
  baseCurrency!: 'USD' | 'INR';
}

export class UpsertPreferencesDto {
  @IsInt()
  @Min(1)
  defaultAccountId!: number;

  @IsObject()
  @IsNotEmptyObject()
  @ValidateNested()
  @Type(() => TradingPreferencesDto)
  trading!: TradingPreferencesDto;

  @IsObject()
  @IsNotEmptyObject()
  @ValidateNested()
  @Type(() => NotificationsDto)
  notifications!: NotificationsDto;

  @IsObject()
  @IsNotEmptyObject()
  @ValidateNested()
  @Type(() => DisplayDto)
  display!: DisplayDto;
}
