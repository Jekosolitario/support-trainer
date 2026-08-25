import {
  assertUnreachable,
  type UserAccessProfile,
} from '../../app/config/access';

export interface NavigationItem {
  id: string;
  label: string;
  path: string;
}

const clientNavigation: NavigationItem[] = [
  { id: 'dashboard', label: 'Dashboard', path: '/app/client/dashboard' },
  {
    id: 'professionals',
    label: 'Professionisti',
    path: '/app/client/professionals',
  },
  { id: 'bookings', label: 'Prenotazioni', path: '/app/client/bookings' },
  { id: 'profile', label: 'Profilo', path: '/app/client/profile' },
];

const personalTrainerNavigation: NavigationItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    path: '/app/professional/dashboard',
  },
  { id: 'clients', label: 'Clienti', path: '/app/professional/clients' },
  {
    id: 'availability',
    label: 'Disponibilità',
    path: '/app/professional/availability',
  },
  {
    id: 'bookings',
    label: 'Prenotazioni',
    path: '/app/professional/bookings',
  },
  { id: 'invites', label: 'Inviti', path: '/app/professional/invites' },
  { id: 'profile', label: 'Profilo', path: '/app/professional/profile' },
];

const nutritionistNavigation: NavigationItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    path: '/app/professional/dashboard',
  },
  { id: 'clients', label: 'Clienti', path: '/app/professional/clients' },
  { id: 'invites', label: 'Inviti', path: '/app/professional/invites' },
  { id: 'profile', label: 'Profilo', path: '/app/professional/profile' },
];

export function getNavigationItems(
  profile: UserAccessProfile,
): NavigationItem[] {
  if (profile.role === 'CLIENT') {
    return clientNavigation;
  }

  const specialization = profile.specialization;

  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return personalTrainerNavigation;
    case 'NUTRITIONIST':
      return nutritionistNavigation;
    default:
      return assertUnreachable(specialization);
  }
}
