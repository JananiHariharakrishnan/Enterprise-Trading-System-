export function getSafeReturnUrl(returnUrl: string | null | undefined): string | undefined {
  if (!returnUrl) {
    return undefined;
  }

  // Accept only same-origin paths like "/dashboard?tab=orders".
  if (!returnUrl.startsWith('/') || returnUrl.startsWith('//')) {
    return undefined;
  }

  return returnUrl;
}