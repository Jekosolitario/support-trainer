import { describe, expect, it } from 'vitest';

import { HttpApiError, type ErrorResponse } from '../api/types';
import {
  buildRegisterClientPayload,
  createEmptyRegisterClientDraft,
  createInitialRegisterClientFlowState,
  isValidPastCivilDate,
  mapRegisterClientValidationFailure,
  parseHeightCm,
  registerClientFlowReducer,
  validateRegisterClientDraft,
  type RegisterClientDraft,
} from './registerClientForm';

const VALID_DRAFT: RegisterClientDraft = {
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'ada@example.com',
  password: 'Password1!',
  birthDate: '1995-12-10',
  heightCm: '170.25',
  primaryGoal: 'Migliorare la forma fisica',
  gender: 'FEMALE',
  medicalNotes: '',
  injuryNotes: '',
  notes: '',
};

function httpValidationError(
  fieldErrors: ErrorResponse['fieldErrors'],
): HttpApiError {
  return new HttpApiError(
    400,
    {
      timestamp: '2026-08-05T10:00:00Z',
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'backend detail hidden',
      path: '/api/v1/auth/register/client',
      fieldErrors,
    },
    new Response(null, { status: 400 }),
  );
}

describe('registerClientForm', () => {
  it('crea un draft nuovo e completamente vuoto', () => {
    const first = createEmptyRegisterClientDraft();
    const second = createEmptyRegisterClientDraft();

    expect(first).not.toBe(second);
    expect(Object.values(first).every((value) => value === '')).toBe(true);
  });

  it.each(['confirmed', 'ambiguous', 'inviteUnavailable'] as const)(
    'la transizione %s distrugge atomicamente tutto il draft sensibile',
    (phase) => {
      const sensitiveDraft: RegisterClientDraft = {
        ...VALID_DRAFT,
        password: 'SecretPass1!',
        medicalNotes: 'Nota medica privata',
        injuryNotes: 'Infortunio privato',
        notes: 'Nota privata',
      };
      const initial = {
        ...createInitialRegisterClientFlowState(),
        phase: 'submitting' as const,
        draft: sensitiveDraft,
      };

      const terminal = registerClientFlowReducer(initial, {
        type: 'enterTerminal',
        phase,
      });

      expect(terminal).toEqual({
        phase,
        draft: createEmptyRegisterClientDraft(),
      });
      expect(terminal.draft).not.toBe(sensitiveDraft);
      expect(sensitiveDraft.password).toBe('SecretPass1!');
    },
  );

  it.each([
    ['1999-12-31', true],
    ['2024-02-29', true],
    ['2026-08-04', true],
    ['2026-08-05', false],
    ['2026-08-06', false],
    ['2025-02-29', false],
    ['2024-13-01', false],
    ['2024-04-31', false],
    ['04/01/2020', false],
    ['', false],
  ] as const)(
    'valida la data civile %s senza conversione UTC',
    (value, valid) => {
      expect(isValidPastCivilDate(value, '2026-08-05')).toBe(valid);
    },
  );

  it.each([
    ['0.01', 0.01],
    ['170', 170],
    ['170.25', 170.25],
    ['170,25', 170.25],
    ['999.99', 999.99],
    ['', null],
    ['0', null],
    ['-1', null],
    ['1000', null],
    ['12.345', null],
    ['Infinity', null],
    ['NaN', null],
  ] as const)('parsa altezza strutturale %s', (value, expected) => {
    expect(parseHeightCm(value)).toBe(expected);
  });

  it('accetta il draft valido', () => {
    expect(validateRegisterClientDraft(VALID_DRAFT, '2026-08-05')).toEqual({});
  });

  it('valida tutti i required senza chiamare helper esterni permissivi', () => {
    const errors = validateRegisterClientDraft(
      createEmptyRegisterClientDraft(),
      '2026-08-05',
    );

    expect(Object.keys(errors)).toEqual([
      'firstName',
      'lastName',
      'email',
      'password',
      'birthDate',
      'heightCm',
      'primaryGoal',
      'gender',
    ]);
  });

  it('applica blank, massimi, email e password condivisa', () => {
    const errors = validateRegisterClientDraft(
      {
        ...VALID_DRAFT,
        firstName: '   ',
        lastName: 'L'.repeat(101),
        email: 'not-an-email',
        password: 'password',
        primaryGoal: 'G'.repeat(256),
      },
      '2026-08-05',
    );

    expect(errors.firstName).toContain('Inserisci il nome.');
    expect(errors.lastName).toContain(
      'Il cognome non può superare 100 caratteri.',
    );
    expect(errors.email).toContain('Inserisci un indirizzo email valido.');
    expect(errors.password?.[0]).toMatch(/maiuscola/);
    expect(errors.primaryGoal).toContain(
      'L’obiettivo principale non può superare 255 caratteri.',
    );
  });

  it.each(['medicalNotes', 'injuryNotes', 'notes'] as const)(
    'rifiuta %s oltre 5000 caratteri dopo trim',
    (field) => {
      expect(
        validateRegisterClientDraft(
          { ...VALID_DRAFT, [field]: ` ${'N'.repeat(5001)} ` },
          '2026-08-05',
        )[field],
      ).toContain('Questo campo non può superare 5000 caratteri.');
    },
  );

  it('costruisce il payload esatto, normalizza senza mutare la password e omette note blank', () => {
    const payload = buildRegisterClientPayload(
      {
        ...VALID_DRAFT,
        firstName: '  Ada  ',
        lastName: '  Lovelace ',
        email: '  ADA@Example.COM ',
        password: ' Password1! ',
        heightCm: '170,25',
        primaryGoal: '  Aumentare la forza ',
        medicalNotes: '   ',
        injuryNotes: '\n\t',
        notes: '',
      },
      'INV-CANONICAL01',
    );

    expect(payload).toEqual({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      password: ' Password1! ',
      inviteCode: 'INV-CANONICAL01',
      birthDate: '1995-12-10',
      heightCm: 170.25,
      primaryGoal: 'Aumentare la forza',
      gender: 'FEMALE',
    });
  });

  it('include soltanto le note valorizzate e trimmed', () => {
    expect(
      buildRegisterClientPayload(
        {
          ...VALID_DRAFT,
          medicalNotes: '  Controllo periodico ',
          injuryNotes: ' Distorsione pregressa ',
          notes: ' Preferenza oraria ',
        },
        'INV-CANONICAL02',
      ),
    ).toMatchObject({
      medicalNotes: 'Controllo periodico',
      injuryNotes: 'Distorsione pregressa',
      notes: 'Preferenza oraria',
    });
  });

  it('preserva più errori riconosciuti per campo senza mostrare messaggi backend', () => {
    const presentation = mapRegisterClientValidationFailure(
      httpValidationError([
        { field: 'email', code: 'NotBlank', message: 'secret backend 1' },
        { field: 'email', code: 'Email', message: 'secret backend 2' },
      ]),
    );

    expect(presentation.summary).toBeNull();
    expect(presentation.fieldErrors.email).toEqual([
      'Inserisci l’email.',
      'Inserisci un indirizzo email valido.',
    ]);
    expect(JSON.stringify(presentation)).not.toContain('secret backend');
  });

  it('porta errori assenti o non mappabili nel summary globale', () => {
    const cases = [
      [{ field: null, code: 'NotBlank', message: 'hidden' }],
      [{ code: 'NotBlank', message: 'hidden' }],
      [{ field: 'unknown', code: 'Size', message: 'hidden' }],
      [{ field: 'email', code: 'UnknownConstraint', message: 'hidden' }],
      [],
    ];

    for (const fieldErrors of cases) {
      const presentation = mapRegisterClientValidationFailure(
        httpValidationError(fieldErrors),
      );

      expect(presentation.summary).toBe('Controlla i dati inseriti e riprova.');
      expect(JSON.stringify(presentation)).not.toContain('hidden');
    }
  });
});
