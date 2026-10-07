import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Pencil, Plus, Power } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { SubscriptionPlan } from '../../shared/types';
import { money } from '../../shared/format';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { Dialog } from '../../shared/components/Dialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { Field } from '../../shared/components/Field';
import { Pagination } from '../../shared/components/Pagination';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';

const planSchema = z.object({
  name: z.string().trim().min(1).max(120),
  description: z.string().trim().max(500),
  monthlyPrice: z.number().min(0).max(999_999_999),
  billingPeriodDays: z.number().int().min(1).max(366),
  gracePeriodDays: z.number().int().min(0).max(90),
});
type PlanValues = z.infer<typeof planSchema>;

function PlanForm({ plan, onClose }: { plan?: SubscriptionPlan; onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PlanValues>({
    resolver: zodResolver(planSchema),
    defaultValues: plan
      ? {
          name: plan.name,
          description: plan.description || '',
          monthlyPrice: plan.monthlyPrice,
          billingPeriodDays: plan.billingPeriodDays,
          gracePeriodDays: plan.gracePeriodDays,
        }
      : {
          name: '',
          description: '',
          monthlyPrice: 0,
          billingPeriodDays: 30,
          gracePeriodDays: 7,
        },
  });
  const save = useMutation({
    mutationFn: (values: PlanValues) =>
      adminApi.savePlan({
        id: plan?.id,
        ...values,
        description: values.description || null,
        active: plan?.active ?? true,
      }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: adminKeys.plans }),
        client.invalidateQueries({ queryKey: adminKeys.subscriptions }),
      ]);
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog
      title={t(plan ? 'admin.editPlan' : 'admin.newPlan')}
      onClose={save.isPending ? () => undefined : onClose}
      wide
    >
      <form className="form-stack" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <div className="form-grid">
          <Field
            id="plan-name"
            label={t('common.name')}
            error={errors.name && t('validation.required')}
          >
            <input id="plan-name" autoFocus disabled={save.isPending} {...register('name')} />
          </Field>
          <Field
            id="plan-price"
            label={t('admin.monthlyPrice')}
            error={errors.monthlyPrice && t('validation.required')}
          >
            <input
              id="plan-price"
              type="number"
              min="0"
              step="1"
              disabled={save.isPending}
              {...register('monthlyPrice', { valueAsNumber: true })}
            />
          </Field>
          <Field
            id="billing-days"
            label={t('admin.billingPeriodDays')}
            error={errors.billingPeriodDays && t('validation.required')}
          >
            <input
              id="billing-days"
              type="number"
              min="1"
              max="366"
              disabled={save.isPending}
              {...register('billingPeriodDays', { valueAsNumber: true })}
            />
          </Field>
          <Field
            id="grace-days"
            label={t('admin.gracePeriodDays')}
            error={errors.gracePeriodDays && t('validation.required')}
          >
            <input
              id="grace-days"
              type="number"
              min="0"
              max="90"
              disabled={save.isPending}
              {...register('gracePeriodDays', { valueAsNumber: true })}
            />
          </Field>
        </div>
        <Field id="plan-description" label={t('products.descriptionField')}>
          <textarea
            id="plan-description"
            rows={3}
            disabled={save.isPending}
            {...register('description')}
          />
        </Field>
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

export function PlansSection() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<SubscriptionPlan | 'new' | null>(null);
  const [changing, setChanging] = useState<SubscriptionPlan | null>(null);
  const plans = useQuery({
    queryKey: [...adminKeys.plans, page],
    queryFn: () => adminApi.plans(page),
  });
  const changeStatus = useMutation({
    mutationFn: (plan: SubscriptionPlan) =>
      adminApi.savePlan({
        id: plan.id,
        name: plan.name,
        description: plan.description,
        monthlyPrice: plan.monthlyPrice,
        billingPeriodDays: plan.billingPeriodDays,
        gracePeriodDays: plan.gracePeriodDays,
        active: !plan.active,
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.plans });
      toast(t('common.saved'));
      setChanging(null);
    },
  });
  return (
    <section>
      <div className="section-toolbar">
        <div>
          <h2>{t('admin.plans')}</h2>
          <p>{t('admin.plansDescription')}</p>
        </div>
        <Button onClick={() => setEditing('new')}>
          <Plus size={17} />
          {t('admin.newPlan')}
        </Button>
      </div>
      <div className="panel">
        {plans.isPending ? (
          <Loading />
        ) : plans.isError ? (
          <ErrorState error={plans.error} retry={() => void plans.refetch()} />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('admin.monthlyPrice')}</th>
                    <th>{t('admin.period')}</th>
                    <th>{t('admin.grace')}</th>
                    <th>{t('common.status')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.data.items.map((plan) => (
                    <tr key={plan.id}>
                      <td>
                        <strong>{plan.name}</strong>
                        {plan.description && (
                          <span className="table-subtext">{plan.description}</span>
                        )}
                      </td>
                      <td>{money(plan.monthlyPrice, i18n.language)}</td>
                      <td>{t('subscription.days', { count: plan.billingPeriodDays })}</td>
                      <td>{t('subscription.days', { count: plan.gracePeriodDays })}</td>
                      <td>
                        <span className="badge">
                          {t(plan.active ? 'common.active' : 'common.inactive')}
                        </span>
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="icon-button"
                            aria-label={`${t('common.edit')} ${plan.name}`}
                            onClick={() => setEditing(plan)}
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            className="icon-button"
                            aria-label={`${t(plan.active ? 'admin.deactivate' : 'admin.activate')} ${plan.name}`}
                            onClick={() => setChanging(plan)}
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
            {!plans.data.items.length && <EmptyState title={t('common.noData')} />}
            <Pagination page={page} total={plans.data.total} onChange={setPage} />
          </>
        )}
      </div>
      {editing && (
        <PlanForm plan={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />
      )}
      {changing && (
        <ConfirmDialog
          title={t(changing.active ? 'admin.deactivatePlan' : 'admin.activatePlan')}
          description={t('admin.planStatusDescription', { name: changing.name })}
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
