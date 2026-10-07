import { describe, expect, it } from 'vitest';
import {
  USD_INR_RATE,
  avgCostInUsd,
  convertAmount,
  currencySymbol,
  isIndianSymbol,
  quoteCurrency
} from './currency.util';

describe('currency.util', () => {
  it('treats NSE and BSE suffixes as INR symbols', () => {
    expect(isIndianSymbol('tcs.ns')).toBe(true);
    expect(isIndianSymbol('RELIANCE.BO')).toBe(true);
    expect(isIndianSymbol('AAPL')).toBe(false);
    expect(isIndianSymbol('')).toBe(false);
    expect(isIndianSymbol(undefined as unknown as string)).toBe(false);
  });

  it('converts only Indian average costs into USD', () => {
    expect(avgCostInUsd(USD_INR_RATE, 'TCS.NS')).toBe(1);
    expect(avgCostInUsd(18.5, 'AAPL')).toBe(18.5);
  });

  it('quotes Indian symbols in INR and others in the fallback currency', () => {
    expect(quoteCurrency('INFY.NS')).toBe('INR');
    expect(quoteCurrency('MSFT')).toBe('USD');
    expect(quoteCurrency('MSFT', 'EUR')).toBe('EUR');
  });

  it('maps USD to $ and every other code to ₹', () => {
    expect(currencySymbol('usd')).toBe('$');
    expect(currencySymbol('USD')).toBe('$');
    expect(currencySymbol('INR')).toBe('₹');
    expect(currencySymbol(null)).toBe('₹');
    expect(currencySymbol(undefined)).toBe('₹');
    expect(currencySymbol('')).toBe('₹');
  });

  it('converts between USD and INR and leaves other pairs unchanged', () => {
    expect(convertAmount(2, 'USD', 'INR')).toBe(2 * USD_INR_RATE);
    expect(convertAmount(USD_INR_RATE, 'INR', 'USD')).toBe(1);
    expect(convertAmount(5, 'usd', 'inr', 10)).toBe(50);
    expect(convertAmount(5, 'INR', 'INR')).toBe(5);
    expect(convertAmount(5, 'USD', 'USD')).toBe(5);
    expect(convertAmount(5, 'EUR', 'GBP')).toBe(5);
    expect(convertAmount(Number.NaN, 'USD', 'INR')).toBeNaN();
    expect(convertAmount(Number.POSITIVE_INFINITY, 'USD', 'INR')).toBe(Number.POSITIVE_INFINITY);
    expect(convertAmount(2, '', '')).toBe(2 * USD_INR_RATE);
    expect(convertAmount(2, null as unknown as string, null as unknown as string)).toBe(2 * USD_INR_RATE);
  });
});
