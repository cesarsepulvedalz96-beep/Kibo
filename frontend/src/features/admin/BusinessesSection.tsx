import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Building2, Pencil, Plus, Power } from 'lucide-react';
import type { Business } from '../../shared/types';
import { api } from '../../shared/api/client';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { Pagination } from '../../shared/components/Pagination';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';
import { BusinessForm } from './AdminForms';

export function BusinessesSection() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Business | 'new' | null>(null);
  const [changing, setChanging] = useState<Business | null>(null);
  const query = useQuery({
    queryKey: [...adminKeys.businesses, page],
    queryFn: () => adminApi.businesses(page),
  });
  const changeStatus = useMutation({
    mutationFn: (business: Business) =>
      api.put(`/admin/businesses/${business.id}`, {
        name: business.name,
        taxId: business.taxId,
        active: !business.active,
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.businesses });
      toast(t('common.saved'));
      setChanging(null);
    },
  });
  return (
    <section>
      <div className="section-toolbar">
        <div>
          <h2>{t('admin.businesses')}</h2>
          <p>{t('admin.businessesDescription')}</p>
        </div>
        <Button onClick={() => setEditing('new')}>
          <Plus size={17} />
          {t('admin.newBusiness')}
        </Button>
      </div>
      <div className="panel">
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorState error={query.error} />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('admin.taxId')}</th>
                    <th>{t('admin.plan')}</th>
                    <th>{t('admin.subscriptionStatus')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('admin.nextBilling')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <div className="product-cell">
                          <span className="product-symbol">
                            <Building2 size={18} />
                          </span>
                          <strong>{item.name}</strong>
                        </div>
                      </td>
                      <td>{item.taxId || '—'}</td>
                      <td>{item.planName || '—'}</td>
                      <td>
                        <span className="badge">
                          {item.subscriptionStatus
                            ? t(`subscription.status.${item.subscriptionStatus}`)
                            : t('admin.unassigned')}
                        </span>
                      </td>
                      <td>
                        <span className="badge">
                          {t(item.active ? 'common.active' : 'common.inactive')}
                        </span>
                      </td>
                      <td>
                        {item.nextBillingDate
                          ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(
                              new Date(item.nextBillingDate),
                            )
                          : '—'}
                      </td>
                      <td className="numeric">
                        <div className="row-actions">
                          <button
                            className="icon-button"
                            aria-label={`${t('common.edit')} ${item.name}`}
                            onClick={() => setEditing(item)}
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            className="icon-button"
                            aria-label={`${t(item.active ? 'admin.deactivate' : 'admin.activate')} ${item.name}`}
                            onClick={() => setChanging(item)}
                          >
                            <Power size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!query.data.items.length && <EmptyState title={t('common.noData')} />}
            <Pagination page={page} total={query.data.total} onChange={setPage} />
          </>
        )}
      </div>
      {editing && (
        <BusinessForm
          business={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
        />
      )}
      {changing && (
        <ConfirmDialog
          title={t(changing.active ? 'admin.deactivateBusiness' : 'admin.activateBusiness')}
          description={t('admin.businessStatusDescription', { name: changing.name })}
          confirmLabel={t(changing.active ? 'admin.deactivate' : 'admin.activate')}
          danger={changing.active}
          busy={changeStatus.isPending}
          onClose={() => setChanging(null)}
          onConfirm={() => changeStatus.mutate(changing)}
        />
      )}
    </section>
  );
}
