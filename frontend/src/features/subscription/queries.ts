import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Page, Subscription, SubscriptionPayment } from '../../shared/types';

export const subscriptionKey = ['subscription'] as const;

export function useSubscription(enabled = true) {
  return useQuery({
    queryKey: subscriptionKey,
    queryFn: ({ signal }) => api.get<Subscription>('/subscription', signal),
    staleTime: 30_000,
    retry: false,
    enabled,
  });
}

export function useRequestSubscriptionPayment() {
  const client = useQueryClient();
  const requestId = useRef(crypto.randomUUID());
  return useMutation({
    mutationFn: (values: { paymentMethod: string; externalReference?: string | null }) =>
      api.post<SubscriptionPayment>('/subscription/payments', {
        ...values,
        requestId: requestId.current,
      }),
    onSuccess: async () => {
      requestId.current = crypto.randomUUID();
      await Promise.all([
        client.invalidateQueries({ queryKey: subscriptionKey }),
        client.invalidateQueries({ queryKey: ['subscription', 'payments'] }),
      ]);
    },
  });
}

export function usePendingSubscriptionPayments(enabled = true) {
  return useQuery({
    queryKey: ['subscription', 'payments', 'PENDING'],
    queryFn: ({ signal }) =>
      api.get<Page<SubscriptionPayment>>(
        '/subscription/payments?status=PENDING&page=0&size=20',
        signal,
      ),
    enabled,
  });
}
