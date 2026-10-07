import { describe, expect, it } from 'vitest';
import { getSafeReturnUrl } from './return-url.util';

describe('getSafeReturnUrl', () => {
  it('keeps same-origin paths', () => {
    expect(getSafeReturnUrl('/dashboard?tab=orders')).toBe('/dashboard?tab=orders');
    expect(getSafeReturnUrl('/')).toBe('/');
  });

  it('drops missing, protocol-relative, and off-site values', () => {
    expect(getSafeReturnUrl(null)).toBeUndefined();
    expect(getSafeReturnUrl(undefined)).toBeUndefined();
    expect(getSafeReturnUrl('')).toBeUndefined();
    expect(getSafeReturnUrl('//evil.example/phish')).toBeUndefined();
    expect(getSafeReturnUrl('https://evil.example/phish')).toBeUndefined();
    expect(getSafeReturnUrl('dashboard')).toBeUndefined();
  });
});
