import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Settings } from '../../shared/types';

export function useSettings() {
  return useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings') });
}
