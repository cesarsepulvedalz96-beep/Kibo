import { useQuery, type QueryClient } from '@tanstack/react-query';
import { api, ApiError, refreshCsrf, resetApiSession } from '../../shared/api/client';
import type { User } from '../../shared/types';

export function useSession() {
  return useQuery({
    queryKey: ['session'],
    queryFn: async ({ signal }) => {
      try {
        return await api.get<User>('/auth/me', signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    retry: false,
    staleTime: 60_000,
  });
}

/** Keep the observed session query alive so route guards see the transition. */
export async function replaceSession(client: QueryClient, user: User | null) {
  resetApiSession();
  await client.cancelQueries();
  client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'session' });
  client.getMutationCache().clear();
  client.setQueryData(['session'], user);
}

export async function logoutSession(client: QueryClient) {
  // Another tab or an expired session can invalidate the cached CSRF token.
  await refreshCsrf();
  try {
    await api.post('/auth/logout');
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 403) throw error;
    // Only logout is safe to retry here; business mutations never retry automatically.
    await refreshCsrf();
    await api.post('/auth/logout');
  }
  await replaceSession(client, null);
}

export async function loginSession(
  client: QueryClient,
  credentials: { email: string; password: string },
) {
  await replaceSession(client, null);
  await refreshCsrf();
  const user = await api.post<User>('/auth/login', credentials);
  await replaceSession(client, user);
  return user;
}

export async function changePasswordSession(client: QueryClient, newPassword: string) {
  const response = await api.post<User | void>('/auth/change-password', { newPassword });
  const user = response || (await api.get<User>('/auth/me'));
  await replaceSession(client, user);
  return user;
}
