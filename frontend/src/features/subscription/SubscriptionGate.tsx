import { Navigate, Outlet } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { useSubscription } from './queries';

export function SubscriptionGate() {
  const subscription = useSubscription();
  if (subscription.isPending) return <Loading />;
  if (subscription.isError) {
    if (
      subscription.error instanceof ApiError &&
      ['SUBSCRIPTION_REQUIRED', 'SUBSCRIPTION_SUSPENDED', 'TRIAL_EXPIRED'].includes(
        subscription.error.code,
      )
    )
      return <Navigate to="/subscription-suspended" replace />;
    return <ErrorState error={subscription.error} retry={() => void subscription.refetch()} />;
  }
  if (['SUSPENDED', 'CANCELLED'].includes(subscription.data.status))
    return <Navigate to="/subscription-suspended" replace />;
  return <Outlet />;
}
