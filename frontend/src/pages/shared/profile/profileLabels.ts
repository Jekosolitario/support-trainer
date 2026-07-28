import type {
  AccountStatus,
  ClientOperationalStatus,
  Gender,
  ProfessionalOperationalStatus,
  ProfessionalSpecialization,
  UserRole,
} from '../../../api/authTypes';

export function roleLabel(role: UserRole): string {
  switch (role) {
    case 'CLIENT':
      return 'Cliente';
    case 'PROFESSIONAL':
      return 'Professionista';
  }
}

export function specializationLabel(
  specialization: ProfessionalSpecialization,
): string {
  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return 'Personal trainer';
    case 'NUTRITIONIST':
      return 'Nutrizionista';
  }
}

export function accountStatusLabel(status: AccountStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'Attivo';
    case 'PENDING_VERIFICATION':
      return 'In attesa di verifica';
  }
}

export function genderLabel(gender: Gender): string {
  switch (gender) {
    case 'MALE':
      return 'Maschio';
    case 'FEMALE':
      return 'Femmina';
    case 'OTHER':
      return 'Altro';
    case 'NOT_SPECIFIED':
      return 'Non specificato';
  }
}

export function clientOperationalStatusLabel(
  status: ClientOperationalStatus,
): string {
  switch (status) {
    case 'ATTIVO':
      return 'Attivo';
    case 'INFORTUNATO':
      return 'Infortunato';
    case 'PAUSA':
      return 'In pausa';
  }
}

export function professionalOperationalStatusLabel(
  status: ProfessionalOperationalStatus,
): string {
  switch (status) {
    case 'DISPONIBILE':
      return 'Disponibile';
    case 'ASSENTE':
      return 'Assente';
    case 'FERIE':
      return 'In ferie';
    case 'MALATTIA':
      return 'In malattia';
  }
}

export const CLIENT_OPERATIONAL_OPTIONS: readonly ClientOperationalStatus[] = [
  'ATTIVO',
  'INFORTUNATO',
  'PAUSA',
];

export const PROFESSIONAL_OPERATIONAL_OPTIONS: readonly ProfessionalOperationalStatus[] =
  ['DISPONIBILE', 'ASSENTE', 'FERIE', 'MALATTIA'];

export const GENDER_OPTIONS: readonly Gender[] = [
  'MALE',
  'FEMALE',
  'OTHER',
  'NOT_SPECIFIED',
];

export function formatAccountDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }

  return new Intl.DateTimeFormat('it-IT', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(date);
}

export function profileImageFallback(
  firstName: string,
  lastName: string,
): string {
  const first = firstName.trim().charAt(0);
  const last = lastName.trim().charAt(0);
  const initials = `${first}${last}`.toUpperCase();
  return initials.length > 0 ? initials : '?';
}
