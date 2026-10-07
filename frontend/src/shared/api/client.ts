import { trackedFetch } from './apiActivity';

const API_URL = import.meta.env.VITE_API_URL || '/api/v1';
let csrfToken: { token: string; headerName: string } | undefined;
let sessionGeneration = 0;

/** Discard credentials and responses belonging to the previous browser session. */
export function resetApiSession() {
  sessionGeneration += 1;
  csrfToken = undefined;
}

function assertCurrentSession(generation: number) {
  if (generation !== sessionGeneration) throw new DOMException('Session changed', 'AbortError');
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
  ) {
    super(code);
  }
}

export function apiUrl(path: string) {
  return `${API_URL}${path}`;
}

export async function refreshCsrf() {
  const generation = sessionGeneration;
  let response: Response;
  try {
    response = await trackedFetch(
      `${API_URL}/auth/csrf`,
      {
        credentials: 'include',
      },
      'write',
    );
  } catch {
    throw new ApiError('CONNECTION_ERROR', 0);
  }
  assertCurrentSession(generation);
  if (!response.ok) throw new ApiError('CONNECTION_ERROR', response.status);
  const token = await response.json();
  assertCurrentSession(generation);
  csrfToken = token;
}

/** No credentials are persisted in browser storage. Mutations are never retried automatically. */
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const generation = sessionGeneration;
  const method = options.method || 'GET';
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrfToken) await refreshCsrf();
    assertCurrentSession(generation);
    if (csrfToken) headers.set(csrfToken.headerName, csrfToken.token);
  }
  let response: Response;
  try {
    response = await trackedFetch(`${API_URL}${path}`, {
      ...options,
      headers,
      credentials: 'include',
    });
  } catch (error) {
    assertCurrentSession(generation);
    if (options.signal?.aborted) throw error;
    throw new ApiError('CONNECTION_ERROR', 0);
  }
  assertCurrentSession(generation);
  if (!response.ok) {
    const body = await response.json().catch(() => ({ code: 'INTERNAL_ERROR' }));
    assertCurrentSession(generation);
    if (response.status === 401 && path !== '/auth/login')
      window.dispatchEvent(new Event('stockflow:session-expired'));
    if (response.status === 403) csrfToken = undefined;
    if (body.code === 'INSUFFICIENT_STOCK' || body.code === 'STOCK_INSUFFICIENT')
      window.dispatchEvent(new Event('stockflow:stock-changed'));
    throw new ApiError(body.code || 'INTERNAL_ERROR', response.status);
  }
  if (response.status === 204) return undefined as T;
  const body = await response.json();
  assertCurrentSession(generation);
  return body as T;
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PATCH',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
};

export function queryString(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value));
  });
  return params.toString();
}
