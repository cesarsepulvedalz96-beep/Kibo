import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { Category, Page } from '../../shared/types';

/** Small reference catalog, fetched in bounded pages. Sales are never aggregated in the browser. */
export function useCategories() {
  return useQuery({
    queryKey: ['categories', 'lookup'],
    queryFn: async () => {
      const categories: Category[] = [];
      let page = 0;
      let total = 0;
      do {
        const result = await api.get<Page<Category>>(`/categories?size=100&page=${page++}`);
        categories.push(...result.items);
        total = result.total;
      } while (categories.length < total);
      return categories;
    },
  });
}
