import { ErrorResponseErrorCodeEnum as AuthErrorEnum } from '../generated/auth/models/ErrorResponse';
import { ErrorResponseErrorCodeEnum as TradeErrorEnum } from '../generated/trade/models/ErrorResponse';

const authErrorMap: Record<string, string> = {
  'AUTH-401': 'Your session is invalid or expired — please sign in again.',
  'AUTH-409': 'Account conflict detected — contact support with your username.',
  'VAL-422': 'The provided data is invalid — please check the fields and try again.'
};

const tradeErrorMap: Record<string, string> = {
  'ACC-404': 'Account not found — verify your account details.',
  'ACC-403': 'Account not active or not accessible with this token.',
  'INS-404': 'Instrument not found or not tradable.',
  'ORD-400': 'Insufficient funds to place this order.',
  'ORD-409': 'Order conflict: duplicate, not cancellable, or insufficient holdings.',
  'WAT-404': 'Watchlist or instrument not found.',
  'WAT-409': 'Duplicate watchlist or duplicate instrument in watchlist.',
  'ALT-400': 'Maximum active alerts per account exceeded.',
  'ALT-404': 'Price alert not found.',
  'ALT-409': 'Alert is already cancelled.',
  'VAL-422': 'Validation failed — check the request and try again.',
  'AUTH-401': 'Authentication required — please sign in.'
};

export const authMappingComplete =
  Object.values(AuthErrorEnum).every((c) => Object.prototype.hasOwnProperty.call(authErrorMap, c as string));

export const tradeMappingComplete =
  Object.values(TradeErrorEnum).every((c) => Object.prototype.hasOwnProperty.call(tradeErrorMap, c as string));

export function mapErrorCodeToMessage(code?: string | null, serverMessage?: string | null): string {
  if (!code) {
    if (serverMessage && serverMessage.trim().length > 0) return serverMessage;
    return 'An unexpected error occurred — please try again or contact support.';
  }

  if (Object.prototype.hasOwnProperty.call(tradeErrorMap, code)) {
    return tradeErrorMap[code];
  }

  if (Object.prototype.hasOwnProperty.call(authErrorMap, code)) {
    return authErrorMap[code];
  }

  if (serverMessage && serverMessage.trim().length > 0) {
    return serverMessage;
  }

  return `Unknown error (${code}). Please contact support.`;
}


