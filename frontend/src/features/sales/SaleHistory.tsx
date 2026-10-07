import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ReceiptText, RotateCcw } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Page, Sale } from '../../shared/types';
import { dateTime, money, shortId } from '../../shared/format';
import { Loading, ErrorState, EmptyState } from '../../shared/components/Feedback';
import { Dialog } from '../../shared/components/Dialog';
import { Button } from '../../shared/components/Button';
import { Pagination } from '../../shared/components/Pagination';
import { useSession } from '../auth/session';
import { useSettings } from '../settings/queries';
import { useToast } from '../../app/toast';

export function SaleHistory() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const settings = useSettings();
  const client = useQueryClient();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState<Sale>();
  const sales = useQuery({
    queryKey: ['sales', 'history', page],
    queryFn: () => api.get<Page<Sale>>(`/sales?page=${page}`),
  });
  const cancel = useMutation({
    mutationFn: (id: string) => api.post(`/sales/${id}/cancel`),
    onSuccess: async () => {
      await Promise.all(
        ['sales', 'products', 'reports', 'inventory', 'notifications', 'receipts'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      );
      setCancelling(undefined);
      toast(t('sales.cancelled'));
    },
  });
  return (
    <section className="panel">
      {sales.isPending ? (
        <Loading />
      ) : sales.isError ? (
        <ErrorState error={sales.error} />
      ) : (
        <>
          {sales.data.items.length ? (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('sales.id')}</th>
                    <th>{t('common.date')}</th>
                    <th>{t('pos.payment')}</th>
                    <th>{t('common.status')}</th>
                    <th className="numeric">{t('pos.total')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {sales.data.items.map((sale) => (
                    <tr key={sale.id}>
                      <td className="mono">#{shortId(sale.id)}</td>
                      <td>{dateTime(sale.createdAt, i18n.language, settings.data?.timezone)}</td>
                      <td>{t(`payment.${sale.paymentMethod}`)}</td>
                      <td>
                        <span className="badge">{t(`sales.${sale.status}`)}</span>
                      </td>
                      <td className="numeric strong">{money(sale.total, i18n.language)}</td>
                      <td>
                        <div className="row-actions">
                          {settings.data?.generateReceipt && (
                            <Link
                              className="icon-button"
                              to={`/receipts/${sale.id}`}
                              aria-label={`${t('pos.receipt')} ${shortId(sale.id)}`}
                            >
                              <ReceiptText size={17} />
                            </Link>
                          )}
                          {sale.status === 'CONFIRMED' &&
                            session.data?.role === 'BUSINESS_USER' && (
                              <button
                                className="icon-button"
                                onClick={() => {
                                  cancel.reset();
                                  setCancelling(sale);
                                }}
                                aria-label={`${t('sales.cancel')} ${shortId(sale.id)}`}
                              >
                                <RotateCcw size={16} />
                              </button>
                            )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState
              title={t('dashboard.emptySales')}
              description={t('dashboard.emptyDescription')}
            />
          )}
          <Pagination page={page} total={sales.data.total} onChange={setPage} />
        </>
      )}
      {cancelling && (
        <Dialog
          title={t('sales.cancel')}
          onClose={() => {
            if (!cancel.isPending) setCancelling(undefined);
          }}
        >
          <p>{t('sales.cancelDescription')}</p>
          <div className="confirmation-amount">{money(cancelling.total, i18n.language)}</div>
          {cancel.isError && <ErrorState error={cancel.error} />}
          <div className="dialog-actions">
            <Button
              variant="secondary"
              onClick={() => setCancelling(undefined)}
              disabled={cancel.isPending}
            >
              {t('common.back')}
            </Button>
            <Button
              variant="danger"
              busy={cancel.isPending}
              onClick={() => cancel.mutate(cancelling.id)}
            >
              {t('sales.cancel')}
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
