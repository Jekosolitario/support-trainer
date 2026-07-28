import { describe, expect, it } from 'vitest';

import type {
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
} from '../../../api/authTypes';
import {
  buildClientProfilePatch,
  buildProfessionalProfilePatch,
  isClientProfileDirty,
  mapClientProfileToDraft,
  mapProfessionalProfileToDraft,
  validateClientProfileDraft,
  validateProfessionalProfileDraft,
  type ClientProfileDraft,
  type ProfessionalProfileDraft,
} from './profileFormModel';

const CLIENT_BASELINE: MyClientProfileResponse = {
  id: 1,
  role: 'CLIENT',
  firstName: 'Ada',
  lastName: 'Lovelace',
  profileImageUrl: 'https://cdn.example/ada.png',
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
  medicalNotes: 'Nota medica',
  injuryNotes: null,
  notes: null,
};

const PROFESSIONAL_BASELINE: MyProfessionalProfileResponse = {
  id: 2,
  role: 'PROFESSIONAL',
  firstName: 'Grace',
  lastName: 'Hopper',
  profileImageUrl: null,
  operationalStatus: 'DISPONIBILE',
  active: true,
  specialization: 'PERSONAL_TRAINER',
  phoneNumber: '+39 333',
  bio: 'Bio',
  workplaceName: 'Gym',
  city: 'Roma',
  instagramUrl: 'https://instagram.com/grace',
  websiteUrl: 'https://grace.example',
  birthDate: null,
  heightCm: null,
  primaryGoal: null,
  gender: null,
  medicalNotes: null,
  injuryNotes: null,
  notes: null,
};

function clientDraft(
  overrides: Partial<ClientProfileDraft> = {},
): ClientProfileDraft {
  return {
    ...mapClientProfileToDraft(CLIENT_BASELINE),
    ...overrides,
  };
}

function professionalDraft(
  overrides: Partial<ProfessionalProfileDraft> = {},
): ProfessionalProfileDraft {
  return {
    ...mapProfessionalProfileToDraft(PROFESSIONAL_BASELINE),
    ...overrides,
  };
}

describe('buildClientProfilePatch', () => {
  it('restituisce null quando non ci sono cambiamenti', () => {
    expect(buildClientProfilePatch(CLIENT_BASELINE, clientDraft())).toBeNull();
  });

  it('include solo il campo modificato e omette gli invariati', () => {
    expect(
      buildClientProfilePatch(
        CLIENT_BASELINE,
        clientDraft({ firstName: ' Augusta ' }),
      ),
    ).toEqual({ firstName: 'Augusta' });
  });

  it('tratta null ↔ empty come invariato per le note opzionali', () => {
    expect(
      buildClientProfilePatch(
        CLIENT_BASELINE,
        clientDraft({ injuryNotes: '', notes: '   ' }),
      ),
    ).toBeNull();
  });

  it('invia "" per clear di note opzionali valorizzate', () => {
    expect(
      buildClientProfilePatch(
        CLIENT_BASELINE,
        clientDraft({ medicalNotes: '' }),
      ),
    ).toEqual({ medicalNotes: '' });
  });

  it('non clear-a birthDate, heightCm o gender con draft vuoto', () => {
    const patch = buildClientProfilePatch(
      CLIENT_BASELINE,
      clientDraft({ birthDate: '', heightCm: '', gender: '' }),
    );

    expect(patch).toBeNull();
    expect(
      isClientProfileDirty(
        CLIENT_BASELINE,
        clientDraft({ birthDate: '', heightCm: '', gender: '' }),
      ),
    ).toBe(true);
  });

  it('aggiorna birthDate, heightCm e gender solo con valori validi diversi', () => {
    expect(
      buildClientProfilePatch(
        CLIENT_BASELINE,
        clientDraft({
          birthDate: '1990-01-01',
          heightCm: '175.5',
          gender: 'OTHER',
        }),
      ),
    ).toEqual({
      birthDate: '1990-01-01',
      heightCm: 175.5,
      gender: 'OTHER',
    });
  });

  it('produce esclusivamente chiavi CLIENT e mai chiavi PROFESSIONAL o read-only', () => {
    const patch = buildClientProfilePatch(
      CLIENT_BASELINE,
      clientDraft({
        firstName: 'Ada',
        lastName: 'Byron',
        primaryGoal: 'Forza',
        notes: 'Nota',
      }),
    );

    expect(patch).toEqual({
      lastName: 'Byron',
      primaryGoal: 'Forza',
      notes: 'Nota',
    });
    expect(patch).not.toHaveProperty('phoneNumber');
    expect(patch).not.toHaveProperty('bio');
    expect(patch).not.toHaveProperty('workplaceName');
    expect(patch).not.toHaveProperty('city');
    expect(patch).not.toHaveProperty('instagramUrl');
    expect(patch).not.toHaveProperty('websiteUrl');
    expect(patch).not.toHaveProperty('id');
    expect(patch).not.toHaveProperty('role');
    expect(patch).not.toHaveProperty('specialization');
    expect(patch).not.toHaveProperty('profileImageUrl');
    expect(patch).not.toHaveProperty('operationalStatus');
    expect(patch).not.toHaveProperty('active');
  });
});

describe('buildProfessionalProfilePatch', () => {
  it('restituisce null quando non ci sono cambiamenti', () => {
    expect(
      buildProfessionalProfilePatch(PROFESSIONAL_BASELINE, professionalDraft()),
    ).toBeNull();
  });

  it('include solo il campo modificato', () => {
    expect(
      buildProfessionalProfilePatch(
        PROFESSIONAL_BASELINE,
        professionalDraft({ city: ' Milano ' }),
      ),
    ).toEqual({ city: 'Milano' });
  });

  it('invia "" per clear degli optional e omette null ↔ empty invariati', () => {
    const emptyOptionalBaseline: MyProfessionalProfileResponse = {
      ...PROFESSIONAL_BASELINE,
      phoneNumber: null,
      bio: null,
      workplaceName: null,
      city: null,
      instagramUrl: null,
      websiteUrl: null,
    };

    expect(
      buildProfessionalProfilePatch(
        emptyOptionalBaseline,
        mapProfessionalProfileToDraft(emptyOptionalBaseline),
      ),
    ).toBeNull();

    expect(
      buildProfessionalProfilePatch(
        PROFESSIONAL_BASELINE,
        professionalDraft({
          phoneNumber: '',
          bio: '  ',
          workplaceName: '',
          city: '',
          instagramUrl: '',
          websiteUrl: '',
        }),
      ),
    ).toEqual({
      phoneNumber: '',
      bio: '',
      workplaceName: '',
      city: '',
      instagramUrl: '',
      websiteUrl: '',
    });
  });

  it('accetta URL http/https e produce solo chiavi PROFESSIONAL', () => {
    const patch = buildProfessionalProfilePatch(
      PROFESSIONAL_BASELINE,
      professionalDraft({
        firstName: 'Grace',
        lastName: 'Murray',
        instagramUrl: 'https://instagram.com/hopper',
        websiteUrl: 'http://hopper.example',
      }),
    );

    expect(patch).toEqual({
      lastName: 'Murray',
      instagramUrl: 'https://instagram.com/hopper',
      websiteUrl: 'http://hopper.example',
    });
    expect(patch).not.toHaveProperty('birthDate');
    expect(patch).not.toHaveProperty('heightCm');
    expect(patch).not.toHaveProperty('primaryGoal');
    expect(patch).not.toHaveProperty('gender');
    expect(patch).not.toHaveProperty('medicalNotes');
    expect(patch).not.toHaveProperty('injuryNotes');
    expect(patch).not.toHaveProperty('notes');
    expect(patch).not.toHaveProperty('id');
    expect(patch).not.toHaveProperty('role');
    expect(patch).not.toHaveProperty('specialization');
    expect(patch).not.toHaveProperty('profileImageUrl');
    expect(patch).not.toHaveProperty('operationalStatus');
    expect(patch).not.toHaveProperty('active');
  });
});

describe('validate profile drafts', () => {
  it('valida vincoli CLIENT senza regole più strette del backend', () => {
    expect(validateClientProfileDraft(clientDraft())).toEqual({});
    expect(
      validateClientProfileDraft(
        clientDraft({
          firstName: '',
          birthDate: '2999-01-01',
          heightCm: '0',
          gender: '',
        }),
      ),
    ).toMatchObject({
      firstName: expect.any(String),
      birthDate: expect.any(String),
      heightCm: expect.any(String),
      gender: expect.any(String),
    });
  });

  it('valida URL PROFESSIONAL con lo stesso contratto del backend', () => {
    expect(validateProfessionalProfileDraft(professionalDraft())).toEqual({});
    expect(
      validateProfessionalProfileDraft(
        professionalDraft({ instagramUrl: 'instagram.com/x' }),
      ),
    ).toMatchObject({
      instagramUrl: expect.any(String),
    });
    expect(
      validateProfessionalProfileDraft(professionalDraft({ instagramUrl: '' })),
    ).toEqual({});
  });
});
