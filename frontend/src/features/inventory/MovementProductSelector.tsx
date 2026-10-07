import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { api, queryString } from '../../shared/api/client';
import type { Page, Product } from '../../shared/types';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import { Field } from '../../shared/components/Field';
import { ErrorState } from '../../shared/components/Feedback';
import { useCategories } from '../categories/queries';

const PAGE_SIZE = 20;

/** Fetch only the visible page; changing filters cannot leave an unrelated product selected. */
export function MovementProductSelector({
  product,
  onChange,
  disabled,
}: {
  product?: Product;
  onChange: (product: Product | undefined) => void;
  disabled: boolean;
}) {
  const { t } = useTranslation();
  const categories = useCategories();
  const [categoryId, setCategoryId] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search);
  const products = useQuery({
    queryKey: ['products', 'movement-selector', categoryId, debounced, page],
    queryFn: () =>
      api.get<Page<Product>>(
        `/products?${queryString({
          categoryId,
          search: debounced,
          status: 'active',
          page,
          size: PAGE_SIZE,
        })}`,
      ),
    enabled: !disabled,
  });
  const waiting = products.isFetching || search !== debounced;
  const options = products.data?.items || [];
  const hasSelectedOption = options.some((item) => item.id === product?.id);

  function filter(nextCategory: string, nextSearch: string) {
    setCategoryId(nextCategory);
    setSearch(nextSearch);
    setPage(0);
    const term = nextSearch.trim().toLocaleLowerCase();
    if (
      product &&
      ((nextCategory && product.categoryId !== nextCategory) ||
        !`${product.name} ${product.barcode || ''}`.toLocaleLowerCase().includes(term))
    )
      onChange(undefined);
  }

  return (
    <div className="movement-product-selector form-stack">
      <div className="form-grid">
        <Field id="movement-category" label={t('products.category')}>
          <select
            id="movement-category"
            value={categoryId}
            disabled={disabled || categories.isPending}
            onChange={(event) => filter(event.target.value, search)}
          >
            <option value="">{t('products.allCategories')}</option>
            {categories.data?.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </Field>
        <Field id="movement-search" label={t('inventory.searchProduct')}>
          <input
            id="movement-search"
            value={search}
            disabled={disabled}
            autoComplete="off"
            placeholder={t('inventory.searchPlaceholder')}
            onChange={(event) => filter(categoryId, event.target.value)}
            onKeyDown={(event) => {
              // Searching (including an HID scanner's Enter) must never confirm stock changes.
              if (event.key === 'Enter') event.preventDefault();
            }}
          />
        </Field>
      </div>
      {categories.isError && (
        <ErrorState error={categories.error} retry={() => void categories.refetch()} />
      )}
      <Field
        id="movement-product"
        label={t('products.product')}
        hint={waiting ? t('common.loading') : undefined}
      >
        <select
          id="movement-product"
          value={product?.id || ''}
          disabled={disabled || waiting || products.isError}
          onChange={(event) => onChange(options.find((item) => item.id === event.target.value))}
        >
          <option value="">{t('inventory.selectProduct')}</option>
          {product && !hasSelectedOption && <option value={product.id}>{product.name}</option>}
          {options.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
              {item.barcode ? ` · ${item.barcode}` : ''}
            </option>
          ))}
        </select>
      </Field>
      {products.isError && (
        <ErrorState error={products.error} retry={() => void products.refetch()} />
      )}
      {!disabled && products.data && (
        <div className="pagination selector-pagination" aria-label={t('inventory.productPages')}>
          <span>
            {t('common.results', {
              from: products.data.total ? page * PAGE_SIZE + 1 : 0,
              to: Math.min((page + 1) * PAGE_SIZE, products.data.total),
              total: products.data.total,
            })}
          </span>
          <div>
            <button
              type="button"
              className="icon-button"
              aria-label={t('common.previous')}
              disabled={waiting || page === 0}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft size={18} />
            </button>
            <span>{page + 1}</span>
            <button
              type="button"
              className="icon-button"
              aria-label={t('common.next')}
              disabled={waiting || (page + 1) * PAGE_SIZE >= products.data.total}
              onClick={() => setPage(page + 1)}
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
