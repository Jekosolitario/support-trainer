import { useEffect } from 'react';
import { Navigate, Outlet } from 'react-router-dom';

import { AuthUnavailableBoundary } from './AuthUnavailableBoundary';
import { useAuth } from './authState';
import { useClientOnboarding } from './clientOnboardingState';
import { getDashboardTarget } from './loginRedirect';

/**
 * Local auth gate for the CLIENT onboarding subtree only.
 * Does not guard other public routes.
 */
export function ClientOnboardingAuthGate() {
  const { state } = useAuth();
  const { clearInvite } = useClientOnboarding();

  useEffect(() => {
    if (state.status === 'authenticated') {
      clearInvite();
    }
  }, [state, clearInvite]);

  switch (state.status) {
    case 'initializing':
      return (
        <p aria-live="polite" role="status">
          {state.operation === 'logout'
            ? 'Disconnessione in corso.'
            : 'Verifica della sessione in corso.'}
        </p>
      );
    case 'unavailable':
      return <AuthUnavailableBoundary />;
    case 'authenticated':
      return <Navigate replace to={getDashboardTarget(state.accessProfile)} />;
    case 'unauthenticated':
      return <Outlet />;
  }
}
