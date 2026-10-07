export type ApiConnectionState = 'connected' | 'unavailable';

export interface ApiActivityDetail {
  pending: number;
  writes: number;
}

export const API_ACTIVITY_EVENT = 'stockflow:api-activity';
export const API_CONNECTION_EVENT = 'stockflow:api-connection';

let pendingRequests = 0;
let pendingWrites = 0;

function publishActivity() {
  window.dispatchEvent(
    new CustomEvent<ApiActivityDetail>(API_ACTIVITY_EVENT, {
      detail: { pending: pendingRequests, writes: pendingWrites },
    }),
  );
}

function publishConnection(state: ApiConnectionState) {
  window.dispatchEvent(
    new CustomEvent<ApiConnectionState>(API_CONNECTION_EVENT, { detail: state }),
  );
}

/** Tracks API work and exposes connection changes without coupling UI code to fetch. */
export async function trackedFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
  activity: 'read' | 'write' = 'read',
) {
  const method = (init?.method || 'GET').toUpperCase();
  const writes = activity === 'write' || !['GET', 'HEAD', 'OPTIONS'].includes(method);
  pendingRequests += 1;
  if (writes) pendingWrites += 1;
  publishActivity();

  try {
    const response = await fetch(input, init);
    publishConnection(response.status >= 500 ? 'unavailable' : 'connected');
    return response;
  } catch (error) {
    if (!init?.signal?.aborted) publishConnection('unavailable');
    throw error;
  } finally {
    pendingRequests = Math.max(0, pendingRequests - 1);
    if (writes) pendingWrites = Math.max(0, pendingWrites - 1);
    publishActivity();
  }
}
