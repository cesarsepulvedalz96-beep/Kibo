import { Navigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../shared/components/PageHeader';
import { BusinessesSection } from './BusinessesSection';
import { UsersSection } from './UsersSection';
import { PlansSection } from './PlansSection';
import { SubscriptionsSection } from './SubscriptionsSection';
import { SubscriptionPaymentsSection } from './SubscriptionPaymentsSection';
import { AdminPreferencesSection } from './AdminPreferencesSection';

const sections = [
  'businesses',
  'users',
  'plans',
  'subscriptions',
  'payments',
  'preferences',
] as const;
type Section = (typeof sections)[number];

export default function AdminPage() {
  const { t } = useTranslation();
  const { section } = useParams();
  if (!section) return <Navigate to="/admin/businesses" replace />;
  if (!sections.includes(section as Section)) return <Navigate to="/admin/businesses" replace />;
  const selected = section as Section;
  return (
    <>
      <PageHeader title={t('admin.title')} description={t('admin.description')} />
      {selected === 'businesses' && <BusinessesSection />}
      {selected === 'users' && <UsersSection />}
      {selected === 'plans' && <PlansSection />}
      {selected === 'subscriptions' && <SubscriptionsSection />}
      {selected === 'payments' && <SubscriptionPaymentsSection />}
      {selected === 'preferences' && <AdminPreferencesSection />}
    </>
  );
}
