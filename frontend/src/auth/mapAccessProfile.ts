import type { MyAccountResponse, MyProfileResponse } from '../api/authTypes';
import type { UserAccessProfile } from '../app/config/access';

export type AuthConsistencyCode =
  | 'INCOMPLETE_DATA'
  | 'IDENTITY_MISMATCH'
  | 'ROLE_MISMATCH'
  | 'INVALID_CLIENT_SPECIALIZATION'
  | 'INVALID_PROFESSIONAL_SPECIALIZATION';

export class AuthConsistencyError extends Error {
  readonly code: AuthConsistencyCode;

  constructor(code: AuthConsistencyCode) {
    super(`Authenticated account/profile data is inconsistent: ${code}`);
    this.name = 'AuthConsistencyError';
    this.code = code;
  }
}

function isValidApplicationId(value: unknown): value is number {
  return (
    typeof value === 'number' &&
    Number.isFinite(value) &&
    Number.isInteger(value) &&
    value > 0
  );
}

function isUserRole(value: unknown): value is 'CLIENT' | 'PROFESSIONAL' {
  return value === 'CLIENT' || value === 'PROFESSIONAL';
}

export function mapAuthenticatedUserToAccessProfile(
  account: MyAccountResponse,
  profile: MyProfileResponse,
): UserAccessProfile {
  if (!isValidApplicationId(account.id) || !isValidApplicationId(profile.id)) {
    throw new AuthConsistencyError('INCOMPLETE_DATA');
  }

  if (account.id !== profile.id) {
    throw new AuthConsistencyError('IDENTITY_MISMATCH');
  }

  if (!isUserRole(account.role) || !isUserRole(profile.role)) {
    throw new AuthConsistencyError('INCOMPLETE_DATA');
  }

  if (account.role !== profile.role) {
    throw new AuthConsistencyError('ROLE_MISMATCH');
  }

  if (profile.role === 'CLIENT') {
    if (profile.specialization !== null) {
      throw new AuthConsistencyError('INVALID_CLIENT_SPECIALIZATION');
    }

    return {
      role: 'CLIENT',
      specialization: null,
    };
  }

  switch (profile.specialization) {
    case 'PERSONAL_TRAINER':
    case 'NUTRITIONIST':
      return {
        role: 'PROFESSIONAL',
        specialization: profile.specialization,
      };
    default:
      throw new AuthConsistencyError('INVALID_PROFESSIONAL_SPECIALIZATION');
  }
}
