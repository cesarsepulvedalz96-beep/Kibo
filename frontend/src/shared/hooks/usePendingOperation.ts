import { useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { User } from '../types';

export interface PendingOperation<Context> {
  path: string;
  payload: Record<string, unknown> & { requestId: string };
  context: Context;
}

export function operationKey(user: Pick<User, 'id' | 'businessId'>, kind: string) {
  return `stockflow:pending:${user.id}:${user.businessId}:${kind}`;
}

export function readOperation<Context>(key: string): {
  pending: PendingOperation<Context> | null;
  error?: ApiError;
} {
  try {
    const stored = sessionStorage.getItem(key);
    if (!stored) return { pending: null };
    const operation = JSON.parse(stored) as PendingOperation<Context>;
    if (!operation.path || !operation.payload?.requestId || !operation.context)
      throw new Error('Invalid pending operation');
    return { pending: operation };
  } catch {
    // Do not silently discard an operation that might already have committed.
    return { pending: null, error: new ApiError('RECOVERY_STORAGE_ERROR', 0) };
  }
}

function definitelyRejected(error: unknown, recovering: boolean) {
  return (
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500 &&
    ![408, 429].includes(error.status) &&
    !['IDEMPOTENCY_CONFLICT', 'INTERNAL_ERROR'].includes(error.code) &&
    !(recovering && [401, 403].includes(error.status))
  );
}

/** Persist business data before sending. A retry always uses the original endpoint and payload. */
export function usePendingOperation<Context>(key: string) {
  const [initial] = useState(() => readOperation<Context>(key));
  const [pending, setPending] = useState(initial.pending);
  const current = useRef(pending);
  const inFlight = useRef(false);

  async function execute<Result>(path: string, payload: Record<string, unknown>, context: Context) {
    if (initial.error) throw initial.error;
    if (inFlight.current) throw new ApiError('OPERATION_PENDING', 0);
    const recovering = current.current !== null;
    const operation = current.current || {
      path,
      payload: { ...payload, requestId: crypto.randomUUID() },
      context,
    };
    try {
      sessionStorage.setItem(key, JSON.stringify(operation));
    } catch {
      throw new ApiError('RECOVERY_STORAGE_ERROR', 0);
    }
    current.current = operation;
    setPending(operation);
    inFlight.current = true;
    try {
      const result = await api.post<Result>(operation.path, operation.payload);
      sessionStorage.removeItem(key);
      current.current = null;
      setPending(null);
      return result;
    } catch (error) {
      if (definitelyRejected(error, recovering)) {
        sessionStorage.removeItem(key);
        current.current = null;
        setPending(null);
      }
      throw error;
    } finally {
      inFlight.current = false;
    }
  }

  return { pending, execute, recoveryError: initial.error };
}
