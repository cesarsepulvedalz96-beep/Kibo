import { useQuery } from '@tanstack/react-query';
import { Package } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Product } from '../../shared/types';

export function ProductImage({ product }: { product: Product }) {
  const image = useQuery({
    queryKey: ['products', product.id, 'image', product.imageKey],
    queryFn: () => api.get<{ url: string }>(`/storage/products/${product.id}`),
    enabled: !!product.imageKey,
    staleTime: 180_000,
    retry: false,
  });
  return (
    <span className="product-symbol">
      {image.data ? (
        <img src={image.data.url} alt="" loading="lazy" width="38" height="38" />
      ) : (
        <Package size={19} strokeWidth={1.5} />
      )}
    </span>
  );
}
