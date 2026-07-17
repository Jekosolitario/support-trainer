import { useRoutes, type RouteObject } from 'react-router-dom';

import {
  CLIENT_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
} from '../config/access';
import { AuthenticatedLayout } from '../../layouts/authenticated/AuthenticatedLayout';
import { ErrorLayout } from '../../layouts/error/ErrorLayout';
import { PublicLayout } from '../../layouts/public/PublicLayout';
import {
  ClientBookingDetailPage,
  ClientBookingsPage,
  ClientProfessionalAvailabilityPage,
  ClientProfessionalDetailPage,
  ClientProfessionalsPage,
} from '../../pages/client/ClientPages';
import { RolePreviewPage } from '../../pages/dev/RolePreviewPage';
import {
  ProfessionalAvailabilityPage,
  ProfessionalBookingDetailPage,
  ProfessionalBookingsPage,
  ProfessionalClientDetailPage,
  ProfessionalClientsPage,
  ProfessionalInvitesPage,
} from '../../pages/professional/ProfessionalPages';
import { HomePage } from '../../pages/public/HomePage';
import {
  LoginPage,
  RegisterClientPage,
  RegisterProfessionalPage,
  ValidateInvitePage,
  VerifyEmailPage,
} from '../../pages/public/PublicPages';
import { DashboardPage } from '../../pages/shared/DashboardPage';
import { ForbiddenPage, NotFoundPage } from '../../pages/shared/ErrorPages';
import { ProfilePage } from '../../pages/shared/ProfilePage';

function createAppRoutes(isDevelopment: boolean): RouteObject[] {
  const routes: RouteObject[] = [
    {
      element: <PublicLayout />,
      children: [
        { path: '/', element: <HomePage /> },
        { path: '/login', element: <LoginPage /> },
        {
          path: '/register/professional',
          element: <RegisterProfessionalPage />,
        },
        { path: '/invite/validate', element: <ValidateInvitePage /> },
        { path: '/register/client', element: <RegisterClientPage /> },
        { path: '/verify-email', element: <VerifyEmailPage /> },
      ],
    },
    {
      element: <AuthenticatedLayout profile={CLIENT_ACCESS_PROFILE} />,
      children: [
        {
          path: '/app/client/dashboard',
          element: <DashboardPage profile={CLIENT_ACCESS_PROFILE} />,
        },
        {
          path: '/app/client/profile',
          element: <ProfilePage area="cliente" />,
        },
        {
          path: '/app/client/professionals',
          element: <ClientProfessionalsPage />,
        },
        {
          path: '/app/client/professionals/:professionalId',
          element: <ClientProfessionalDetailPage />,
        },
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
        {
          path: '/app/client/bookings',
          element: <ClientBookingsPage />,
        },
        {
          path: '/app/client/bookings/:bookingRequestId',
          element: <ClientBookingDetailPage />,
        },
      ],
    },
    {
      element: (
        <AuthenticatedLayout profile={PERSONAL_TRAINER_ACCESS_PROFILE} />
      ),
      children: [
        {
          path: '/app/professional/dashboard',
          element: <DashboardPage profile={PERSONAL_TRAINER_ACCESS_PROFILE} />,
        },
        {
          path: '/app/professional/profile',
          element: <ProfilePage area="professionista" />,
        },
        {
          path: '/app/professional/clients',
          element: <ProfessionalClientsPage />,
        },
        {
          path: '/app/professional/clients/:clientId',
          element: <ProfessionalClientDetailPage />,
        },
        {
          path: '/app/professional/invites',
          element: <ProfessionalInvitesPage />,
        },
        {
          path: '/app/professional/availability',
          element: <ProfessionalAvailabilityPage />,
        },
        {
          path: '/app/professional/bookings',
          element: <ProfessionalBookingsPage />,
        },
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
    },
  ];

  if (isDevelopment) {
    routes.push({ path: '/dev/role-preview', element: <RolePreviewPage /> });
  }

  routes.push({
    element: <ErrorLayout />,
    children: [
      { path: '/forbidden', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  });

  return routes;
}

interface AppRoutesProps {
  isDevelopment?: boolean;
}

export function AppRoutes({
  isDevelopment = import.meta.env.DEV,
}: AppRoutesProps) {
  return useRoutes(createAppRoutes(isDevelopment));
}
