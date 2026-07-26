import { useAuth } from '../../auth/authState';
import { AuthenticatedLayout } from './AuthenticatedLayout';

export function AuthenticatedRouteLayout() {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return null;
  }

  return <AuthenticatedLayout profile={state.accessProfile} />;
}
