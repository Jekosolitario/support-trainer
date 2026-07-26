import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { AuthUnavailableBoundary } from './AuthUnavailableBoundary';
import { useAuth } from './authState';

export interface LoginRedirectState {
  readonly from: {
    readonly pathname: string;
    readonly search: string;
    readonly hash: string;
  };
}

export function RequireAuth() {
  const { state } = useAuth();
  const location = useLocation();

  switch (state.status) {
    case 'initializing':
      return (
        <p aria-live="polite" role="status">
          Verifica della sessione in corso.
        </p>
      );
    case 'unavailable':
      return <AuthUnavailableBoundary />;
    case 'unauthenticated': {
      const redirectState: LoginRedirectState = {
        from: {
          pathname: location.pathname,
          search: location.search,
          hash: location.hash,
        },
      };

      return <Navigate replace state={redirectState} to="/login" />;
    }
    case 'authenticated':
      return <Outlet />;
  }
}
