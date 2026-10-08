import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const { api, query } = vi.hoisted(() => ({
  query: vi.fn(),
  api: {
    getUserByAccountId: vi.fn(),
    getAvailableAccountId: vi.fn(),
    getUserByUsername: vi.fn(),
    getUserById: vi.fn(),
    updateUserProfile: vi.fn(),
    createUser: vi.fn(),
    deleteOrphanCredentials: vi.fn(),
  },
}));

vi.mock('../src/app/common/database.js', () => ({
  db: { query },
}));

vi.mock('../src/app/trade-api/trade-api.client.js', () => ({
  TradeApiClient: class TradeApiClient {
    getUserByAccountId = api.getUserByAccountId;
    getAvailableAccountId = api.getAvailableAccountId;
    getUserByUsername = api.getUserByUsername;
    getUserById = api.getUserById;
    updateUserProfile = api.updateUserProfile;
    createUser = api.createUser;
    deleteOrphanCredentials = api.deleteOrphanCredentials;
  },
}));

vi.mock('argon2', () => ({
  default: {
    argon2id: 2,
    hash: vi.fn(),
    verify: vi.fn(),
  },
}));

vi.mock('bcrypt', () => ({
  default: {
    hash: vi.fn(),
    compare: vi.fn(),
  },
}));

type Repo = typeof import('../src/app/auth/repository/auth.repository.js');

let repo: Repo;

function withoutTestRuntime<T>(run: () => Promise<T>): Promise<T> {
  const previous = process.env.NODE_ENV;
  process.env.NODE_ENV = 'development';
  return run().finally(() => {
    process.env.NODE_ENV = previous;
  });
}

describe('auth.repository trade-api delegation', () => {
  beforeAll(async () => {
    vi.stubEnv('NODE_ENV', 'development');
    vi.stubEnv('VITEST', '');
    vi.stubEnv('VITEST_POOL_ID', '');
    vi.resetModules();
    repo = await import('../src/app/auth/repository/auth.repository.js');
    vi.unstubAllEnvs();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterAll(() => {
    vi.unstubAllEnvs();
  });

  it('checks account existence through trade-api', async () => {
    api.getUserByAccountId.mockResolvedValueOnce({ id: 'u1' });
    await expect(repo.accountExists(1)).resolves.toBe(true);

    api.getUserByAccountId.mockResolvedValueOnce(null);
    await expect(repo.accountExists(2)).resolves.toBe(false);

    api.getUserByAccountId.mockRejectedValueOnce(new Error('down'));
    await expect(repo.accountExists(3)).resolves.toBe(false);
    expect(query).not.toHaveBeenCalled();
  });

  it('loads accounts and users through trade-api', async () => {
    api.getAvailableAccountId.mockResolvedValueOnce(9);
    await expect(repo.findAvailableAccountId()).resolves.toBe(9);

    api.getUserByUsername.mockResolvedValueOnce(null);
    await expect(repo.findUserByUsername('ada')).resolves.toBeNull();

    api.getUserById.mockResolvedValueOnce({ id: 'u1', username: 'ada' });
    await expect(repo.findUserById('u1')).resolves.toMatchObject({ username: 'ada' });

    api.getUserByAccountId.mockResolvedValueOnce(null);
    await expect(repo.findUserByAccountId(4)).resolves.toBeNull();
  });

  it('updates a profile through trade-api and reports a failed update', async () => {
    api.getUserById.mockResolvedValueOnce({ id: 'u1', username: 'ada' });
    api.updateUserProfile.mockResolvedValueOnce(null);
    await expect(repo.updateUserProfile('u1', { firstName: 'Ada' })).rejects.toThrow('Failed to update user profile');

    api.getUserById.mockResolvedValueOnce({ id: 'u1', username: 'ada' });
    api.updateUserProfile.mockResolvedValueOnce({ id: 'u1', username: 'ada', accountId: 1, firstName: 'Ada' });
    await expect(repo.updateUserProfile('u1', { firstName: 'Ada' })).resolves.toMatchObject({ firstName: 'Ada' });
  });

  it('creates users and clears orphan credentials through trade-api outside test mode', async () => {
    await withoutTestRuntime(async () => {
      api.createUser.mockResolvedValueOnce({ id: 'u9', username: 'new' });
      await expect(repo.createUser('new', 8, ['CUSTOMER'])).resolves.toMatchObject({ id: 'u9' });

      api.deleteOrphanCredentials.mockResolvedValueOnce(undefined);
      await expect(repo.deleteOrphanCredentials()).resolves.toBe(0);

      api.deleteOrphanCredentials.mockRejectedValueOnce(new Error('cleanup failed'));
      await expect(repo.deleteOrphanCredentials()).resolves.toBe(0);

      api.deleteOrphanCredentials.mockRejectedValueOnce('cleanup failed');
      await expect(repo.deleteOrphanCredentials()).resolves.toBe(0);
    });
  });
});
