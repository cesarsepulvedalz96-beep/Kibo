import { api, queryString } from '../../shared/api/client';
import type {
  AdminUser,
  Business,
  Page,
  Subscription,
  SubscriptionPayment,
  SubscriptionPlan,
} from '../../shared/types';

export const adminKeys = {
  businesses: ['admin', 'businesses'] as const,
  users: ['admin', 'users'] as const,
  plans: ['admin', 'plans'] as const,
  subscriptions: ['admin', 'subscriptions'] as const,
  payments: ['admin', 'subscription-payments'] as const,
};

export const adminApi = {
  businesses: (page: number, size = 20) =>
    api.get<Page<Business>>(`/admin/businesses?${queryString({ page, size })}`),
  businessLookup: async () => {
    const response = await api.get<Page<Business>>('/admin/businesses?page=0&size=100');
    return response.items;
  },
  users: (filters: { page: number; businessId?: string; active?: string }) =>
    api.get<Page<AdminUser>>(`/admin/users?${queryString(filters)}`),
  createUser: (values: {
    businessId: string;
    email: string;
    displayName: string;
    temporaryPassword: string;
  }) => api.post<AdminUser>('/admin/users', values),
  updateUser: (id: string, values: { email: string; displayName: string }) =>
    api.put<AdminUser>(`/admin/users/${id}`, values),
  userAction: (id: string, action: 'suspend' | 'reactivate') =>
    api.post<AdminUser>(`/admin/users/${id}/${action}`),
  resetPassword: (id: string, temporaryPassword: string) =>
    api.post<void>(`/admin/users/${id}/reset-password`, { temporaryPassword }),
  plans: (page: number, active?: boolean, search?: string, size = 20) =>
    api.get<Page<SubscriptionPlan>>(
      `/admin/plans?${queryString({
        page,
        size,
        active: active === undefined ? undefined : String(active),
        search,
      })}`,
    ),
  savePlan: (values: Partial<SubscriptionPlan> & { id?: string }) => {
    const { id, ...body } = values;
    return id
      ? api.put<SubscriptionPlan>(`/admin/plans/${id}`, body)
      : api.post<SubscriptionPlan>('/admin/plans', body);
  },
  subscriptions: (filters: { page: number; businessId?: string; status?: string }) =>
    api.get<Page<Subscription>>(`/admin/subscriptions?${queryString(filters)}`),
  saveSubscription: (values: { businessId: string; planId: string; trialDays?: number }) =>
    api.post<Subscription>('/admin/subscriptions', {
      ...values,
      trialDays: values.trialDays && values.trialDays > 0 ? values.trialDays : undefined,
    }),
  subscriptionAction: (id: string, action: string, values?: unknown) =>
    api.post<Subscription>(`/admin/subscriptions/${id}/${action}`, values),
  payments: (filters: { page: number; businessId?: string; status?: string }) =>
    api.get<Page<SubscriptionPayment>>(`/admin/subscription-payments?${queryString(filters)}`),
  createPayment: (values: {
    businessId: string;
    requestId: string;
    amount: number;
    paymentMethod: string;
    externalReference?: string | null;
  }) => api.post<SubscriptionPayment>('/admin/subscription-payments', values),
  confirmPayment: (id: string, externalReference?: string | null) =>
    api.post<SubscriptionPayment>(`/admin/subscription-payments/${id}/confirm`, {
      externalReference,
    }),
};
