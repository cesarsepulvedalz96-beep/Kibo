import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSession } from '../auth/session';
import { useSubscription } from './queries';

export function SubscriptionBanner() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const subscription = useSubscription(session.data?.role === 'BUSINESS_USER');
  if (session.data?.role !== 'BUSINESS_USER' || !subscription.data) return null;
  const { status, remainingDays, graceEndsAt } = subscription.data;
  if (status !== 'TRIAL' && status !== 'GRACE_PERIOD') return null;
  return (
    <aside className="subscription-banner" role="status">
      <span>
        {status === 'TRIAL'
          ? t('subscription.trialBanner', { count: remainingDays ?? 0 })
          : t('subscription.graceBanner', {
              date: graceEndsAt
                ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(
                    new Date(graceEndsAt),
                  )
                : t('common.noData'),
            })}
      </span>
      <Link to="/settings/subscription">{t('subscription.view')}</Link>
    </aside>
  );
}
