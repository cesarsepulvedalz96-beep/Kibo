import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { CreditCard } from 'lucide-react';
import { api } from '../../shared/api/client';
import type { Settings } from '../../shared/types';
import { useSettings } from './queries';
import { useSession } from '../auth/session';
import { useToast } from '../../app/toast';
import { PageHeader } from '../../shared/components/PageHeader';
import { Field } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { AppearancePreferences } from './AppearancePreferences';

const schema = z.object({
  operatingMode: z.enum(['POS', 'INVENTORY_ONLY']),
  scannerEnabled: z.boolean(),
  stockAlerts: z.boolean(),
  taxEnabled: z.boolean(),
  taxRate: z.number().min(0).max(1),
  generateReceipt: z.boolean(),
  timezone: z.string().min(1).max(80),
});

function BusinessSettingsForm({ settings }: { settings: Settings }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const { data: user } = useSession();
  const manager = user?.role === 'BUSINESS_USER';
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Settings>({ resolver: zodResolver(schema), defaultValues: settings });
  const save = useMutation({
    mutationFn: (values: Settings) => api.put<Settings>('/settings', values),
    onSuccess: async (data) => {
      client.setQueryData(['settings'], data);
      await client.invalidateQueries({ queryKey: ['reports'] });
      toast(t('common.saved'));
    },
  });
  return (
    <form onSubmit={handleSubmit((values) => save.mutate(values))} noValidate>
      {!manager && <div className="inline-note">{t('settings.readOnly')}</div>}
      <fieldset className="settings-fieldset" disabled={!manager || save.isPending}>
        <section className="settings-section">
          <div>
            <h2>{t('settings.general')}</h2>
          </div>
          <div className="settings-fields">
            <Field id="operating-mode" label={t('settings.mode')}>
              <select id="operating-mode" {...register('operatingMode')}>
                <option value="POS">{t('settings.POS')}</option>
                <option value="INVENTORY_ONLY">{t('settings.INVENTORY_ONLY')}</option>
              </select>
            </Field>
            <Field
              id="timezone"
              label={t('settings.timezone')}
              error={errors.timezone && t('validation.required')}
            >
              <select id="timezone" {...register('timezone')}>
                {Array.from(
                  new Set([
                    settings.timezone,
                    'America/Santiago',
                    'America/Punta_Arenas',
                    'Pacific/Easter',
                    'America/Lima',
                    'America/Bogota',
                    'America/Mexico_City',
                    'UTC',
                    'Asia/Shanghai',
                  ]),
                ).map((zone) => (
                  <option key={zone}>{zone}</option>
                ))}
              </select>
            </Field>
          </div>
        </section>
        <section className="settings-section">
          <div>
            <h2>{t('settings.inventory')}</h2>
          </div>
          <div className="settings-fields">
            <label className="toggle-row">
              <div>
                <strong>{t('settings.scanner')}</strong>
                <span>{t('settings.scannerHint')}</span>
              </div>
              <input type="checkbox" role="switch" {...register('scannerEnabled')} />
            </label>
            <label className="toggle-row">
              <div>
                <strong>{t('settings.alerts')}</strong>
                <span>{t('settings.alertsHint')}</span>
              </div>
              <input type="checkbox" role="switch" {...register('stockAlerts')} />
            </label>
          </div>
        </section>
        <section className="settings-section">
          <div>
            <h2>{t('settings.sales')}</h2>
          </div>
          <div className="settings-fields">
            <label className="toggle-row">
              <strong>{t('settings.tax')}</strong>
              <input type="checkbox" role="switch" {...register('taxEnabled')} />
            </label>
            <Field
              id="tax-rate"
              label={t('settings.taxRate')}
              error={errors.taxRate && t('validation.required')}
            >
              <Controller
                name="taxRate"
                control={control}
                render={({ field }) => (
                  <input
                    id="tax-rate"
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={Math.round(field.value * 10000) / 100}
                    onBlur={field.onBlur}
                    ref={field.ref}
                    onChange={(event) => field.onChange(Number(event.target.value) / 100)}
                  />
                )}
              />
            </Field>
            <p className="field-hint">{t('products.priceHint')}</p>
          </div>
        </section>
        <section className="settings-section">
          <div>
            <h2>{t('settings.documents')}</h2>
          </div>
          <div className="settings-fields">
            <label className="toggle-row">
              <div>
                <strong>{t('settings.receipt')}</strong>
                <span>{t('settings.receiptHint')}</span>
              </div>
              <input type="checkbox" role="switch" {...register('generateReceipt')} />
            </label>
            <p className="field-hint">{t('receipt.notFiscal')}</p>
          </div>
        </section>
      </fieldset>
      {save.isError && <ErrorState error={save.error} />}
      {manager && (
        <div className="settings-actions">
          <Button type="submit" busy={save.isPending}>
            {t('common.save')}
          </Button>
        </div>
      )}
    </form>
  );
}

export default function SettingsPage() {
  const { t } = useTranslation();
  const settings = useSettings();
  return (
    <>
      <PageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="panel settings-panel">
        <section className="settings-section">
          <div>
            <h2>{t('subscription.title')}</h2>
            <p>{t('subscription.description')}</p>
          </div>
          <div className="settings-fields">
            <Link className="settings-link" to="/settings/subscription">
              <CreditCard size={19} strokeWidth={1.5} />
              <span>
                <strong>{t('subscription.view')}</strong>
                <small>{t('subscription.nextBilling')}</small>
              </span>
            </Link>
          </div>
        </section>
        <AppearancePreferences />
        {settings.isPending ? (
          <Loading />
        ) : settings.isError ? (
          <ErrorState error={settings.error} retry={() => void settings.refetch()} />
        ) : (
          <BusinessSettingsForm settings={settings.data} />
        )}
      </div>
    </>
  );
}
