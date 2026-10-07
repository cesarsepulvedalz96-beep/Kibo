import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../api/client';
import { operationKey, usePendingOperation } from './usePendingOperation';

vi.mock('../api/client', async (original) => ({
  ...(await original<typeof import('../api/client')>()),
  api: { post: vi.fn() },
}));
const post = vi.mocked(api.post);
const key = operationKey({ id: 'user-a', businessId: 'business-a' }, 'checkout');
const context = { label: 'Original purchase' };

beforeEach(() => {
  sessionStorage.clear();
  post.mockReset();
});

describe('pending business operations', () => {
  it('persists before sending and restores the identical payload and endpoint after a lost response', async () => {
    post.mockImplementationOnce(async () => {
      expect(sessionStorage.getItem(key)).toContain('requestId');
      throw new ApiError('CONNECTION_ERROR', 0);
    });
    const first = renderHook(() => usePendingOperation(key));
    await act(async () => {
      await expect(
        first.result.current.execute('/sales', { expectedTotal: 119 }, context),
      ).rejects.toThrow();
    });
    const original = post.mock.calls[0];
    first.unmount();
    const restored = renderHook(() => usePendingOperation(key));
    expect(restored.result.current.pending?.context).toEqual(context);
    post.mockResolvedValueOnce({ id: 'same-sale' });
    await act(async () => {
      await restored.result.current.execute(
        '/inventory/exits',
        { expectedTotal: 999 },
        { label: 'edited' },
      );
    });
    expect(post.mock.calls[1]).toEqual(original);
    expect(sessionStorage.getItem(key)).toBeNull();
  });

  it.each([
    new ApiError('INTERNAL_ERROR', 500),
    new SyntaxError('invalid JSON'),
    new ApiError('IDEMPOTENCY_CONFLICT', 409),
  ])('retains the operation for an uncertain response: %s', async (error) => {
    post.mockRejectedValue(error);
    const { result } = renderHook(() => usePendingOperation(key));
    await act(async () => {
      await expect(
        result.current.execute('/inventory', { quantity: 5 }, context),
      ).rejects.toThrow();
    });
    expect(result.current.pending).not.toBeNull();
    expect(sessionStorage.getItem(key)).not.toBeNull();
  });

  it('allows a corrected quote after a definitive price rejection', async () => {
    post.mockRejectedValueOnce(new ApiError('PRICE_CHANGED', 409));
    const { result } = renderHook(() => usePendingOperation(key));
    await act(async () => {
      await expect(
        result.current.execute('/sales', { expectedTotal: 119 }, context),
      ).rejects.toThrow();
    });
    expect(result.current.pending).toBeNull();
    post.mockResolvedValueOnce({ id: 'sale' });
    await act(async () => {
      await result.current.execute('/sales', { expectedTotal: 238 }, context);
    });
    expect(post.mock.calls[1][1]).not.toEqual(post.mock.calls[0][1]);
  });

  it('does not discard an unresolved result when a later retry is denied by expired permissions', async () => {
    post
      .mockRejectedValueOnce(new ApiError('CONNECTION_ERROR', 0))
      .mockRejectedValueOnce(new ApiError('FORBIDDEN', 403));
    const { result } = renderHook(() => usePendingOperation(key));
    await act(async () => {
      await expect(result.current.execute('/sales', {}, context)).rejects.toThrow();
    });
    await act(async () => {
      await expect(result.current.execute('/sales', {}, context)).rejects.toThrow();
    });
    expect(result.current.pending).not.toBeNull();
  });

  it('separates operation records by user, business and operation type', () => {
    expect(key).not.toBe(operationKey({ id: 'user-b', businessId: 'business-a' }, 'checkout'));
    expect(key).not.toBe(operationKey({ id: 'user-a', businessId: 'business-b' }, 'checkout'));
    expect(key).not.toBe(operationKey({ id: 'user-a', businessId: 'business-a' }, 'movement'));
  });

  it('never sends when browser storage is unavailable', async () => {
    const { result } = renderHook(() => usePendingOperation(key));
    const storage = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    await act(async () => {
      await expect(result.current.execute('/sales', {}, context)).rejects.toThrow(
        'RECOVERY_STORAGE_ERROR',
      );
    });
    expect(post).not.toHaveBeenCalled();
    storage.mockRestore();
  });

  it('exposes a recoverable error for malformed storage and preserves the original record', async () => {
    sessionStorage.setItem(key, 'broken JSON');
    const { result } = renderHook(() => usePendingOperation(key));
    expect(result.current.recoveryError?.code).toBe('RECOVERY_STORAGE_ERROR');
    await act(async () => {
      await expect(result.current.execute('/sales', {}, context)).rejects.toThrow(
        'RECOVERY_STORAGE_ERROR',
      );
    });
    expect(sessionStorage.getItem(key)).toBe('broken JSON');
    expect(post).not.toHaveBeenCalled();
  });
});
