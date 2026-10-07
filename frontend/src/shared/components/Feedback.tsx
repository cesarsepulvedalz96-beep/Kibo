import { PackageOpen, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { Button } from './Button';
import { ActivityWindow } from './ActivityOverlay';

export function Loading() {
  const { t } = useTranslation();
  return <ActivityWindow message={t('common.loading')} />;
}

export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const { t } = useTranslation();
  const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR';
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={20} />
      <span>{t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</span>
      {retry && (
        <Button variant="secondary" onClick={retry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="empty-state">
      <PackageOpen size={30} strokeWidth={1.3} />
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  );
}
