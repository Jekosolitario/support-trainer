import type { Gender, RegisterClientRequest } from '../api/authTypes';
import type { HttpApiError } from '../api/types';
import { validateRegistrationPassword } from './passwordPolicy';

export const CLIENT_GENDERS: readonly Gender[] = [
  'MALE',
  'FEMALE',
  'OTHER',
  'NOT_SPECIFIED',
];

export interface RegisterClientDraft {
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly password: string;
  readonly birthDate: string;
  readonly heightCm: string;
  readonly primaryGoal: string;
  readonly gender: Gender | '';
  readonly medicalNotes: string;
  readonly injuryNotes: string;
  readonly notes: string;
}

export type RegisterClientField = keyof RegisterClientDraft;

export type RegisterClientPhase =
  'form' | 'submitting' | 'confirmed' | 'inviteUnavailable' | 'ambiguous';

export type RegisterClientTerminalPhase = Extract<
  RegisterClientPhase,
  'confirmed' | 'inviteUnavailable' | 'ambiguous'
>;

export interface RegisterClientFlowState {
  readonly phase: RegisterClientPhase;
  readonly draft: RegisterClientDraft;
}

export type RegisterClientFlowAction =
  | {
      readonly type: 'updateField';
      readonly field: RegisterClientField;
      readonly value: RegisterClientDraft[RegisterClientField];
    }
  | {
      readonly type: 'setInteractivePhase';
      readonly phase: Extract<RegisterClientPhase, 'form' | 'submitting'>;
    }
  | {
      readonly type: 'enterTerminal';
      readonly phase: RegisterClientTerminalPhase;
    };

export type RegisterClientFieldErrors = Partial<
  Record<RegisterClientField, readonly string[]>
>;

export interface RegisterClientValidationPresentation {
  readonly fieldErrors: RegisterClientFieldErrors;
  readonly summary: string | null;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CIVIL_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const HEIGHT_PATTERN = /^\d{1,3}(?:[.,]\d{1,2})?$/;

const FIELD_NAMES = new Set<RegisterClientField>([
  'firstName',
  'lastName',
  'email',
  'password',
  'birthDate',
  'heightCm',
  'primaryGoal',
  'gender',
  'medicalNotes',
  'injuryNotes',
  'notes',
]);

export function createEmptyRegisterClientDraft(): RegisterClientDraft {
  return {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    birthDate: '',
    heightCm: '',
    primaryGoal: '',
    gender: '',
    medicalNotes: '',
    injuryNotes: '',
    notes: '',
  };
}

export function createInitialRegisterClientFlowState(): RegisterClientFlowState {
  return {
    phase: 'form',
    draft: createEmptyRegisterClientDraft(),
  };
}

export function registerClientFlowReducer(
  state: RegisterClientFlowState,
  action: RegisterClientFlowAction,
): RegisterClientFlowState {
  switch (action.type) {
    case 'updateField':
      return {
        ...state,
        draft: { ...state.draft, [action.field]: action.value },
      };
    case 'setInteractivePhase':
      return { ...state, phase: action.phase };
    case 'enterTerminal':
      return {
        phase: action.phase,
        draft: createEmptyRegisterClientDraft(),
      };
  }
}

function addError(
  errors: Partial<Record<RegisterClientField, string[]>>,
  field: RegisterClientField,
  message: string,
): void {
  const current = errors[field];
  if (current === undefined) {
    errors[field] = [message];
    return;
  }

  if (!current.includes(message)) {
    current.push(message);
  }
}

function isLeapYear(year: number): boolean {
  return year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0);
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    return isLeapYear(year) ? 29 : 28;
  }

  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

export function localCivilDate(date: Date): string {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function isValidPastCivilDate(
  value: string,
  today = localCivilDate(new Date()),
): boolean {
  const match = CIVIL_DATE_PATTERN.exec(value);
  if (match === null) {
    return false;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (
    !Number.isInteger(year) ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > daysInMonth(year, month)
  ) {
    return false;
  }

  return value < today;
}

export function parseHeightCm(value: string): number | null {
  const trimmed = value.trim();
  if (!HEIGHT_PATTERN.test(trimmed)) {
    return null;
  }

  const parsed = Number(trimmed.replace(',', '.'));
  if (!Number.isFinite(parsed) || parsed < 0.01 || parsed > 999.99) {
    return null;
  }

  return parsed;
}

export function validateRegisterClientDraft(
  draft: RegisterClientDraft,
  today = localCivilDate(new Date()),
): RegisterClientFieldErrors {
  const errors: Partial<Record<RegisterClientField, string[]>> = {};
  const firstName = draft.firstName.trim();
  const lastName = draft.lastName.trim();
  const email = draft.email.trim();
  const primaryGoal = draft.primaryGoal.trim();

  if (firstName === '') {
    addError(errors, 'firstName', 'Inserisci il nome.');
  } else if (firstName.length > 100) {
    addError(errors, 'firstName', 'Il nome non può superare 100 caratteri.');
  }

  if (lastName === '') {
    addError(errors, 'lastName', 'Inserisci il cognome.');
  } else if (lastName.length > 100) {
    addError(errors, 'lastName', 'Il cognome non può superare 100 caratteri.');
  }

  if (email === '') {
    addError(errors, 'email', 'Inserisci l’email.');
  } else if (email.length > 100) {
    addError(errors, 'email', 'L’email non può superare 100 caratteri.');
  } else if (!EMAIL_PATTERN.test(email)) {
    addError(errors, 'email', 'Inserisci un indirizzo email valido.');
  }

  const passwordError = validateRegistrationPassword(draft.password);
  if (passwordError !== null) {
    addError(errors, 'password', passwordError);
  }

  if (draft.birthDate === '') {
    addError(errors, 'birthDate', 'Inserisci la data di nascita.');
  } else if (!isValidPastCivilDate(draft.birthDate, today)) {
    addError(
      errors,
      'birthDate',
      'Inserisci una data valida precedente a oggi.',
    );
  }

  if (draft.heightCm.trim() === '') {
    addError(errors, 'heightCm', 'Inserisci l’altezza.');
  } else if (parseHeightCm(draft.heightCm) === null) {
    addError(
      errors,
      'heightCm',
      'Inserisci un’altezza tra 0,01 e 999,99 con massimo due decimali.',
    );
  }

  if (primaryGoal === '') {
    addError(errors, 'primaryGoal', 'Inserisci l’obiettivo principale.');
  } else if (primaryGoal.length > 255) {
    addError(
      errors,
      'primaryGoal',
      'L’obiettivo principale non può superare 255 caratteri.',
    );
  }

  if (draft.gender === '' || !CLIENT_GENDERS.includes(draft.gender)) {
    addError(errors, 'gender', 'Seleziona il genere.');
  }

  for (const field of ['medicalNotes', 'injuryNotes', 'notes'] as const) {
    if (draft[field].trim().length > 5000) {
      addError(errors, field, 'Questo campo non può superare 5000 caratteri.');
    }
  }

  return errors;
}

function optionalTrimmed(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

export function buildRegisterClientPayload(
  draft: RegisterClientDraft,
  inviteCode: string,
): RegisterClientRequest | null {
  const heightCm = parseHeightCm(draft.heightCm);
  if (heightCm === null || draft.gender === '') {
    return null;
  }

  const medicalNotes = optionalTrimmed(draft.medicalNotes);
  const injuryNotes = optionalTrimmed(draft.injuryNotes);
  const notes = optionalTrimmed(draft.notes);

  return {
    firstName: draft.firstName.trim(),
    lastName: draft.lastName.trim(),
    email: draft.email.trim().toLowerCase(),
    password: draft.password,
    inviteCode,
    birthDate: draft.birthDate,
    heightCm,
    primaryGoal: draft.primaryGoal.trim(),
    gender: draft.gender,
    ...(medicalNotes === undefined ? {} : { medicalNotes }),
    ...(injuryNotes === undefined ? {} : { injuryNotes }),
    ...(notes === undefined ? {} : { notes }),
  };
}

function localBackendMessage(
  field: RegisterClientField,
  code: string,
): string | null {
  const messages: Partial<Record<RegisterClientField, Record<string, string>>> =
    {
      firstName: {
        NotBlank: 'Inserisci il nome.',
        Size: 'Il nome non può superare 100 caratteri.',
      },
      lastName: {
        NotBlank: 'Inserisci il cognome.',
        Size: 'Il cognome non può superare 100 caratteri.',
      },
      email: {
        NotBlank: 'Inserisci l’email.',
        Email: 'Inserisci un indirizzo email valido.',
        Size: 'L’email non può superare 100 caratteri.',
      },
      password: {
        NotBlank: 'Inserisci la password.',
        Size: 'La password deve contenere almeno 8 caratteri.',
        Pattern:
          'La password deve contenere almeno una maiuscola, un numero e un carattere speciale.',
        BcryptCompatiblePassword:
          'La password non può superare 72 byte in codifica UTF-8.',
      },
      birthDate: {
        NotNull: 'Inserisci la data di nascita.',
        Past: 'Inserisci una data valida precedente a oggi.',
      },
      heightCm: {
        NotNull: 'Inserisci l’altezza.',
        DecimalMin:
          'Inserisci un’altezza tra 0,01 e 999,99 con massimo due decimali.',
        Digits:
          'Inserisci un’altezza tra 0,01 e 999,99 con massimo due decimali.',
      },
      primaryGoal: {
        NotBlank: 'Inserisci l’obiettivo principale.',
        Size: 'L’obiettivo principale non può superare 255 caratteri.',
      },
      gender: {
        NotNull: 'Seleziona il genere.',
      },
      medicalNotes: {
        Size: 'Questo campo non può superare 5000 caratteri.',
      },
      injuryNotes: {
        Size: 'Questo campo non può superare 5000 caratteri.',
      },
      notes: {
        Size: 'Questo campo non può superare 5000 caratteri.',
      },
    };

  return messages[field]?.[code] ?? null;
}

export function mapRegisterClientValidationFailure(
  error: HttpApiError,
): RegisterClientValidationPresentation {
  const fieldErrors: Partial<Record<RegisterClientField, string[]>> = {};
  let hasGlobalError = false;
  const responseErrors = error.body?.fieldErrors;

  if (responseErrors === undefined || responseErrors.length === 0) {
    hasGlobalError = true;
  }

  for (const entry of responseErrors ?? []) {
    if (
      typeof entry.field !== 'string' ||
      !FIELD_NAMES.has(entry.field as RegisterClientField)
    ) {
      hasGlobalError = true;
      continue;
    }

    const field = entry.field as RegisterClientField;
    const message = localBackendMessage(field, entry.code);
    if (message === null) {
      hasGlobalError = true;
      continue;
    }

    addError(fieldErrors, field, message);
  }

  return {
    fieldErrors,
    summary: hasGlobalError ? 'Controlla i dati inseriti e riprova.' : null,
  };
}
