import { describe, expect, it } from 'vitest';

import type {
  MyAccountResponse,
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
  MyProfileResponse,
} from '../api/authTypes';
import {
  AuthConsistencyError,
  mapAuthenticatedUserToAccessProfile,
} from './mapAccessProfile';

function account(
  overrides: Partial<MyAccountResponse> = {},
): MyAccountResponse {
  return {
    id: 1,
    email: 'user@example.com',
    role: 'CLIENT',
    accountStatus: 'ACTIVE',
    emailVerified: true,
    createdAt: '2026-07-26T10:00:00Z',
    updatedAt: '2026-07-26T10:00:00Z',
    ...overrides,
  };
}

function clientProfile(
  overrides: Partial<MyClientProfileResponse> = {},
): MyClientProfileResponse {
  return {
    id: 1,
    role: 'CLIENT',
    firstName: 'Ada',
    lastName: 'Lovelace',
    profileImageUrl: null,
    operationalStatus: 'ATTIVO',
    active: true,
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
    ...overrides,
  };
}

function professionalProfile(
  overrides: Partial<MyProfessionalProfileResponse> = {},
): MyProfessionalProfileResponse {
  return {
    id: 1,
    role: 'PROFESSIONAL',
    firstName: 'Grace',
    lastName: 'Hopper',
    profileImageUrl: null,
    operationalStatus: 'DISPONIBILE',
    active: true,
    specialization: 'PERSONAL_TRAINER',
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
    ...overrides,
  };
}

describe('mapAuthenticatedUserToAccessProfile', () => {
  it('mappa un client coerente', () => {
    expect(
      mapAuthenticatedUserToAccessProfile(account(), clientProfile()),
    ).toEqual({
      role: 'CLIENT',
      specialization: null,
    });
  });

  it.each(['PERSONAL_TRAINER', 'NUTRITIONIST'] as const)(
    'mappa un professionista %s',
    (specialization) => {
      expect(
        mapAuthenticatedUserToAccessProfile(
          account({ role: 'PROFESSIONAL' }),
          professionalProfile({ specialization }),
        ),
      ).toEqual({
        role: 'PROFESSIONAL',
        specialization,
      });
    },
  );

  it('non usa active per decidere l’autenticazione', () => {
    expect(
      mapAuthenticatedUserToAccessProfile(
        account(),
        clientProfile({ active: false }),
      ),
    ).toEqual({
      role: 'CLIENT',
      specialization: null,
    });
  });

  it.each([
    ['stringa', '1'],
    ['null', null],
    ['NaN', Number.NaN],
    ['Infinity', Number.POSITIVE_INFINITY],
    ['non intero', 1.5],
    ['zero', 0],
    ['negativo', -1],
  ])('rifiuta account.id runtime %s', (_label, invalidId) => {
    const malformed = account();
    Reflect.set(malformed, 'id', invalidId);

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformed, clientProfile()),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta account.id assente', () => {
    const malformed = account();
    Reflect.deleteProperty(malformed, 'id');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformed, clientProfile()),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta profile.id assente', () => {
    const malformed = clientProfile();
    Reflect.deleteProperty(malformed, 'id');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(account(), malformed),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta entrambi gli id assenti', () => {
    const malformedAccount = account();
    const malformedProfile = clientProfile();
    Reflect.deleteProperty(malformedAccount, 'id');
    Reflect.deleteProperty(malformedProfile, 'id');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformedAccount, malformedProfile),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta id differenti anche con ruolo coerente', () => {
    expect(() =>
      mapAuthenticatedUserToAccessProfile(
        account({ id: 1 }),
        clientProfile({ id: 2 }),
      ),
    ).toThrow(
      expect.objectContaining({
        code: 'IDENTITY_MISMATCH',
      }),
    );
  });

  it('continua a mappare id validi e uguali', () => {
    expect(
      mapAuthenticatedUserToAccessProfile(
        account({ id: 42 }),
        clientProfile({ id: 42 }),
      ),
    ).toEqual({
      role: 'CLIENT',
      specialization: null,
    });
  });

  it('rifiuta account.role assente', () => {
    const malformed = account();
    Reflect.deleteProperty(malformed, 'role');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformed, clientProfile()),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta profile.role assente', () => {
    const malformed = clientProfile();
    Reflect.deleteProperty(malformed, 'role');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(account(), malformed),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta role ADMIN uguali senza derivare un profilo professionale', () => {
    const malformedAccount = account();
    const malformedProfile = clientProfile();
    Reflect.set(malformedAccount, 'role', 'ADMIN');
    Reflect.set(malformedProfile, 'role', 'ADMIN');
    Reflect.set(malformedProfile, 'specialization', 'PERSONAL_TRAINER');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformedAccount, malformedProfile),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta un role valido accoppiato a un role fuori dominio', () => {
    const malformed = clientProfile();
    Reflect.set(malformed, 'role', 'ADMIN');

    expect(() =>
      mapAuthenticatedUserToAccessProfile(account(), malformed),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta un role runtime non stringa', () => {
    const malformed = account();
    Reflect.set(malformed, 'role', 42);

    expect(() =>
      mapAuthenticatedUserToAccessProfile(malformed, clientProfile()),
    ).toThrow(
      expect.objectContaining({
        code: 'INCOMPLETE_DATA',
      }),
    );
  });

  it('rifiuta ruoli differenti', () => {
    expect(() =>
      mapAuthenticatedUserToAccessProfile(
        account({ role: 'PROFESSIONAL' }),
        clientProfile(),
      ),
    ).toThrow(
      expect.objectContaining({
        code: 'ROLE_MISMATCH',
      }),
    );
  });

  it('rifiuta una specialization client inattesa a runtime', () => {
    const malformed = {
      ...clientProfile(),
      specialization: 'PERSONAL_TRAINER',
    } as unknown as MyProfileResponse;

    expect(() =>
      mapAuthenticatedUserToAccessProfile(account(), malformed),
    ).toThrow(
      expect.objectContaining({
        code: 'INVALID_CLIENT_SPECIALIZATION',
      }),
    );
  });

  it.each([undefined, 'UNKNOWN'])(
    'rifiuta specialization professionale runtime %s',
    (specialization) => {
      const malformed = {
        ...professionalProfile(),
        specialization,
      } as unknown as MyProfileResponse;

      expect(() =>
        mapAuthenticatedUserToAccessProfile(
          account({ role: 'PROFESSIONAL' }),
          malformed,
        ),
      ).toThrow(AuthConsistencyError);
    },
  );
});
