import { useTranslation } from 'react-i18next';
import { Dialog } from './Dialog';
import { Button } from './Button';

export function ConfirmDialog({
  title,
  description,
  confirmLabel,
  busy,
  danger = false,
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  busy?: boolean;
  danger?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog title={title} onClose={busy ? () => undefined : onClose}>
      <p>{description}</p>
      <div className="dialog-actions">
        <Button variant="secondary" disabled={busy} onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} busy={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
