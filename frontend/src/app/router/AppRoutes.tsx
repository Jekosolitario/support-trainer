import { useRoutes, type RouteObject } from 'react-router-dom';

import { ClientOnboardingProviderLayout } from '../../auth/ClientOnboardingProviderLayout';
import { RequireAuth } from '../../auth/RequireAuth';
import { RequireRole } from '../../auth/RequireRole';
import { RequireSpecialization } from '../../auth/RequireSpecialization';
import { useAuth } from '../../auth/authState';
import { AuthenticatedRouteLayout } from '../../layouts/authenticated/AuthenticatedRouteLayout';
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
} from '../../pages/professional/ProfessionalPages';
import { ProfessionalInvitesPage } from '../../pages/professional/ProfessionalInvitesPage';
import { ForgotPasswordPage } from '../../pages/public/ForgotPasswordPage';
import { HomePage } from '../../pages/public/HomePage';
import { LoginPage } from '../../pages/public/LoginPage';
import { RegisterClientPage } from '../../pages/public/PublicPages';
import { RegisterProfessionalPage } from '../../pages/public/RegisterProfessionalPage';
import { ResetPasswordPage } from '../../pages/public/ResetPasswordPage';
import { ValidateInvitePage } from '../../pages/public/ValidateInvitePage';
import { VerifyEmailPage } from '../../pages/public/VerifyEmailPage';
import { DashboardPage } from '../../pages/shared/DashboardPage';
import { ForbiddenPage, NotFoundPage } from '../../pages/shared/ErrorPages';
import { ProfilePage } from '../../pages/shared/ProfilePage';

function AuthenticatedDashboardRoute() {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return null;
  }

  return <DashboardPage profile={state.accessProfile} />;
}

function createAppRoutes(isDevelopment: boolean): RouteObject[] {
  const routes: RouteObject[] = [
    {
      element: <PublicLayout />,
      children: [
        { path: '/', element: <HomePage /> },
        { path: '/login', element: <LoginPage /> },
        { path: '/forgot-password', element: <ForgotPasswordPage /> },
        { path: '/reset-password', element: <ResetPasswordPage /> },
        {
          path: '/register/professional',
          element: <RegisterProfessionalPage />,
        },
        {
          element: <ClientOnboardingProviderLayout />,
          children: [
            { path: '/invite/validate', element: <ValidateInvitePage /> },
            { path: '/register/client', element: <RegisterClientPage /> },
          ],
        },
        { path: '/verify-email', element: <VerifyEmailPage /> },
      ],
    },
    {
      element: <RequireAuth />,
      children: [
        {
          element: <RequireRole role="CLIENT" />,
          children: [
            {
              element: <AuthenticatedRouteLayout />,
              children: [
                {
                  path: '/app/client/dashboard',
                  element: <AuthenticatedDashboardRoute />,
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
          ],
        },
        {
          element: <RequireRole role="PROFESSIONAL" />,
          children: [
            {
              element: <AuthenticatedRouteLayout />,
              children: [
                {
                  path: '/app/professional/dashboard',
                  element: <AuthenticatedDashboardRoute />,
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
                  element: (
                    <RequireSpecialization specialization="PERSONAL_TRAINER" />
                  ),
                  children: [
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
              ],
            },
          ],
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
