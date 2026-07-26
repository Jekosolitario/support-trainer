import { Navigate, Outlet } from 'react-router-dom';

import type { ProfessionalSpecialization } from '../app/config/access';
import { useAuth } from './authState';

interface RequireSpecializationProps {
  readonly specialization: ProfessionalSpecialization;
}

export function RequireSpecialization({
  specialization,
}: RequireSpecializationProps) {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return null;
  }

  if (
    state.accessProfile.role !== 'PROFESSIONAL' ||
    state.accessProfile.specialization !== specialization
  ) {
    return <Navigate replace to="/forbidden" />;
  }

  return <Outlet />;
}
