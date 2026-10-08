import { beforeEach, describe, expect, it, vi } from 'vitest';

import type axiosType from 'axios';

describe('TradeApiClient', () => {
  let clientMethods: any;

  beforeEach(async () => {
    clientMethods = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    };

    // Patch axios.create to return our mock client
    const axios = (await import('axios')) as typeof axiosType & { default: any };
    axios.default.create = vi.fn(() => clientMethods);
  });

  it('getAvailableAccountId returns account id on success', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.get.mockResolvedValue({ data: { accountId: 5 } });

    const api = new TradeApiClient('http://example');
    const result = await api.getAvailableAccountId();
    expect(result).toBe(5);
  });

  it('getAvailableAccountId propagates errors', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const err = new Error('network');
    clientMethods.get.mockRejectedValue(err);

    const api = new TradeApiClient();
    await expect(api.getAvailableAccountId()).rejects.toBe(err);
  });

  it('createUser returns mapped user on success', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const resp = {
      data: {
        id: 'u1',
        username: 'alice',
        accountId: 10,
        firstName: 'Alice',
        lastName: 'A',
        email: 'a@example.com',
        phoneNo: '+123',
        roles: ['CUSTOMER'],
        createdOn: '2024-01-01T00:00:00Z',
      },
    };
    clientMethods.post.mockResolvedValue(resp);

    const api = new TradeApiClient();
    const user = await api.createUser('alice', 10, ['CUSTOMER']);
    expect(user).toEqual({
      id: 'u1',
      username: 'alice',
      accountId: 10,
      firstName: 'Alice',
      lastName: 'A',
      email: 'a@example.com',
      phoneNo: '+123',
      roles: ['CUSTOMER'],
      createdOn: '2024-01-01T00:00:00Z',
    });
  });

  it('createUser returns null on 409 conflict', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const err: any = new Error('conflict');
    err.response = { status: 409 };
    (err as any).isAxiosError = true;

    clientMethods.post.mockRejectedValue(err);

    const api = new TradeApiClient();
    const user = await api.createUser('bob', 11, ['CUSTOMER']);
    expect(user).toBeNull();
  });

  it('getUserById returns null on 404', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const err: any = new Error('not found');
    err.response = { status: 404 };
    (err as any).isAxiosError = true;
    clientMethods.get.mockRejectedValue(err);

    const api = new TradeApiClient();
    const user = await api.getUserById('nope');
    expect(user).toBeNull();
  });

  it('updateUserProfile returns mapped user', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const resp = { data: { id: 'u2', username: 'c', accountId: 2, roles: ['CUSTOMER'], createdOn: '2024-01-01T00:00:00Z' } };
    clientMethods.put.mockResolvedValue(resp);

    const api = new TradeApiClient();
    const u = await api.updateUserProfile('u2', { firstName: 'x' });
    expect(u).toBeTruthy();
    expect(u?.id).toBe('u2');
  });

  it('getUserByUsername returns user on success and null on 404', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.get.mockResolvedValueOnce({ data: { id: 'ux', username: 'ux', accountId: 3, roles: ['CUSTOMER'], createdOn: '2024-01-01T00:00:00Z' } });
    const api = new TradeApiClient();
    const found = await api.getUserByUsername('ux');
    expect(found?.username).toBe('ux');

    const err: any = new Error('not found');
    err.response = { status: 404 };
    (err as any).isAxiosError = true;
    clientMethods.get.mockRejectedValueOnce(err);
    const notFound = await api.getUserByUsername('no');
    expect(notFound).toBeNull();
  });

  it('getUserByAccountId returns null on 404 and success path', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.get.mockResolvedValueOnce({ data: { id: 'ua', username: 'ua', accountId: 7, roles: ['CUSTOMER'], createdOn: '2024-01-01T00:00:00Z' } });
    const api = new TradeApiClient();
    const ok = await api.getUserByAccountId(7);
    expect(ok?.accountId).toBe(7);

    const err: any = new Error('not found');
    err.response = { status: 404 };
    (err as any).isAxiosError = true;
    clientMethods.get.mockRejectedValueOnce(err);
    const nf = await api.getUserByAccountId(99);
    expect(nf).toBeNull();
  });

  it('deleteOrphanCredentials calls delete and propagates errors', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.delete.mockResolvedValueOnce({ status: 200 });
    const api = new TradeApiClient();
    await expect(api.deleteOrphanCredentials()).resolves.toBeUndefined();

    const err = new Error('boom');
    clientMethods.delete.mockRejectedValueOnce(err);
    await expect(api.deleteOrphanCredentials()).rejects.toBe(err);
  });

  it('createUser throws on unexpected axios error', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const err: any = new Error('bad');
    (err as any).isAxiosError = true;
    err.response = { status: 500 };
    clientMethods.post.mockRejectedValueOnce(err);

    const api = new TradeApiClient();
    await expect(api.createUser('x', 1)).rejects.toBe(err);
  });

  it('createUser returns null when trade-api responds without a body', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.post.mockResolvedValue({ data: null });

    const api = new TradeApiClient();
    await expect(api.createUser('a', 1)).resolves.toBeNull();
  });

  it('getUserById returns the user and rethrows unexpected failures', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    clientMethods.get.mockResolvedValueOnce({
      data: { id: 'u1', username: 'a', accountId: 1, roles: ['ADMIN'], createdOn: '2024-01-01T00:00:00Z' },
    });

    const api = new TradeApiClient();
    await expect(api.getUserById('u1')).resolves.toMatchObject({ id: 'u1', roles: ['ADMIN'] });

    clientMethods.get.mockRejectedValueOnce(new Error('timeout'));
    await expect(api.getUserById('u1')).rejects.toThrow('timeout');
  });

  it('getUserByUsername and getUserByAccountId rethrow unexpected failures', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const api = new TradeApiClient();

    clientMethods.get.mockRejectedValueOnce(new Error('username down'));
    await expect(api.getUserByUsername('a')).rejects.toThrow('username down');

    clientMethods.get.mockRejectedValueOnce(new Error('account down'));
    await expect(api.getUserByAccountId(1)).rejects.toThrow('account down');
  });

  it('updateUserProfile rethrows trade-api failures', async () => {
    const { TradeApiClient } = await import('./trade-api.client.js');
    const api = new TradeApiClient();
    clientMethods.put.mockRejectedValueOnce(new Error('put failed'));

    await expect(api.updateUserProfile('u', { email: 'a@b.c' })).rejects.toThrow('put failed');
  });
});
