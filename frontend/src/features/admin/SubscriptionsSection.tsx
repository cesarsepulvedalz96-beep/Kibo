import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CalendarPlus, Pencil, Play, Plus, Power, XCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Subscription, SubscriptionStatus } from '../../shared/types';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { Dialog } from '../../shared/components/Dialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { Field } from '../../shared/components/Field';
import { Pagination } from '../../shared/components/Pagination';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';

const assignmentSchema = z.object({
  businessId: z.string().min(1),
  planId: z.string().min(1),
  trialDays: z.number().int().min(0).max(3650),
});
type AssignmentValues = z.infer<typeof assignmentSchema>;

function date(value: string | null, language: string) {
  return value
    ? new Intl.DateTimeFormat(language, { dateStyle: 'medium' }).format(new Date(value))
    : '—';
}

function AssignmentDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const businesses = useQuery({
    queryKey: [...adminKeys.businesses, 'lookup'],
    queryFn: adminApi.businessLookup,
  });
  const plans = useQuery({
    queryKey: [...adminKeys.plans, 'lookup', 'active'],
    queryFn: () => adminApi.plans(0, true, '', 100),
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AssignmentValues>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: { businessId: '', planId: '', trialDays: 0 },
  });
  const save = useMutation({
    mutationFn: adminApi.saveSubscription,
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: adminKeys.subscriptions }),
        client.invalidateQueries({ queryKey: adminKeys.businesses }),
      ]);
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog
      title={t('admin.assignSubscription')}
      onClose={save.isPending ? () => undefined : onClose}
    >
      <form className="form-stack" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <Field
          id="subscription-business"
          label={t('admin.business')}
          error={errors.businessId && t('validation.required')}
        >
          <select id="subscription-business" disabled={save.isPending} {...register('businessId')}>
            <option value="">{t('admin.selectBusiness')}</option>
            {businesses.data?.map((business) => (
              <option key={business.id} value={business.id}>
                {business.name}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id="subscription-plan"
          label={t('admin.plan')}
          error={errors.planId && t('validation.required')}
        >
          <select id="subscription-plan" disabled={save.isPending} {...register('planId')}>
            <option value="">{t('admin.selectPlan')}</option>
            {plans.data?.items.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id="trial-days"
          label={t('admin.trialDays')}
          hint={t('admin.trialDaysHint')}
          error={errors.trialDays && t('validation.required')}
        >
          <input
            id="trial-days"
            type="number"
            min="0"
            max="3650"
            disabled={save.isPending}
            {...register('trialDays', { valueAsNumber: true })}
          />
        </Field>
        {(businesses.isError || plans.isError) && (
          <ErrorState error={businesses.error || plans.error} />
        )}
        {save.isError && <ErrorState error={save.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" disabled={save.isPending} onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" busy={save.isPending}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function ChangePlanDialog({
  subscription,
  onClose,
}: {
  subscription: Subscription;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [planId, setPlanId] = useState(subscription.planId);
  const plans = useQuery({
    queryKey: [...adminKeys.plans, 'lookup', 'active'],
    queryFn: () => adminApi.plans(0, true, '', 100),
  });
  const change = useMutation({
    mutationFn: () => adminApi.subscriptionAction(subscription.id, 'change-plan', { planId }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.subscriptions });
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog title={t('admin.changePlan')} onClose={change.isPending ? () => undefined : onClose}>
      <div className="form-stack">
        <Field id="change-plan" label={t('admin.plan')}>
          <select
            id="change-plan"
            value={planId}
            disabled={change.isPending}
            onChange={(event) => setPlanId(event.target.value)}
          >
            {!plans.data?.items.some((plan) => plan.id === subscription.planId) && (
              <option value={subscription.planId}>{subscription.planName}</option>
            )}
            {plans.data?.items.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name}
              </option>
            ))}
          </select>
        </Field>
        {plans.isError && <ErrorState error={plans.error} />}
        {change.isError && <ErrorState error={change.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" disabled={change.isPending} onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            busy={change.isPending}
            disabled={!planId || planId === subscription.planId}
            onClick={() => change.mutate()}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

function TrialDialog({
  subscription,
  mode,
  onClose,
}: {
  subscription: Subscription;
  mode: 'start-trial' | 'extend-trial';
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [trialDays, setTrialDays] = useState(15);
  const save = useMutation({
    mutationFn: () => adminApi.subscriptionAction(subscription.id, mode, { trialDays }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.subscriptions });
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog
      title={t(mode === 'start-trial' ? 'admin.startTrial' : 'admin.extendTrial')}
      onClose={save.isPending ? () => undefined : onClose}
    >
      <div className="form-stack">
        <p>{subscription.businessName}</p>
        <Field id="change-trial-days" label={t('admin.trialDays')}>
          <input
            id="change-trial-days"
            type="number"
            min="1"
            max="3650"
            value={trialDays}
            disabled={save.isPending}
            onChange={(event) => setTrialDays(event.currentTarget.valueAsNumber)}
          />
        </Field>
        {save.isError && <ErrorState error={save.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" disabled={save.isPending} onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            busy={save.isPending}
            disabled={!Number.isInteger(trialDays) || trialDays < 1 || trialDays > 3650}
            onClick={() => save.mutate()}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

type ConfirmAction = {
  subscription: Subscription;
  action: 'suspend' | 'reactivate' | 'activate' | 'cancel';
};

export function SubscriptionsSection() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [businessId, setBusinessId] = useState('');
  const [status, setStatus] = useState('');
  const [assigning, setAssigning] = useState(false);
  const [changingPlan, setChangingPlan] = useState<Subscription | null>(null);
  const [trial, setTrial] = useState<{
    subscription: Subscription;
    mode: 'start-trial' | 'extend-trial';
  } | null>(null);
  const [confirming, setConfirming] = useState<ConfirmAction | null>(null);
  const businesses = useQuery({
    queryKey: [...adminKeys.businesses, 'lookup'],
    queryFn: adminApi.businessLookup,
  });
  const query = useQuery({
    queryKey: [...adminKeys.subscriptions, page, businessId, status],
    queryFn: () =>
      adminApi.subscriptions({
        page,
        businessId: businessId || undefined,
        status: status || undefined,
      }),
  });
  const action = useMutation({
    mutationFn: ({ subscription, action }: ConfirmAction) =>
      adminApi.subscriptionAction(subscription.id, action),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: adminKeys.subscriptions }),
        client.invalidateQueries({ queryKey: adminKeys.businesses }),
      ]);
      toast(t('common.saved'));
      setConfirming(null);
    },
  });
  const statuses: SubscriptionStatus[] = [
    'TRIAL',
    'ACTIVE',
    'GRACE_PERIOD',
    'SUSPENDED',
    'CANCELLED',
  ];
  return (
    <section>
      <div className="section-toolbar">
        <div>
          <h2>{t('admin.subscriptions')}</h2>
          <p>{t('admin.subscriptionsDescription')}</p>
        </div>
        <Button onClick={() => setAssigning(true)}>
          <Plus size={17} />
          {t('admin.assignSubscription')}
        </Button>
      </div>
      <div className="filters admin-filters">
        <Field id="subscriptions-business" label={t('admin.business')}>
          <select
            id="subscriptions-business"
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
        <Field id="subscriptions-status" label={t('common.status')}>
          <select
            id="subscriptions-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t('common.all')}</option>
            {statuses.map((value) => (
              <option key={value} value={value}>
                {t(`subscription.status.${value}`)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="panel">
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('admin.business')}</th>
                    <th>{t('admin.plan')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('admin.startedAt')}</th>
                    <th>{t('admin.nextBilling')}</th>
                    <th>{t('admin.grace')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.items.map((item) => (
                    <tr key={item.id}>
                      <td className="strong">{item.businessName}</td>
                      <td>{item.planName}</td>
                      <td>
                        <span className="badge">{t(`subscription.status.${item.status}`)}</span>
                      </td>
                      <td>{date(item.startedAt, i18n.language)}</td>
                      <td>{date(item.nextBillingDate, i18n.language)}</td>
                      <td>{date(item.graceEndsAt, i18n.language)}</td>
                      <td>
                        <div className="row-actions">
                          {item.status === 'TRIAL' ? (
                            <button
                              className="icon-button"
                              aria-label={`${t('admin.extendTrial')} ${item.businessName}`}
                              onClick={() => setTrial({ subscription: item, mode: 'extend-trial' })}
                            >
                              <CalendarPlus size={16} />
                            </button>
                          ) : (
                            item.status !== 'CANCELLED' && (
                              <button
                                className="icon-button"
                                aria-label={`${t('admin.startTrial')} ${item.businessName}`}
                                onClick={() =>
                                  setTrial({ subscription: item, mode: 'start-trial' })
                                }
                              >
                                <CalendarPlus size={16} />
                              </button>
                            )
                          )}
                          {item.status !== 'CANCELLED' && (
                            <button
                              className="icon-button"
                              aria-label={`${t('admin.changePlan')} ${item.businessName}`}
                              onClick={() => setChangingPlan(item)}
                            >
                              <Pencil size={16} />
                            </button>
                          )}
                          {item.status === 'TRIAL' && (
                            <button
                              className="icon-button"
                              aria-label={`${t('admin.activate')} ${item.businessName}`}
                              onClick={() =>
                                setConfirming({ subscription: item, action: 'activate' })
                              }
                            >
                              <Play size={16} />
                            </button>
                          )}
                          {item.status === 'SUSPENDED' ? (
                            <button
                              className="icon-button"
                              aria-label={`${t('admin.reactivate')} ${item.businessName}`}
                              onClick={() =>
                                setConfirming({ subscription: item, action: 'reactivate' })
                              }
                            >
                              <Play size={16} />
                            </button>
                          ) : (
                            item.status !== 'CANCELLED' && (
                              <button
                                className="icon-button"
                                aria-label={`${t('admin.suspend')} ${item.businessName}`}
                                onClick={() =>
                                  setConfirming({ subscription: item, action: 'suspend' })
                                }
                              >
                                <Power size={16} />
                              </button>
                            )
                          )}
                          {item.status !== 'CANCELLED' && (
                            <button
                              className="icon-button"
                              aria-label={`${t('admin.cancelSubscription')} ${item.businessName}`}
                              onClick={() =>
                                setConfirming({ subscription: item, action: 'cancel' })
                              }
                            >
                              <XCircle size={16} />
                            </button>
                          )}
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
      {assigning && <AssignmentDialog onClose={() => setAssigning(false)} />}
      {changingPlan && (
        <ChangePlanDialog subscription={changingPlan} onClose={() => setChangingPlan(null)} />
      )}
      {trial && (
        <TrialDialog
          subscription={trial.subscription}
          mode={trial.mode}
          onClose={() => setTrial(null)}
        />
      )}
      {confirming && (
        <ConfirmDialog
          title={t(`admin.${confirming.action}Subscription`)}
          description={t('admin.subscriptionActionDescription', {
            name: confirming.subscription.businessName,
          })}
          confirmLabel={t(`admin.${confirming.action}`)}
          danger={confirming.action === 'suspend' || confirming.action === 'cancel'}
          busy={action.isPending}
          onClose={() => setConfirming(null)}
          onConfirm={() => action.mutate(confirming)}
        />
      )}
    </section>
  );
}
