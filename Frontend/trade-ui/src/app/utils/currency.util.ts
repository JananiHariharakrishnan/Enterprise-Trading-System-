// Put this file at: src/app/utils/currency.util.ts
// One place for the INR -> USD rule, so Positions and Dashboard always agree.

/** Rate used to turn INR prices into USD. Change it here and both pages follow. */
export const USD_INR_RATE = 96;

/** NSE (.NS) and BSE (.BO) symbols are quoted in INR. */
export function isIndianSymbol(symbol: string): boolean {
  const s = (symbol ?? '').toUpperCase();
  return s.endsWith('.NS') || s.endsWith('.BO');
}

/** Average cost converted to USD (INR symbols are divided by the rate). */
export function avgCostInUsd(averageCost: number, symbol: string): number {
  return isIndianSymbol(symbol) ? averageCost / USD_INR_RATE : averageCost;
}

/** Currency code a symbol's own prices are quoted in. */
export function quoteCurrency(symbol: string, fallback = 'USD'): string {
  return isIndianSymbol(symbol) ? 'INR' : fallback;
}
