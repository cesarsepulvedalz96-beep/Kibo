import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '../../shared/types';
import { useRealtimeEvents } from './useRealtimeEvents';

class FakeEventSource {
  static instance: FakeEventSource;
  readonly listeners = new Map<string, EventListener>();
  readonly close = vi.fn();
  onopen: ((event: Event) => void) | null = null;

  constructor(
    readonly url: string,
    readonly options?: EventSourceInit,
  ) {
    FakeEventSource.instance = this;
  }

  addEventListener(type: string, listener: EventListener) {
    this.listeners.set(type, listener);
  }

  removeEventListener(type: string, listener: EventListener) {
    if (this.listeners.get(type) === listener) this.listeners.delete(type);
  }
}

const user: User = {
  id: 'user-a',
  businessId: 'business-a',
  email: 'user@example.test',
  displayName: 'User A',
  role: 'BUSINESS_USER',
  businessName: 'Business A',
  mustChangePassword: false,
};

function invalidatedRoots(calls: readonly unknown[][]) {
  return calls.map((call) => (call[0] as { queryKey?: readonly unknown[] }).queryKey?.[0]);
}

describe('useRealtimeEvents', () => {
  let client: QueryClient;
  let invalidate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();
  });

  afterEach(() => {
    client.clear();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('listens to named company events, refreshes real query roots and closes the stream', () => {
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const hook = renderHook(() => useRealtimeEvents(user), { wrapper });
    const source = FakeEventSource.instance;

    expect(source.url).toBe('/api/v1/events');
    expect(source.options).toEqual({ withCredentials: true });
    expect([...source.listeners.keys()]).toEqual([
      'PRODUCT_STOCK_CHANGED',
      'SALE_CONFIRMED',
      'INVENTORY_CHANGED',
      'SUBSCRIPTION_CHANGED',
    ]);

    act(() => {
      source.onopen?.(new Event('open'));
      vi.advanceTimersByTime(75);
    });
    expect(invalidatedRoots(invalidate.mock.calls)).toEqual(
      expect.arrayContaining([
        'products',
        'sales',
        'inventory',
        'reports',
        'notifications',
        'subscription',
      ]),
    );

    invalidate.mockClear();
    act(() => {
      source.listeners.get('SALE_CONFIRMED')?.(
        new MessageEvent('SALE_CONFIRMED', {
          data: JSON.stringify({ type: 'SALE_CONFIRMED', resourceId: 'sale-a' }),
        }),
      );
      vi.advanceTimersByTime(75);
    });
    expect(invalidatedRoots(invalidate.mock.calls)).toEqual(
      expect.arrayContaining(['products', 'sales', 'inventory', 'reports', 'notifications']),
    );

    hook.unmount();
    expect(source.listeners.size).toBe(0);
    expect(source.close).toHaveBeenCalledOnce();
  });

  it('refreshes stock-dependent queries after a stale-stock API response', () => {
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    renderHook(() => useRealtimeEvents(user), { wrapper });

    act(() => {
      window.dispatchEvent(new Event('stockflow:stock-changed'));
      vi.advanceTimersByTime(75);
    });

    expect(invalidatedRoots(invalidate.mock.calls)).toEqual(
      expect.arrayContaining(['products', 'inventory', 'reports', 'notifications']),
    );
  });
});
