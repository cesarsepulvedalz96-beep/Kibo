import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import { Brand } from '../../shared/components/Brand';
import { Button } from '../../shared/components/Button';
import { Field } from '../../shared/components/Field';
import { ErrorState } from '../../shared/components/Feedback';
import { changePasswordSession, useSession } from './session';
import { AuthPreferences } from './AuthPreferences';

const passwordSchema = z
  .object({
    newPassword: z.string().min(12).max(64),
    confirmPassword: z.string().min(1).max(64),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
  })
  .refine((values) => new TextEncoder().encode(values.newPassword).length <= 72, {
    path: ['newPassword'],
  });
type Values = z.infer<typeof passwordSchema>;

export default function ChangePasswordPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const session = useSession();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(passwordSchema) });

  async function submit(values: Values) {
    setError(undefined);
    try {
      const user = await changePasswordSession(client, values.newPassword);
      navigate(user.role === 'SUPER_ADMIN' ? '/admin/businesses' : '/', { replace: true });
    } catch (failure) {
      setError(failure);
    }
  }

  if (!session.data) return <Navigate to="/login" replace />;
  if (!session.data.mustChangePassword)
    return (
      <Navigate to={session.data.role === 'SUPER_ADMIN' ? '/admin/businesses' : '/'} replace />
    );

  return (
    <main className="login-page">
      <header className="login-header">
        <Brand complete />
        <AuthPreferences />
      </header>
      <section className="login-panel">
        <KeyRound size={28} strokeWidth={1.5} />
        <p className="eyebrow">{t('passwordChange.eyebrow')}</p>
        <h1>{t('passwordChange.title')}</h1>
        <p className="page-description">{t('passwordChange.description')}</p>
        <form className="form-stack" onSubmit={handleSubmit(submit)} noValidate>
          <Field
            id="new-password"
            label={t('passwordChange.new')}
            hint={t('validation.password')}
            error={errors.newPassword && t('validation.password')}
          >
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              autoFocus
              {...register('newPassword')}
            />
          </Field>
          <Field
            id="confirm-password"
            label={t('passwordChange.confirm')}
            error={errors.confirmPassword && t('passwordChange.mismatch')}
          >
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              {...register('confirmPassword')}
            />
          </Field>
          {error !== undefined && <ErrorState error={error} />}
          <Button type="submit" busy={isSubmitting} className="button--full">
            {t('passwordChange.submit')}
          </Button>
        </form>
      </section>
    </main>
  );
}
