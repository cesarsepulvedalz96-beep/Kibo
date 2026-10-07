import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, refreshCsrf, resetApiSession } from '../../shared/api/client';
import type { User } from '../../shared/types';
import { loginSession, logoutSession, replaceSession } from './session';

const user: User = {
  id: 'user-a',
  businessId: 'business-a',
  email: 'a@example.test',
  displayName: 'User A',
  role: 'BUSINESS_USER',
  businessName: 'Business A',
  mustChangePassword: false,
  lastLoginAt: null,
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const csrf = (token: string) => json({ token, headerName: 'X-CSRF-TOKEN' });
const fetchMock = vi.fn<typeof fetch>();
let client: QueryClient;

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(['session'], user);
  resetApiSession();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => {
  client.clear();
  vi.unstubAllGlobals();
});

describe('session transitions', () => {
  it('notifies existing route observers and removes company data after logout', async () => {
    client.setQueryData(['products'], [{ id: 'private-product' }]);
    const query = client.getQueryCache().find({ queryKey: ['session'] });
    const observer = new QueryObserver<User | null>(client, {
      queryKey: ['session'],
      enabled: false,
    });
    const changed = vi.fn();
    const unsubscribe = observer.subscribe(changed);
    fetchMock
      .mockResolvedValueOnce(csrf('fresh'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await logoutSession(client);

    expect(client.getQueryCache().find({ queryKey: ['session'] })).toBe(query);
    expect(observer.getCurrentResult().data).toBeNull();
    expect(changed).toHaveBeenCalled();
    expect(client.getQueryData(['products'])).toBeUndefined();
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('fresh');
    unsubscribe();
  });

  it('keeps the current identity and company data when logout cannot reach the server', async () => {
    client.setQueryData(['products'], ['private-product']);
    fetchMock
      .mockResolvedValueOnce(csrf('fresh'))
      .mockRejectedValueOnce(new TypeError('Network unavailable'));
    await expect(logoutSession(client)).rejects.toMatchObject({ code: 'CONNECTION_ERROR' });
    expect(client.getQueryData(['session'])).toEqual(user);
    expect(client.getQueryData(['products'])).toEqual(['private-product']);
  });

  it('renews CSRF and retries only logout once after a token rejection', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf('first'))
      .mockResolvedValueOnce(json({ code: 'FORBIDDEN' }, 403))
      .mockResolvedValueOnce(csrf('renewed'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await logoutSession(client);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-CSRF-TOKEN')).toBe('renewed');
    expect(client.getQueryData(['session'])).toBeNull();
  });

  it('does not report logout success after a second rejection', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf('first'))
      .mockResolvedValueOnce(json({ code: 'FORBIDDEN' }, 403))
      .mockResolvedValueOnce(csrf('second'))
      .mockResolvedValueOnce(json({ code: 'FORBIDDEN' }, 403));
    await expect(logoutSession(client)).rejects.toMatchObject({ status: 403 });
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(client.getQueryData(['session'])).toEqual(user);
  });

  it('starts login with fresh CSRF and publishes the user without another network dependency', async () => {
    await replaceSession(client, null);
    fetchMock.mockResolvedValueOnce(csrf('login-token')).mockResolvedValueOnce(json(user));
    await expect(
      loginSession(client, { email: user.email, password: 'test-only' }),
    ).resolves.toEqual(user);
    expect(client.getQueryData(['session'])).toEqual(user);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('ignores an old unauthorized response after another account has logged in', async () => {
    let respond!: (response: Response) => void;
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve;
        }),
    );
    const expired = vi.fn();
    window.addEventListener('stockflow:session-expired', expired);
    const oldRequest = api.get('/products');
    const rejected = expect(oldRequest).rejects.toMatchObject({ name: 'AbortError' });
    const nextUser = { ...user, id: 'user-b', businessId: 'business-b' };
    await replaceSession(client, nextUser);
    respond(json({ code: 'UNAUTHORIZED' }, 401));
    await rejected;
    expect(expired).not.toHaveBeenCalled();
    expect(client.getQueryData(['session'])).toEqual(nextUser);
    window.removeEventListener('stockflow:session-expired', expired);
  });

  it('discards a pending CSRF token from the previous session', async () => {
    let respond!: (response: Response) => void;
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve;
        }),
    );
    const pending = refreshCsrf();
    const rejected = expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    await replaceSession(client, null);
    respond(csrf('old-token'));
    await rejected;
    fetchMock.mockResolvedValueOnce(csrf('new-token')).mockResolvedValueOnce(json({ id: 'sale' }));
    await api.post('/sales', {});
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('X-CSRF-TOKEN')).toBe('new-token');
  });

  it('never automatically retries a rejected business mutation', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf('token'))
      .mockResolvedValueOnce(json({ code: 'FORBIDDEN' }, 403));
    await expect(api.post('/sales', {})).rejects.toMatchObject({ status: 403 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
