import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LockKeyhole } from 'lucide-react';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { useToast } from '../../app/toast';
import {
  usePendingSubscriptionPayments,
  useRequestSubscriptionPayment,
  useSubscription,
} from './queries';

export default function SuspendedPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const subscription = useSubscription();
  const payment = useRequestSubscriptionPayment();
  const pendingPayments = usePendingSubscriptionPayments();
  if (subscription.isPending) return <Loading />;
  if (subscription.data && !['SUSPENDED', 'CANCELLED'].includes(subscription.data.status))
    return <Navigate to="/" replace />;
  return (
    <section className="suspended-state">
      <LockKeyhole size={34} strokeWidth={1.4} />
      <h1>{t('subscription.suspendedTitle')}</h1>
      <p>{t('subscription.suspendedDescription')}</p>
      {subscription.isError && <ErrorState error={subscription.error} />}
      {pendingPayments.isError && <ErrorState error={pendingPayments.error} />}
      {payment.isError && <ErrorState error={payment.error} />}
      <Button
        busy={payment.isPending}
        disabled={pendingPayments.isPending || Boolean(pendingPayments.data?.total)}
        onClick={() =>
          payment.mutate(
            { paymentMethod: 'MANUAL_TRANSFER' },
            { onSuccess: () => toast(t('subscription.paymentPending')) },
          )
        }
      >
        {t(pendingPayments.data?.total ? 'subscription.paymentStatus.PENDING' : 'subscription.pay')}
      </Button>
      <span className="field-hint">{t('subscription.verificationRequired')}</span>
    </section>
  );
}
