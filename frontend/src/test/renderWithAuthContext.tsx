import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import type { MyAccountResponse, MyProfileResponse } from '../api/authTypes';
import type { UserAccessProfile } from '../app/config/access';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
  type AuthenticatedAuthState,
} from '../auth/authState';

type AuthOperations = Omit<AuthContextValue, 'state'>;

export function createAuthContextValue(
  state: AuthState,
  overrides: Partial<AuthOperations> = {},
): AuthContextValue {
  return {
    state,
    login: async () => undefined,
    logout: async () => undefined,
    reconcileSession: async () => undefined,
    ...overrides,
  };
}

export function createAuthenticatedAuthState(
  accessProfile: UserAccessProfile,
  { active = true }: { readonly active?: boolean } = {},
): AuthenticatedAuthState {
  const account: MyAccountResponse = {
    id: 1,
    email: 'user@example.com',
    role: accessProfile.role,
    accountStatus: 'ACTIVE',
    emailVerified: true,
    createdAt: '2026-07-26T10:00:00Z',
    updatedAt: '2026-07-26T10:00:00Z',
  };
  const profile: MyProfileResponse =
    accessProfile.role === 'CLIENT'
      ? {
          id: 1,
          role: 'CLIENT',
          firstName: 'Ada',
          lastName: 'Lovelace',
          profileImageUrl: null,
          operationalStatus: 'ATTIVO',
          active,
          specialization: null,
          phoneNumber: null,
          bio: null,
          workplaceName: null,
          city: null,
          instagramUrl: null,
          websiteUrl: null,
          birthDate: '1996-04-15',
          heightCm: 170,
          primaryGoal: 'Benessere',
          gender: 'FEMALE',
          medicalNotes: null,
          injuryNotes: null,
          notes: null,
        }
      : {
          id: 1,
          role: 'PROFESSIONAL',
          firstName: 'Grace',
          lastName: 'Hopper',
          profileImageUrl: null,
          operationalStatus: 'DISPONIBILE',
          active,
          specialization: accessProfile.specialization,
          phoneNumber: null,
          bio: null,
          workplaceName: null,
          city: null,
          instagramUrl: null,
          websiteUrl: null,
          birthDate: null,
          heightCm: null,
          primaryGoal: null,
          gender: null,
          medicalNotes: null,
          injuryNotes: null,
          notes: null,
        };

  return {
    status: 'authenticated',
    operation: null,
    reason: null,
    account,
    profile,
    accessProfile,
  };
}

export function renderWithAuthContext(
  element: ReactElement,
  value: AuthContextValue,
  {
    initialEntries = ['/'],
  }: { readonly initialEntries?: readonly string[] } = {},
) {
  return render(
    <MemoryRouter initialEntries={[...initialEntries]}>
      <AuthContext.Provider value={value}>{element}</AuthContext.Provider>
    </MemoryRouter>,
  );
}
