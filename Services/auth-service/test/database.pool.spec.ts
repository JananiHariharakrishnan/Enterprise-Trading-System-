import { afterEach, describe, expect, it, vi } from 'vitest';

const { existsSync } = vi.hoisted(() => ({
  existsSync: vi.fn((_candidate?: unknown) => false),
}));

vi.mock('node:fs', async () => {
  const actual = await vi.importActual<typeof import('node:fs')>('node:fs');
  const wrapped = { ...actual, existsSync };
  return { ...wrapped, default: wrapped };
});

vi.mock('pg', () => ({
  Pool: vi.fn().mockImplementation(function Pool() {
    return {
      query: vi.fn().mockResolvedValue({ rows: [{ id: 1 }], rowCount: 1 }),
    };
  }),
}));

vi.mock('dotenv', () => ({
  default: {
    config: vi.fn(),
  },
}));

async function loadDatabase(exists: (candidate: string) => boolean) {
  existsSync.mockImplementation((candidate?: unknown) => exists(String(candidate)));
  vi.resetModules();
  vi.stubEnv('NODE_ENV', 'development');
  vi.stubEnv('VITEST', '');
  vi.stubEnv('VITEST_POOL_ID', '');

  const database = await import('../src/app/common/database.js');
  const dotenv = await import('dotenv');
  const { Pool } = await import('pg');
  await database.db.query('SELECT 1', []);
  return { dotenv, Pool };
}

describe('database pool initialization', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('loads the first env file it finds and opens a pool', async () => {
    const { dotenv, Pool } = await loadDatabase((candidate) => candidate.endsWith('.env.local'));

    expect(dotenv.default.config).toHaveBeenCalledWith(expect.objectContaining({ path: expect.stringMatching(/\.env\.local$/) }));
    expect(Pool).toHaveBeenCalled();
  });

  it('falls back to the default dotenv lookup when no env file exists', async () => {
    const { dotenv, Pool } = await loadDatabase(() => false);

    expect(dotenv.default.config).toHaveBeenCalledWith();
    expect(Pool).toHaveBeenCalled();
  });
});
