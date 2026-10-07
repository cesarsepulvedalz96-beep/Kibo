import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  LayoutDashboard,
  ShoppingBag,
  Package,
  ArrowLeftRight,
  ChartNoAxesCombined,
  Settings2,
  Building2,
  Users,
  BadgeDollarSign,
  CreditCard,
  ReceiptText,
  Bell,
  Check,
  Menu,
  X,
  LogOut,
  ChevronDown,
} from 'lucide-react';
import { Brand } from '../shared/components/Brand';
import { logoutSession, useSession } from '../features/auth/session';
import { ErrorState } from '../shared/components/Feedback';
import { SubscriptionBanner } from '../features/subscription/SubscriptionBanner';
import { useRealtimeEvents } from '../features/realtime/useRealtimeEvents';
import { api } from '../shared/api/client';
import type { Notification, Page } from '../shared/types';

const navigation = [
  { to: '/', label: 'nav.home', icon: LayoutDashboard },
  { to: '/sale', label: 'nav.sale', icon: ShoppingBag },
  { to: '/products', label: 'nav.products', icon: Package },
  { to: '/inventory', label: 'nav.inventory', icon: ArrowLeftRight },
  { to: '/reports', label: 'nav.reports', icon: ChartNoAxesCombined },
  { to: '/settings', label: 'nav.settings', icon: Settings2 },
];

const adminNavigation = [
  { to: '/admin/businesses', label: 'admin.businesses', icon: Building2 },
  { to: '/admin/users', label: 'admin.users', icon: Users },
  { to: '/admin/plans', label: 'admin.plans', icon: BadgeDollarSign },
  { to: '/admin/subscriptions', label: 'admin.subscriptions', icon: CreditCard },
  { to: '/admin/payments', label: 'admin.payments', icon: ReceiptText },
  { to: '/admin/preferences', label: 'admin.preferences', icon: Settings2 },
];

export function AppShell() {
  const { t } = useTranslation();
  const { data: user } = useSession();
  const location = useLocation();
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<unknown>();
  const [loggingOut, setLoggingOut] = useState(false);
  const accountMenuRef = useRef<HTMLDetailsElement>(null);
  const notificationMenuRef = useRef<HTMLDetailsElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  useRealtimeEvents(user);
  const notificationPreview = useQuery({
    queryKey: ['notifications', 'indicator'],
    queryFn: () => api.get<Page<Notification>>('/notifications?size=100'),
    enabled: user?.role === 'BUSINESS_USER',
  });
  const hasUnreadNotifications = notificationPreview.data?.items.some(
    (notification) => !notification.read,
  );
  const readNotification = useMutation({
    mutationFn: (id: string) => api.post(`/notifications/${id}/read`),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  });
  useEffect(() => {
    const closeOutside = (event: PointerEvent) => {
      [accountMenuRef.current, notificationMenuRef.current].forEach((menu) => {
        if (menu?.open && !menu.contains(event.target as Node)) menu.open = false;
      });
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      [accountMenuRef.current, notificationMenuRef.current].forEach((menu) => {
        if (!menu?.open) return;
        menu.open = false;
        menu.querySelector<HTMLElement>('summary')?.focus();
      });
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const sidebar = sidebarRef.current;
    const focusable = () =>
      sidebar?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
    focusable()?.[0]?.focus();
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
      if (event.key !== 'Tab') return;
      const elements = focusable();
      if (!elements?.length) return;
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      }
      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', keyboard);
    return () => {
      document.removeEventListener('keydown', keyboard);
      previous?.focus();
    };
  }, [open]);
  const isAdmin = user?.role === 'SUPER_ADMIN';
  const links = isAdmin ? adminNavigation : navigation;
  const currentLink = links.find((item) =>
    item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to),
  );

  async function logout() {
    if (loggingOut) return;
    if (accountMenuRef.current) accountMenuRef.current.open = false;
    setLoggingOut(true);
    setError(undefined);
    try {
      await logoutSession(client);
    } catch (failure) {
      setError(failure);
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t('nav.skip')}
      </a>
      {open && (
        <button
          className="sidebar-backdrop"
          onClick={() => setOpen(false)}
          aria-label={t('common.close')}
        />
      )}
      <aside
        ref={sidebarRef}
        className={`sidebar ${open ? 'sidebar--open' : ''}`}
        aria-label={t('nav.workspace')}
      >
        <div className="sidebar-brand">
          <Brand complete />
          <button
            className="icon-button mobile-only"
            onClick={() => setOpen(false)}
            aria-label={t('common.close')}
          >
            <X size={20} />
          </button>
        </div>
        <p className="nav-label">{t(isAdmin ? 'nav.admin' : 'nav.workspace')}</p>
        <nav>
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              end={to === '/'}
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) => `nav-link ${isActive ? 'nav-link--active' : ''}`}
            >
              <Icon size={19} strokeWidth={1.6} />
              <span>{t(label)}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="sidebar-footer-mark">KV</span>
          <p>{t('nav.tagline')}</p>
          <span className="version">v0.1.0</span>
        </div>
      </aside>
      <div className="app-main" inert={open}>
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button mobile-only"
              onClick={() => setOpen(true)}
              aria-label={t('nav.menu')}
              aria-expanded={open}
            >
              <Menu size={21} />
            </button>
            <span className="breadcrumb">
              {t(
                currentLink
                  ? currentLink.label
                  : location.pathname === '/notifications'
                    ? 'notifications.title'
                    : 'receipt.title',
              )}
            </span>
          </div>
          <div className="topbar-right">
            <span className="business-name">
              <Building2 size={15} />
              {user?.businessName}
            </span>
            {!isAdmin && (
              <details className="notification-menu" ref={notificationMenuRef}>
                <summary
                  className="icon-button notification-button"
                  aria-label={t(
                    hasUnreadNotifications ? 'notifications.unread' : 'notifications.title',
                  )}
                  onClick={() => {
                    if (accountMenuRef.current) accountMenuRef.current.open = false;
                  }}
                >
                  <Bell size={19} />
                  {hasUnreadNotifications && (
                    <span className="notification-dot" aria-hidden="true" />
                  )}
                </summary>
                <div className="notification-dropdown">
                  <div className="notification-dropdown-header">
                    <strong>{t('notifications.title')}</strong>
                    {hasUnreadNotifications && <span className="count-badge" />}
                  </div>
                  {notificationPreview.isPending ? (
                    <p className="notification-dropdown-state">{t('common.loading')}</p>
                  ) : notificationPreview.isError ? (
                    <button
                      className="notification-dropdown-state notification-retry"
                      onClick={() => void notificationPreview.refetch()}
                    >
                      {t('common.retry')}
                    </button>
                  ) : notificationPreview.data.items.length ? (
                    <ul className="notification-preview-list">
                      {notificationPreview.data.items.slice(0, 5).map((notification) => (
                        <li
                          key={notification.id}
                          className={notification.read ? 'notification-preview--read' : ''}
                        >
                          <span className="notification-preview-icon">
                            <Bell size={15} />
                          </span>
                          <strong>
                            {t('notifications.message', {
                              name: notification.productName,
                              stock: notification.stock,
                            })}
                          </strong>
                          {!notification.read && (
                            <button
                              className="notification-preview-read"
                              aria-label={`${t('notifications.read')} ${notification.productName}`}
                              disabled={readNotification.isPending}
                              onClick={() => readNotification.mutate(notification.id)}
                            >
                              <Check size={15} />
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="notification-dropdown-state">{t('notifications.empty')}</p>
                  )}
                </div>
              </details>
            )}
            <details className="account-menu" ref={accountMenuRef}>
              <summary
                onClick={() => {
                  if (notificationMenuRef.current) notificationMenuRef.current.open = false;
                }}
              >
                <span className="avatar">{user?.displayName.slice(0, 2).toUpperCase()}</span>
                <span className="account-name">{user?.displayName}</span>
                <ChevronDown size={14} />
              </summary>
              <div className="account-dropdown">
                <p>{user?.email}</p>
                <button onClick={() => void logout()} disabled={loggingOut} aria-busy={loggingOut}>
                  <LogOut size={16} />
                  {t('common.logout')}
                </button>
              </div>
            </details>
          </div>
        </header>
        <main id="main-content" className="content" tabIndex={-1}>
          {error !== undefined && <ErrorState error={error} />}
          <SubscriptionBanner />
          <Outlet />
        </main>
      </div>
    </div>
  );
}
