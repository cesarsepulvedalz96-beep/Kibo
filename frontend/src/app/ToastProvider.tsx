import { useEffect, useState, type ReactNode } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { ToastContext } from './toast';

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState('');
  useEffect(() => {
    if (!message) return;
    const timer = window.setTimeout(() => setMessage(''), 4500);
    return () => window.clearTimeout(timer);
  }, [message]);
  return (
    <ToastContext value={setMessage}>
      {children}
      <div className="toast-region" aria-live="polite" role="status">
        {message && (
          <div className="toast">
            <CheckCircle2 size={18} />
            {message}
          </div>
        )}
      </div>
    </ToastContext>
  );
}
