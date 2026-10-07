import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowRight, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Brand } from '../../shared/components/Brand';
import { Button } from '../../shared/components/Button';
import { Field } from '../../shared/components/Field';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { loginSession, useSession } from './session';
import { AuthPreferences } from './AuthPreferences';

const schema = z.object({ email: z.email(), password: z.string().min(1).max(64) });
type Values = z.infer<typeof schema>;

export default function LoginPage() {
  const { t } = useTranslation();
  const session = useSession();
  const client = useQueryClient();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>();
  const [visible, setVisible] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  if (session.isPending) return <Loading />;

  if (session.data)
    return <Navigate to={session.data.mustChangePassword ? '/change-password' : '/'} replace />;

  async function submit(values: Values) {
    setError(undefined);
    try {
      const user = await loginSession(client, values);
      navigate(
        user.mustChangePassword
          ? '/change-password'
          : user.role === 'SUPER_ADMIN'
            ? '/admin/businesses'
            : '/',
        { replace: true },
      );
    } catch (failure) {
      setError(failure);
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="login-panel-brand">
          <Brand complete />
        </div>
        <h1>{t('login.title')}</h1>
        <p className="page-description">{t('login.subtitle')}</p>
        <form onSubmit={handleSubmit(submit)} className="form-stack" noValidate>
          <Field id="email" label={t('login.email')} error={errors.email && t('validation.email')}>
            <input
              id="email"
              type="email"
              autoComplete="username"
              autoFocus
              placeholder="nombre@empresa.cl"
              {...register('email')}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
            />
          </Field>
          <Field
            id="password"
            label={t('login.password')}
            error={errors.password && t('validation.required')}
          >
            <div className="password-input">
              <input
                id="password"
                type={visible ? 'text' : 'password'}
                autoComplete="current-password"
                {...register('password')}
                aria-invalid={!!errors.password}
              />
              <button
                type="button"
                className="icon-button"
                onClick={() => setVisible(!visible)}
                aria-label={t(visible ? 'login.hidePassword' : 'login.showPassword')}
              >
                {visible ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </Field>
          {error !== undefined && <ErrorState error={error} />}
          <Button type="submit" busy={isSubmitting} className="button--full">
            {t('login.submit')}
            <ArrowRight size={17} />
          </Button>
        </form>
        <AuthPreferences />
        <p className="login-help">{t('login.help')}</p>
      </section>
      <p className="login-footer">
        <ShieldCheck size={15} />
        {t('login.footer')}
      </p>
    </main>
  );
}
