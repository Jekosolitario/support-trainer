export type UserRole = 'CLIENT' | 'PROFESSIONAL';

export type ProfessionalSpecialization = 'PERSONAL_TRAINER' | 'NUTRITIONIST';

export type UserAccessProfile =
  | {
      role: 'CLIENT';
      specialization: null;
    }
  | {
      role: 'PROFESSIONAL';
      specialization: ProfessionalSpecialization;
    };

export const CLIENT_ACCESS_PROFILE: UserAccessProfile = {
  role: 'CLIENT',
  specialization: null,
};

export const PERSONAL_TRAINER_ACCESS_PROFILE: UserAccessProfile = {
  role: 'PROFESSIONAL',
  specialization: 'PERSONAL_TRAINER',
};

export const NUTRITIONIST_ACCESS_PROFILE: UserAccessProfile = {
  role: 'PROFESSIONAL',
  specialization: 'NUTRITIONIST',
};

export function getAccessProfileLabel(profile: UserAccessProfile) {
  if (profile.role === 'CLIENT') {
    return 'Area cliente';
  }

  const specialization = profile.specialization;

  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return 'Area personal trainer';
    case 'NUTRITIONIST':
      return 'Area nutrizionista';
    default:
      return assertUnreachable(specialization);
  }
}

export function assertUnreachable(value: never): never {
  throw new Error(`Valore di accesso non gestito: ${String(value)}`);
}
