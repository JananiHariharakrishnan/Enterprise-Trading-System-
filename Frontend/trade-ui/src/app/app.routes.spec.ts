import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('app routes', () => {
  it('loads every lazy page', async () => {
    const loaders = routes.filter((route) => typeof route.loadComponent === 'function');
    expect(loaders.length).toBeGreaterThan(0);

    const pages = await Promise.all(loaders.map((route) => route.loadComponent!()));
    expect(pages.every((page) => page)).toBe(true);
  });
});
