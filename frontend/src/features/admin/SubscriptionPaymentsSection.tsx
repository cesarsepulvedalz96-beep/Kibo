import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { SubscriptionPayment, SubscriptionPaymentStatus } from '../../shared/types';
import { money, dateTime } from '../../shared/format';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { Field } from '../../shared/components/Field';
import { Pagination } from '../../shared/components/Pagination';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';

const paymentSchema = z.object({
  businessId: z.string().min(1),
  amount: z.number().positive().max(999_999_999),
  paymentMethod: z.enum(['MANUAL_TRANSFER', 'MANUAL_CASH', 'OTHER']),
  externalReference: z.string().trim().max(120),
});
type PaymentValues = z.infer<typeof paymentSchema>;

function PaymentDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [requestId] = useState(() => crypto.randomUUID());
  const businesses = useQuery({
    queryKey: [...adminKeys.businesses, 'lookup'],
    queryFn: adminApi.businessLookup,
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PaymentValues>({
    resolver: zodResolver(paymentSchema),
    defaultValues: {
      businessId: '',
      amount: 0,
      paymentMethod: 'MANUAL_TRANSFER',
      externalReference: '',
    },
  });
  const create = useMutation({
    mutationFn: (values: PaymentValues) =>
      adminApi.createPayment({
        ...values,
        requestId,
        externalReference: values.externalReference || null,
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.payments });
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog
      title={t('admin.registerPayment')}
      onClose={create.isPending ? () => undefined : onClose}
      wide
    >
      <form className="form-stack" onSubmit={handleSubmit((values) => create.mutate(values))}>
        <div className="form-grid">
          <Field
            id="payment-business"
            label={t('admin.business')}
            error={errors.businessId && t('validation.required')}
          >
            <select id="payment-business" disabled={create.isPending} {...register('businessId')}>
              <option value="">{t('admin.selectBusiness')}</option>
              {businesses.data?.map((business) => (
                <option key={business.id} value={business.id}>
                  {business.name}
                </option>
              ))}
            </select>
          </Field>
          <Field
            id="payment-amount"
            label={t('admin.amount')}
            error={errors.amount && t('validation.required')}
          >
            <input
              id="payment-amount"
              type="number"
              min="1"
              step="1"
              disabled={create.isPending}
              {...register('amount', { valueAsNumber: true })}
            />
          </Field>
          <Field id="payment-method" label={t('admin.paymentMethod')}>
            <select id="payment-method" disabled={create.isPending} {...register('paymentMethod')}>
              <option value="MANUAL_TRANSFER">
                {t('subscription.paymentMethod.MANUAL_TRANSFER')}
              </option>
              <option value="MANUAL_CASH">{t('subscription.paymentMethod.MANUAL_CASH')}</option>
              <option value="OTHER">{t('subscription.paymentMethod.OTHER')}</option>
            </select>
          </Field>
          <Field
            id="payment-reference"
            label={t('subscription.reference')}
            hint={t('common.optional')}
            error={errors.externalReference && t('validation.required')}
          >
            <input
              id="payment-reference"
              maxLength={120}
              disabled={create.isPending}
              {...register('externalReference')}
            />
          </Field>
        </div>
        {businesses.isError && <ErrorState error={businesses.error} />}
        {create.isError && <ErrorState error={create.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" disabled={create.isPending} onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" busy={create.isPending}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function ConfirmPaymentDialog({
  payment,
  onClose,
}: {
  payment: SubscriptionPayment;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [reference, setReference] = useState(payment.externalReference || '');
  const confirm = useMutation({
    mutationFn: () => adminApi.confirmPayment(payment.id, reference.trim() || null),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: adminKeys.payments }),
        client.invalidateQueries({ queryKey: adminKeys.subscriptions }),
        client.invalidateQueries({ queryKey: adminKeys.businesses }),
      ]);
      toast(t('admin.paymentConfirmed'));
      onClose();
    },
  });
  return (
    <Dialog
      title={t('admin.confirmPayment')}
      onClose={confirm.isPending ? () => undefined : onClose}
    >
      <div className="form-stack">
        <p>{t('admin.confirmPaymentDescription', { name: payment.businessName })}</p>
        <Field
          id="confirm-payment-reference"
          label={t('subscription.reference')}
          hint={t('common.optional')}
        >
          <input
            id="confirm-payment-reference"
            maxLength={120}
            value={reference}
            disabled={confirm.isPending}
            onChange={(event) => setReference(event.target.value)}
          />
        </Field>
        {confirm.isError && <ErrorState error={confirm.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" disabled={confirm.isPending} onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button busy={confirm.isPending} onClick={() => confirm.mutate()}>
            {t('admin.confirmPayment')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

export function SubscriptionPaymentsSection() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(0);
  const [businessId, setBusinessId] = useState('');
  const [status, setStatus] = useState('');
  const [creating, setCreating] = useState(false);
  const [confirming, setConfirming] = useState<SubscriptionPayment | null>(null);
  const businesses = useQuery({
    queryKey: [...adminKeys.businesses, 'lookup'],
    queryFn: adminApi.businessLookup,
  });
  const payments = useQuery({
    queryKey: [...adminKeys.payments, page, businessId, status],
    queryFn: () =>
      adminApi.payments({ page, businessId: businessId || undefined, status: status || undefined }),
  });
  const statuses: SubscriptionPaymentStatus[] = ['PENDING', 'PAID', 'FAILED', 'CANCELLED'];
  return (
    <section>
      <div className="section-toolbar">
        <div>
          <h2>{t('admin.payments')}</h2>
          <p>{t('admin.paymentsDescription')}</p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus size={17} />
          {t('admin.registerPayment')}
        </Button>
      </div>
      <div className="filters admin-filters">
        <Field id="payments-business" label={t('admin.business')}>
          <select
            id="payments-business"
            value={businessId}
            onChange={(event) => {
              setBusinessId(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t('common.all')}</option>
            {businesses.data?.map((business) => (
              <option key={business.id} value={business.id}>
                {business.name}
              </option>
            ))}
          </select>
        </Field>
        <Field id="payments-status" label={t('common.status')}>
          <select
            id="payments-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t('common.all')}</option>
            {statuses.map((value) => (
              <option key={value} value={value}>
                {t(`subscription.paymentStatus.${value}`)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="panel">
        {payments.isPending ? (
          <Loading />
        ) : payments.isError ? (
          <ErrorState error={payments.error} retry={() => void payments.refetch()} />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('admin.business')}</th>
                    <th>{t('admin.amount')}</th>
                    <th>{t('admin.paymentMethod')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('subscription.reference')}</th>
                    <th>{t('common.date')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.data.items.map((payment) => (
                    <tr key={payment.id}>
                      <td className="strong">{payment.businessName}</td>
                      <td>{money(payment.amount, i18n.language)}</td>
                      <td>{t(`subscription.paymentMethod.${payment.paymentMethod}`)}</td>
                      <td>
                        <span
                          className={`badge ${payment.status === 'PENDING' ? 'badge--attention' : ''}`}
                        >
                          {t(`subscription.paymentStatus.${payment.status}`)}
                        </span>
                      </td>
                      <td>{payment.externalReference || '—'}</td>
                      <td>{dateTime(payment.paidAt || payment.createdAt, i18n.language)}</td>
                      <td className="numeric">
                        {payment.status === 'PENDING' && (
                          <button
                            className="icon-button"
                            aria-label={`${t('admin.confirmPayment')} ${payment.businessName}`}
                            onClick={() => setConfirming(payment)}
                          >
                            <Check size={16} />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!payments.data.items.length && <EmptyState title={t('common.noData')} />}
            <Pagination page={page} total={payments.data.total} onChange={setPage} />
          </>
        )}
      </div>
      {creating && <PaymentDialog onClose={() => setCreating(false)} />}
      {confirming && (
        <ConfirmPaymentDialog payment={confirming} onClose={() => setConfirming(null)} />
      )}
    </section>
  );
}
