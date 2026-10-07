import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Search, Pencil, SlidersHorizontal } from 'lucide-react';
import { api, queryString } from '../../shared/api/client';
import type { Page, Product } from '../../shared/types';
import { money } from '../../shared/format';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Pagination } from '../../shared/components/Pagination';
import { Loading, ErrorState, EmptyState } from '../../shared/components/Feedback';
import { useCategories } from '../categories/queries';
import { CategoriesDialog } from '../categories/CategoriesDialog';
import { useSession } from '../auth/session';
import { ProductForm } from './ProductForm';
import { ProductImage } from './ProductImage';

export default function ProductsPage() {
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const [categoriesOpen, setCategoriesOpen] = useState(params.get('categories') === 'open');
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState(params.get('status') === 'low' ? 'low' : 'all');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const debounced = useDebouncedValue(search);
  const categories = useCategories();
  const session = useSession();
  const manager = session.data?.role === 'BUSINESS_USER';
  const products = useQuery({
    queryKey: ['products', debounced, categoryId, status, page],
    queryFn: () =>
      api.get<Page<Product>>(
        `/products?${queryString({ search: debounced, categoryId, status, page, size: 20 })}`,
      ),
  });
  return (
    <>
      <PageHeader
        title={t('products.title')}
        description={t('products.description')}
        action={
          <>
            <Button variant="secondary" onClick={() => setCategoriesOpen(true)}>
              {t('products.categories')}
            </Button>
            {manager && (
              <Button onClick={() => setEditing('new')}>
                <Plus size={17} />
                {t('products.new')}
              </Button>
            )}
          </>
        }
      />
      <section className="panel">
        <div className="filters">
          <div className="search-input">
            <Search size={18} />
            <input
              aria-label={t('products.search')}
              placeholder={t('products.search')}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
            />
          </div>
          <div className="filter-controls">
            <SlidersHorizontal className="muted" size={17} />
            <select
              aria-label={t('products.category')}
              value={categoryId}
              onChange={(event) => {
                setCategoryId(event.target.value);
                setPage(0);
              }}
            >
              <option value="">{t('products.allCategories')}</option>
              {categories.data?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            <select
              aria-label={t('common.status')}
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
            >
              <option value="all">{t('common.all')}</option>
              <option value="active">{t('common.active')}</option>
              <option value="inactive">{t('common.inactive')}</option>
              <option value="low">{t('products.low')}</option>
            </select>
          </div>
        </div>
        {products.isPending ? (
          <Loading />
        ) : products.isError ? (
          <ErrorState error={products.error} retry={() => void products.refetch()} />
        ) : products.data.items.length ? (
          <>
            <div className="table-scroll">
              <table className="products-table">
                <thead>
                  <tr>
                    <th>{t('products.product')}</th>
                    <th>{t('products.category')}</th>
                    <th className="numeric">{t('products.price')}</th>
                    <th className="numeric">{t('products.stock')}</th>
                    <th>{t('common.status')}</th>
                    {manager && <th className="numeric">{t('common.actions')}</th>}
                  </tr>
                </thead>
                <tbody>
                  {products.data.items.map((product) => (
                    <tr key={product.id}>
                      <td>
                        <div className="product-cell">
                          <ProductImage product={product} />
                          <div>
                            <strong>{product.name}</strong>
                            <span className="mono">{product.barcode || '—'}</span>
                          </div>
                        </div>
                      </td>
                      <td className="muted">
                        {categories.data?.find((category) => category.id === product.categoryId)
                          ?.name || '—'}
                      </td>
                      <td className="numeric">{money(product.salePrice, i18n.language)}</td>
                      <td className="numeric strong">{product.stock}</td>
                      <td>
                        <span
                          className={`badge ${product.active && product.stock <= product.minimumStock ? 'badge--attention' : ''}`}
                        >
                          {t(
                            !product.active
                              ? 'common.inactive'
                              : product.stock === 0
                                ? 'products.out'
                                : product.stock <= product.minimumStock
                                  ? 'products.low'
                                  : 'products.inStock',
                          )}
                        </span>
                      </td>
                      {manager && (
                        <td className="numeric">
                          <button
                            className="icon-button"
                            aria-label={`${t('common.edit')} ${product.name}`}
                            onClick={() => setEditing(product)}
                          >
                            <Pencil size={16} />
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} total={products.data.total} onChange={setPage} />
          </>
        ) : (
          <EmptyState
            title={t(
              search || categoryId || status !== 'all' ? 'common.noResults' : 'products.empty',
            )}
            description={t(
              search || categoryId || status !== 'all'
                ? 'common.adjustSearch'
                : 'products.emptyDescription',
            )}
          />
        )}
      </section>
      {editing && (
        <ProductForm
          product={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
        />
      )}
      {categoriesOpen && (
        <CategoriesDialog
          onClose={() => {
            setCategoriesOpen(false);
            if (params.has('categories')) {
              const next = new URLSearchParams(params);
              next.delete('categories');
              setParams(next, { replace: true });
            }
          }}
        />
      )}
    </>
  );
}
