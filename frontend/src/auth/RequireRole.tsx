import { Navigate, Outlet } from 'react-router-dom';

import type { UserRole } from '../app/config/access';
import { useAuth } from './authState';

interface RequireRoleProps {
  readonly role: UserRole;
}

export function RequireRole({ role }: RequireRoleProps) {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return null;
  }

  if (state.accessProfile.role !== role) {
    return <Navigate replace to="/forbidden" />;
  }

  return <Outlet />;
}
