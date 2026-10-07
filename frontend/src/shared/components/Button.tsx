import type { ButtonHTMLAttributes } from 'react';
import { LoaderCircle } from 'lucide-react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  busy?: boolean;
};

export function Button({
  variant = 'primary',
  busy = false,
  children,
  className = '',
  disabled,
  ...props
}: Props) {
  return (
    <button
      type="button"
      {...props}
      disabled={disabled || busy}
      className={`button button--${variant} ${className}`}
      aria-busy={busy}
    >
      {busy && <LoaderCircle size={16} className="spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}
