import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  API_ACTIVITY_EVENT,
  API_CONNECTION_EVENT,
  type ApiActivityDetail,
  type ApiConnectionState,
} from '../api/apiActivity';
import { Brand } from './Brand';

type ActivityMode = 'loading' | 'processing' | 'connecting' | 'offline';
const MINIMUM_OPERATION_TIME_MS = 1_800;

interface ActivityWindowProps {
  message: string;
  description?: string;
  mode?: ActivityMode;
}

export function ActivityWindow({ message, description, mode = 'loading' }: ActivityWindowProps) {
  return (
    <div
      className="activity-overlay"
      role={mode === 'offline' ? 'alert' : 'status'}
      aria-live={mode === 'offline' ? 'assertive' : 'polite'}
      aria-busy={mode !== 'offline'}
    >
      <div className={`activity-window activity-window--${mode}`}>
        <span className="activity-logo" aria-hidden="true">
          <Brand />
        </span>
        <div className="activity-copy">
          <strong>{message}</strong>
          {description && <span>{description}</span>}
        </div>
      </div>
    </div>
  );
}

export function GlobalActivityOverlay() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [showTransfer, setShowTransfer] = useState(false);
  const [online, setOnline] = useState(() => navigator.onLine);
  const [connection, setConnection] = useState<ApiConnectionState>('connected');
  const [checkingConnection, setCheckingConnection] = useState(false);
  const retrying = useRef(false);
  const transferring = useRef(false);
  const transferStartedAt = useRef(0);
  const transferHideTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    const activityChanged = (event: Event) => {
      const active = (event as CustomEvent<ApiActivityDetail>).detail.writes > 0;

      if (active) {
        window.clearTimeout(transferHideTimer.current);
        if (!transferring.current) transferStartedAt.current = Date.now();
        setShowTransfer(true);
      } else if (transferring.current) {
        window.clearTimeout(transferHideTimer.current);
        const elapsed = Date.now() - transferStartedAt.current;
        transferHideTimer.current = window.setTimeout(
          () => {
            setShowTransfer(false);
            transferStartedAt.current = 0;
          },
          Math.max(0, MINIMUM_OPERATION_TIME_MS - elapsed),
        );
      }

      transferring.current = active;
    };
    const connectionChanged = (event: Event) => {
      const state = (event as CustomEvent<ApiConnectionState>).detail;
      setConnection(state);
      if (state === 'connected') setCheckingConnection(false);
    };
    const wentOffline = () => {
      setOnline(false);
      setCheckingConnection(false);
    };
    const wentOnline = () => {
      setOnline(true);
      setCheckingConnection(true);
      void queryClient.refetchQueries({ type: 'active' });
    };

    window.addEventListener(API_ACTIVITY_EVENT, activityChanged);
    window.addEventListener(API_CONNECTION_EVENT, connectionChanged);
    window.addEventListener('offline', wentOffline);
    window.addEventListener('online', wentOnline);
    return () => {
      window.clearTimeout(transferHideTimer.current);
      window.removeEventListener(API_ACTIVITY_EVENT, activityChanged);
      window.removeEventListener(API_CONNECTION_EVENT, connectionChanged);
      window.removeEventListener('offline', wentOffline);
      window.removeEventListener('online', wentOnline);
    };
  }, [queryClient]);

  useEffect(() => {
    if (!online || connection !== 'unavailable') return;
    const retry = () => {
      if (retrying.current) return;
      retrying.current = true;
      setCheckingConnection(true);
      void queryClient.refetchQueries({ type: 'active' }).finally(() => {
        retrying.current = false;
      });
    };
    const interval = window.setInterval(retry, 5_000);
    return () => window.clearInterval(interval);
  }, [connection, online, queryClient]);

  if (!online)
    return (
      <ActivityWindow
        mode="offline"
        message={t('activity.offline')}
        description={t('activity.offlineDescription')}
      />
    );

  if (connection === 'unavailable' || checkingConnection)
    return (
      <ActivityWindow
        mode="connecting"
        message={t('activity.connecting')}
        description={t('activity.connectingDescription')}
      />
    );

  if (showTransfer)
    return (
      <ActivityWindow
        mode="processing"
        message={t('activity.processing')}
        description={t('activity.processingDescription')}
      />
    );

  return null;
}
