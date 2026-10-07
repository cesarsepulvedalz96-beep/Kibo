import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ScanBarcode, Search, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { api, queryString } from '../../shared/api/client';
import type { Page, Product } from '../../shared/types';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { money } from '../../shared/format';

export function ProductSearch({
  onSelect,
  scanner = false,
  disabled = false,
  onScanningChange,
}: {
  onSelect: (product: Product) => void;
  scanner?: boolean;
  disabled?: boolean;
  onScanningChange?: (scanning: boolean) => void;
}) {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useState('');
  const [scanError, setScanError] = useState<unknown>();
  const queue = useRef<string[]>([]);
  const processing = useRef(false);
  const mounted = useRef(true);
  const callbacks = useRef({ onSelect, onScanningChange });
  useEffect(() => {
    callbacks.current = { onSelect, onScanningChange };
  }, [onSelect, onScanningChange]);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      queue.current = [];
      callbacks.current.onScanningChange?.(false);
    };
  }, []);
  const debounced = useDebouncedValue(search);
  const query = useQuery({
    queryKey: ['products', 'search', debounced],
    queryFn: () =>
      api.get<Page<Product>>(
        `/products?${queryString({ search: debounced, status: 'active', size: 10 })}`,
      ),
    enabled: debounced.trim().length > 0,
  });

  function select(product: Product) {
    onSelect(product);
    setSearch('');
    setScanError(undefined);
  }

  async function drainScans() {
    if (processing.current) return;
    processing.current = true;
    callbacks.current.onScanningChange?.(true);
    while (queue.current.length && mounted.current) {
      const barcode = queue.current.shift()!;
      try {
        const product = await api.get<Product>(`/products/barcode/${encodeURIComponent(barcode)}`);
        if (mounted.current) callbacks.current.onSelect(product);
      } catch (error) {
        if (mounted.current) setScanError(error);
      }
    }
    processing.current = false;
    if (mounted.current) callbacks.current.onScanningChange?.(false);
  }

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    const barcode = event.currentTarget.value.trim();
    if (!barcode || disabled || event.repeat) return;
    if (!scanner) {
      if (query.data?.items.length === 1) select(query.data.items[0]);
      return;
    }
    // Clear synchronously: a scanner may send the next code before React renders.
    event.currentTarget.value = '';
    setSearch('');
    setScanError(undefined);
    queue.current.push(barcode);
    void drainScans();
  }

  return (
    <div className="product-search">
      <div className="search-input search-input--large">
        {scanner ? <ScanBarcode size={22} /> : <Search size={20} />}
        <input
          aria-label={t('pos.search')}
          placeholder={t('pos.search')}
          value={search}
          autoComplete="off"
          disabled={disabled}
          onChange={(event) => {
            setSearch(event.target.value);
            setScanError(undefined);
          }}
          onKeyDown={(event) => void keyDown(event)}
        />
        {scanner && <kbd>↵</kbd>}
      </div>
      <p className="field-hint">{t(scanner ? 'pos.scannerHint' : 'pos.searchHint')}</p>
      {scanError !== undefined && <ErrorState error={scanError} />}
      {search.trim() && (
        <div className="search-results">
          {query.isFetching ? (
            <Loading />
          ) : query.isError ? (
            <ErrorState error={query.error} />
          ) : query.data?.items.length ? (
            query.data.items.map((product) => (
              <button
                type="button"
                key={product.id}
                onClick={() => select(product)}
                disabled={disabled}
              >
                <div>
                  <strong>{product.name}</strong>
                  <span>
                    {product.barcode} · {product.stock} {t('common.units')}
                  </span>
                </div>
                <span>{money(product.salePrice, i18n.language)}</span>
                <Plus size={17} />
              </button>
            ))
          ) : (
            <p>{t('common.noResults')}</p>
          )}
        </div>
      )}
    </div>
  );
}
