import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiUrl } from '../../shared/api/client';
import type { User } from '../../shared/types';

type RealtimeEventType =
  'PRODUCT_STOCK_CHANGED' | 'SALE_CONFIRMED' | 'INVENTORY_CHANGED' | 'SUBSCRIPTION_CHANGED';

const invalidations: Record<RealtimeEventType, string[]> = {
  PRODUCT_STOCK_CHANGED: ['products', 'inventory', 'reports', 'notifications'],
  SALE_CONFIRMED: ['products', 'sales', 'inventory', 'reports', 'notifications'],
  INVENTORY_CHANGED: ['products', 'inventory', 'reports', 'notifications'],
  SUBSCRIPTION_CHANGED: ['subscription'],
};
const eventTypes = Object.keys(invalidations) as RealtimeEventType[];
const allQueryKeys = [...new Set(Object.values(invalidations).flat())];

export function useRealtimeEvents(user: User | null | undefined) {
  const client = useQueryClient();
  const userId = user?.id;
  const businessId = user?.businessId;
  const role = user?.role;
  useEffect(() => {
    if (!userId || role !== 'BUSINESS_USER' || !businessId) return;
    const source = new EventSource(apiUrl('/events'), { withCredentials: true });
    let refreshTimer: number | undefined;
    const pendingKeys = new Set<string>();
    const invalidate = (keys: string[]) => {
      keys.forEach((key) => pendingKeys.add(key));
      window.clearTimeout(refreshTimer);
      refreshTimer = window.setTimeout(() => {
        pendingKeys.forEach((key) => void client.invalidateQueries({ queryKey: [key] }));
        pendingKeys.clear();
      }, 75);
    };
    source.onopen = () => {
      invalidate(allQueryKeys);
    };
    const handlers = new Map<RealtimeEventType, EventListener>();
    eventTypes.forEach((type) => {
      const receive = ((event: MessageEvent<string>) => {
        try {
          const message = JSON.parse(event.data) as { type?: RealtimeEventType };
          if (!message.type || message.type === type) invalidate(invalidations[type]);
        } catch {
          // Ignore malformed events; the server remains the source of truth on the next refetch.
        }
      }) as EventListener;
      handlers.set(type, receive);
      source.addEventListener(type, receive);
    });
    const stockChanged = () => invalidate(['products', 'inventory', 'reports', 'notifications']);
    window.addEventListener('stockflow:stock-changed', stockChanged);
    return () => {
      window.clearTimeout(refreshTimer);
      pendingKeys.clear();
      handlers.forEach((handler, type) => source.removeEventListener(type, handler));
      window.removeEventListener('stockflow:stock-changed', stockChanged);
      source.close();
    };
  }, [businessId, client, role, userId]);
}
