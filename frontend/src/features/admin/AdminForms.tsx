import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { api } from '../../shared/api/client';
import type { Business } from '../../shared/types';
import { Dialog } from '../../shared/components/Dialog';
import { Field } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { ErrorState } from '../../shared/components/Feedback';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';

const businessSchema = z.object({
  name: z.string().trim().min(1).max(160),
  taxId: z.string().max(40),
  planId: z.string(),
  trialDays: z.number().int().min(0).max(3650),
});

export function BusinessForm({ business, onClose }: { business?: Business; onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const plans = useQuery({
    queryKey: [...adminKeys.plans, 'lookup', 'active'],
    queryFn: () => adminApi.plans(0, true, '', 100),
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.infer<typeof businessSchema>>({
    resolver: zodResolver(businessSchema),
    defaultValues: business
      ? {
          name: business.name,
          taxId: business.taxId || '',
          planId: business.planId || '',
          trialDays: 0,
        }
      : { name: '', taxId: '', planId: '', trialDays: 0 },
  });
  const save = useMutation({
    mutationFn: (values: z.infer<typeof businessSchema>) =>
      business
        ? api.put(`/admin/businesses/${business.id}`, {
            name: values.name,
            taxId: values.taxId,
            active: business.active,
            planId: values.planId || undefined,
            trialDays: values.planId && values.trialDays > 0 ? values.trialDays : undefined,
          })
        : api.post('/admin/businesses', {
            name: values.name,
            taxId: values.taxId,
            active: true,
            planId: values.planId || undefined,
            trialDays: values.planId && values.trialDays > 0 ? values.trialDays : undefined,
          }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: adminKeys.businesses }),
        client.invalidateQueries({ queryKey: adminKeys.subscriptions }),
      ]);
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog title={t(business ? 'admin.editBusiness' : 'admin.newBusiness')} onClose={onClose}>
      <form className="form-stack" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <Field
          id="business-name"
          label={t('common.name')}
          error={errors.name && t('validation.required')}
        >
          <input id="business-name" autoFocus {...register('name')} />
        </Field>
        <Field id="tax-id" label={t('admin.taxId')}>
          <input id="tax-id" {...register('taxId')} />
        </Field>
        <div className="form-grid">
          <Field id="business-plan" label={t('admin.plan')} hint={t('common.optional')}>
            <select id="business-plan" disabled={plans.isPending} {...register('planId')}>
              <option value="">{t('admin.unassigned')}</option>
              {business?.planId &&
                !plans.data?.items.some((plan) => plan.id === business.planId) && (
                  <option value={business.planId}>{business.planName}</option>
                )}
              {plans.data?.items.map((plan) => (
                <option key={plan.id} value={plan.id}>
                  {plan.name}
                </option>
              ))}
            </select>
          </Field>
          {(!business || !business.planId) && (
            <Field
              id="business-trial-days"
              label={t('admin.trialDays')}
              hint={t('admin.trialDaysHint')}
              error={errors.trialDays && t('validation.required')}
            >
              <input
                id="business-trial-days"
                type="number"
                min="0"
                max="3650"
                {...register('trialDays', { valueAsNumber: true })}
              />
            </Field>
          )}
        </div>
        {plans.isError && <ErrorState error={plans.error} />}
        {save.isError && <ErrorState error={save.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onClose}>
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
