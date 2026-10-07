import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Bell, Check } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Notification, Page } from '../../shared/types';
import { dateTime } from '../../shared/format';
import { useSettings } from '../settings/queries';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Pagination } from '../../shared/components/Pagination';
import { Loading, ErrorState, EmptyState } from '../../shared/components/Feedback';

export default function NotificationsPage() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const settings = useSettings();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['notifications', page],
    queryFn: () => api.get<Page<Notification>>(`/notifications?page=${page}`),
  });
  const read = useMutation({
    mutationFn: (id: string) => api.post(`/notifications/${id}/read`),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  });
  return (
    <>
      <PageHeader title={t('notifications.title')} description={t('notifications.description')} />
      <section className="panel">
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorState error={query.error} />
        ) : (
          <>
            {query.data.items.length ? (
              <ul className="notification-list">
                {query.data.items.map((notification) => (
                  <li
                    key={notification.id}
                    className={notification.read ? 'notification--read' : ''}
                  >
                    <span className="product-symbol">
                      <Bell size={18} />
                    </span>
                    <div>
                      <strong>
                        {t('notifications.message', {
                          name: notification.productName,
                          stock: notification.stock,
                        })}
                      </strong>
                      <span>
                        {dateTime(notification.createdAt, i18n.language, settings.data?.timezone)}
                      </span>
                    </div>
                    {notification.read ? (
                      <span className="badge">{t('notifications.readStatus')}</span>
                    ) : (
                      <Button
                        variant="ghost"
                        busy={read.isPending}
                        onClick={() => read.mutate(notification.id)}
                      >
                        <Check size={16} />
                        {t('notifications.read')}
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                title={t('notifications.empty')}
                description={t('notifications.emptyDescription')}
              />
            )}
            <Pagination page={page} total={query.data.total} onChange={setPage} />
          </>
        )}
        {read.isError && <ErrorState error={read.error} />}
      </section>
    </>
  );
}
