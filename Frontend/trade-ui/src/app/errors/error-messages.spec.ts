import { describe, it, expect } from 'vitest';
import {
  authMappingComplete,
  tradeMappingComplete,
  mapErrorCodeToMessage
} from './error-messages';

describe('error-messages', () => {
  it('has complete contract mappings for auth and trade enums', () => {
    expect(authMappingComplete).toBe(true);
    expect(tradeMappingComplete).toBe(true);
  });

  it('returns server message when no code is provided and message is non-empty', () => {
    expect(mapErrorCodeToMessage(undefined, 'Service unavailable')).toBe('Service unavailable');
    expect(mapErrorCodeToMessage(null, 'Locked account')).toBe('Locked account');
  });

  it('returns generic fallback when no code and no usable server message', () => {
    expect(mapErrorCodeToMessage(undefined, undefined)).toBe(
      'An unexpected error occurred — please try again or contact support.'
    );
    expect(mapErrorCodeToMessage(null, '   ')).toBe(
      'An unexpected error occurred — please try again or contact support.'
    );
  });

  it('maps known trade error codes first', () => {
    expect(mapErrorCodeToMessage('ORD-400')).toBe('Insufficient funds to place this order.');
    expect(mapErrorCodeToMessage('WAT-409')).toBe(
      'Duplicate watchlist or duplicate instrument in watchlist.'
    );
    expect(mapErrorCodeToMessage('AUTH-401')).toBe('Authentication required — please sign in.');
  });

  it('maps known auth-only error code when not defined in trade map', () => {
    expect(mapErrorCodeToMessage('AUTH-409')).toBe(
      'Account conflict detected — contact support with your username.'
    );
  });

  it('falls back to server message for unknown codes when message exists', () => {
    expect(mapErrorCodeToMessage('X-999', 'Custom backend message')).toBe('Custom backend message');
  });

  it('returns unknown-code fallback when code is unknown and message missing', () => {
    expect(mapErrorCodeToMessage('X-999')).toBe('Unknown error (X-999). Please contact support.');
    expect(mapErrorCodeToMessage('X-999', '  ')).toBe('Unknown error (X-999). Please contact support.');
  });
});
