import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { replaceSession, useSession } from '../features/auth/session';
import { ErrorState, Loading, EmptyState } from '../shared/components/Feedback';
import { AppShell } from './AppShell';
import { GlobalActivityOverlay } from '../shared/components/ActivityOverlay';

const LoginPage = lazy(() => import('../features/auth/LoginPage'));
const DashboardPage = lazy(() => import('../features/dashboard/DashboardPage'));
const ProductsPage = lazy(() => import('../features/products/ProductsPage'));
const CategoriesPage = lazy(() => import('../features/categories/CategoriesPage'));
const InventoryPage = lazy(() => import('../features/inventory/InventoryPage'));
const SalePage = lazy(() => import('../features/sales/SalePage'));
const ReportsPage = lazy(() => import('../features/reports/ReportsPage'));
const SettingsPage = lazy(() => import('../features/settings/SettingsPage'));
const ReceiptPage = lazy(() => import('../features/documents/ReceiptPage'));
const NotificationsPage = lazy(() => import('../features/notifications/NotificationsPage'));
const AdminPage = lazy(() => import('../features/admin/AdminPage'));
const ChangePasswordPage = lazy(() => import('../features/auth/ChangePasswordPage'));
const SubscriptionPage = lazy(() => import('../features/subscription/SubscriptionPage'));
const SuspendedPage = lazy(() => import('../features/subscription/SuspendedPage'));
import { SubscriptionGate } from '../features/subscription/SubscriptionGate';

function ProtectedRoute() {
  const session = useSession();
  if (session.isPending) return <Loading />;
  if (session.isError)
    return <ErrorState error={session.error} retry={() => void session.refetch()} />;
  if (!session.data) return <Navigate to="/login" replace />;
  if (session.data.mustChangePassword) return <Navigate to="/change-password" replace />;
  return <Outlet />;
}

function PasswordChangeRoute() {
  const session = useSession();
  if (session.isPending) return <Loading />;
  if (session.isError)
    return <ErrorState error={session.error} retry={() => void session.refetch()} />;
  if (!session.data) return <Navigate to="/login" replace />;
  if (!session.data.mustChangePassword)
    return (
      <Navigate to={session.data.role === 'SUPER_ADMIN' ? '/admin/businesses' : '/'} replace />
    );
  return <ChangePasswordPage />;
}

function BusinessRoute() {
  const { data } = useSession();
  return data?.role === 'SUPER_ADMIN' ? <Navigate to="/admin" replace /> : <Outlet />;
}

function AdminRoute() {
  const { data } = useSession();
  return data?.role === 'SUPER_ADMIN' ? <AdminPage /> : <Navigate to="/" replace />;
}

function NotFound() {
  const { t } = useTranslation();
  return <EmptyState title={t('notFound.title')} description={t('notFound.description')} />;
}

export function App() {
  const client = useQueryClient();
  useEffect(() => {
    const expired = () => {
      void replaceSession(client, null);
    };
    window.addEventListener('stockflow:session-expired', expired);
    return () => window.removeEventListener('stockflow:session-expired', expired);
  }, [client]);
  return (
    <>
      <GlobalActivityOverlay />
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/change-password" element={<PasswordChangeRoute />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route path="/admin/:section?" element={<AdminRoute />} />
              <Route element={<BusinessRoute />}>
                <Route path="subscription-suspended" element={<SuspendedPage />} />
                <Route element={<SubscriptionGate />}>
                  <Route index element={<DashboardPage />} />
                  <Route path="products" element={<ProductsPage />} />
                  <Route path="categories" element={<CategoriesPage />} />
                  <Route path="inventory" element={<InventoryPage />} />
                  <Route path="sale" element={<SalePage />} />
                  <Route path="reports" element={<ReportsPage />} />
                  <Route path="settings" element={<SettingsPage />} />
                  <Route path="settings/subscription" element={<SubscriptionPage />} />
                  <Route path="notifications" element={<NotificationsPage />} />
                  <Route path="receipts/:saleId" element={<ReceiptPage />} />
                </Route>
              </Route>
              <Route path="*" element={<NotFound />} />
            </Route>
          </Route>
        </Routes>
      </Suspense>
    </>
  );
}
