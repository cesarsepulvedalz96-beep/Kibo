import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Page, Movement } from '../../shared/types';
import { dateTime } from '../../shared/format';
import { useSession } from '../auth/session';
import { useSettings } from '../settings/queries';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Loading, ErrorState, EmptyState } from '../../shared/components/Feedback';
import { Pagination } from '../../shared/components/Pagination';
import { MovementForm } from './MovementForm';
import { operationKey, readOperation } from '../../shared/hooks/usePendingOperation';

export default function InventoryPage() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const settings = useSettings();
  const [page, setPage] = useState(0);
  const [params, setParams] = useSearchParams();
  const inventoryOnly = settings.data?.operatingMode === 'INVENTORY_ONLY';
  const exitsOnly = inventoryOnly && params.get('view') === 'exits';
  const [open, setOpen] = useState(() => {
    const stored = readOperation(operationKey(session.data!, 'movement'));
    return Boolean(stored.pending || stored.error);
  });
  const movements = useQuery({
    queryKey: ['inventory', exitsOnly ? 'exits' : 'all', page],
    queryFn: () =>
      api.get<Page<Movement>>(`/inventory?page=${page}${exitsOnly ? '&type=MANUAL_EXIT' : ''}`),
  });
  return (
    <>
      <PageHeader
        title={t('inventory.title')}
        description={t('inventory.description')}
        action={
          session.data?.role === 'BUSINESS_USER' && (
            <Button onClick={() => setOpen(true)}>
              <Plus size={17} />
              {t('inventory.new')}
            </Button>
          )
        }
      />
      {inventoryOnly && (
        <div className="tabs" role="group" aria-label={t('nav.inventory')}>
          <button
            aria-pressed={!exitsOnly}
            onClick={() => {
              setPage(0);
              setParams({});
            }}
          >
            {t('inventory.movementsTab')}
          </button>
          <button
            aria-pressed={exitsOnly}
            onClick={() => {
              setPage(0);
              setParams({ view: 'exits' });
            }}
          >
            {t('inventory.exitsTab')}
          </button>
        </div>
      )}
      <section className="panel">
        <div className="panel-header">
          <h2>{t(exitsOnly ? 'inventory.exitHistory' : 'inventory.history')}</h2>
        </div>
        {movements.isPending ? (
          <Loading />
        ) : movements.isError ? (
          <ErrorState error={movements.error} retry={() => void movements.refetch()} />
        ) : (
          <>
            {movements.data.items.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{t('common.date')}</th>
                      <th>{t('products.product')}</th>
                      <th>{t('inventory.type')}</th>
                      <th className="numeric">{t('inventory.quantity')}</th>
                      <th className="numeric">{t('inventory.before')}</th>
                      <th className="numeric">{t('inventory.after')}</th>
                      <th>{t('common.user')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movements.data.items.map((movement) => (
                      <tr key={movement.id}>
                        <td className="muted">
                          {dateTime(movement.createdAt, i18n.language, settings.data?.timezone)}
                        </td>
                        <td className="strong">{movement.productName}</td>
                        <td>
                          <span className="badge">{t(`movement.${movement.type}`)}</span>
                        </td>
                        <td className="numeric strong">
                          {movement.quantity > 0 ? '+' : ''}
                          {movement.quantity}
                        </td>
                        <td className="numeric muted">{movement.previousStock}</td>
                        <td className="numeric strong">{movement.newStock}</td>
                        <td className="muted">{movement.performedBy}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title={t(exitsOnly ? 'inventory.exitEmpty' : 'inventory.empty')} />
            )}
            <Pagination page={page} total={movements.data.total} onChange={setPage} />
          </>
        )}
      </section>
      {open && <MovementForm onClose={() => setOpen(false)} />}
    </>
  );
}
