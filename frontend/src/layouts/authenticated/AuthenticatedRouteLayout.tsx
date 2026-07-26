import { useAuth } from '../../auth/authState';
import { LogoutButton } from '../../components/navigation/LogoutButton';
import { AuthenticatedLayout } from './AuthenticatedLayout';

export function AuthenticatedRouteLayout() {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return null;
  }

  return (
    <AuthenticatedLayout
      profile={state.accessProfile}
      headerActions={<LogoutButton />}
    />
  );
}
