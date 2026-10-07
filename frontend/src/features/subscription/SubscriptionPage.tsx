import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CreditCard, CalendarDays } from 'lucide-react';
import { money } from '../../shared/format';
import { Button } from '../../shared/components/Button';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { Field } from '../../shared/components/Field';
import { PageHeader } from '../../shared/components/PageHeader';
import { useToast } from '../../app/toast';
import {
  usePendingSubscriptionPayments,
  useRequestSubscriptionPayment,
  useSubscription,
} from './queries';

function date(value: string | null, language: string) {
  return value
    ? new Intl.DateTimeFormat(language, { dateStyle: 'long' }).format(new Date(value))
    : '—';
}

export default function SubscriptionPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const subscription = useSubscription();
  const requestPayment = useRequestSubscriptionPayment();
  const pendingPayments = usePendingSubscriptionPayments();
  const [reference, setReference] = useState('');
  if (subscription.isPending) return <Loading />;
  if (subscription.isError)
    return <ErrorState error={subscription.error} retry={() => void subscription.refetch()} />;
  if (['SUSPENDED', 'CANCELLED'].includes(subscription.data.status))
    return <Navigate to="/subscription-suspended" replace />;
  const data = subscription.data;
  const paymentPending = Boolean(pendingPayments.data?.total);
  const dueDate = data.trialEndsAt || data.graceEndsAt || data.nextBillingDate;
  return (
    <>
      <PageHeader title={t('subscription.title')} description={t('subscription.description')} />
      <div className="subscription-layout">
        <section className="panel subscription-summary">
          <div className="subscription-heading">
            <CreditCard size={25} strokeWidth={1.5} />
            <div>
              <span className="badge">{t(`subscription.status.${data.status}`)}</span>
              <h2>{data.planName}</h2>
            </div>
          </div>
          <dl className="detail-list">
            <div>
              <dt>{t('subscription.price')}</dt>
              <dd>{money(data.monthlyPrice, i18n.language)}</dd>
            </div>
            <div>
              <dt>{t('subscription.nextBilling')}</dt>
              <dd>{date(data.nextBillingDate, i18n.language)}</dd>
            </div>
            <div>
              <dt>{t('subscription.currentPeriod')}</dt>
              <dd>
                {date(data.currentPeriodStart, i18n.language)} ·{' '}
                {date(data.currentPeriodEnd, i18n.language)}
              </dd>
            </div>
            {data.remainingDays !== null && (
              <div>
                <dt>{t('subscription.remaining')}</dt>
                <dd>{t('subscription.days', { count: data.remainingDays })}</dd>
              </div>
            )}
          </dl>
          {(data.status === 'TRIAL' || data.status === 'GRACE_PERIOD') && (
            <p className="inline-note">
              <CalendarDays size={17} />
              {t(data.status === 'TRIAL' ? 'subscription.trialEnds' : 'subscription.graceEnds', {
                date: date(dueDate, i18n.language),
              })}
            </p>
          )}
        </section>
        <section className="panel subscription-payment-panel">
          <h2>{t('subscription.requestPayment')}</h2>
          <p>{t('subscription.manualExplanation')}</p>
          <Field id="subscription-reference" label={t('subscription.reference')}>
            <input
              id="subscription-reference"
              value={reference}
              maxLength={120}
              onChange={(event) => setReference(event.target.value)}
            />
          </Field>
          {pendingPayments.isError && <ErrorState error={pendingPayments.error} />}
          {requestPayment.isError && <ErrorState error={requestPayment.error} />}
          <Button
            busy={requestPayment.isPending}
            disabled={pendingPayments.isPending || paymentPending}
            onClick={() =>
              requestPayment.mutate(
                { paymentMethod: 'MANUAL_TRANSFER', externalReference: reference.trim() || null },
                {
                  onSuccess: () => {
                    setReference('');
                    toast(t('subscription.paymentPending'));
                  },
                },
              )
            }
          >
            {t(paymentPending ? 'subscription.paymentStatus.PENDING' : 'subscription.pay')}
          </Button>
          <p className="field-hint">{t('subscription.verificationRequired')}</p>
        </section>
      </div>
    </>
  );
}
