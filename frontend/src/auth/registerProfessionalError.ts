import { HttpApiError } from '../api/types';

export interface RegisterProfessionalFieldErrors {
  readonly firstName?: string;
  readonly lastName?: string;
  readonly email?: string;
  readonly password?: string;
  readonly specialization?: string;
}

export interface RegisterProfessionalErrorPresentation {
  readonly summary: string | null;
  readonly fieldErrors: RegisterProfessionalFieldErrors;
}

const GENERIC_ERROR = 'Registrazione non completata. Riprova.';
const GENERIC_VALIDATION = 'Controlla i dati inseriti e riprova.';

function fieldMessage(
  field: string | null | undefined,
  code: string,
): string | null {
  switch (field) {
    case 'firstName':
      switch (code) {
        case 'NotBlank':
          return 'Inserisci il nome.';
        case 'Size':
          return 'Il nome non può superare 100 caratteri.';
        default:
          return null;
      }
    case 'lastName':
      switch (code) {
        case 'NotBlank':
          return 'Inserisci il cognome.';
        case 'Size':
          return 'Il cognome non può superare 100 caratteri.';
        default:
          return null;
      }
    case 'email':
      switch (code) {
        case 'NotBlank':
          return 'Inserisci l’email.';
        case 'Email':
          return 'Inserisci un indirizzo email valido.';
        case 'Size':
          return 'L’email non può superare 100 caratteri.';
        default:
          return null;
      }
    case 'password':
      switch (code) {
        case 'NotBlank':
          return 'Inserisci la password.';
        case 'Size':
          return 'La password deve contenere almeno 8 caratteri.';
        case 'Pattern':
          return 'La password deve contenere almeno una maiuscola, un numero e un carattere speciale.';
        case 'BcryptCompatiblePassword':
          return 'La password non può superare 72 byte in codifica UTF-8.';
        default:
          return null;
      }
    case 'specialization':
      switch (code) {
        case 'NotNull':
          return 'Seleziona la specializzazione.';
        default:
          return null;
      }
    default:
      return null;
  }
}

export function getRegisterProfessionalErrorPresentation(
  error: unknown,
): RegisterProfessionalErrorPresentation | null {
  if (
    !(error instanceof HttpApiError) ||
    error.status < 400 ||
    error.status >= 500
  ) {
    return null;
  }

  if (error.body?.code !== 'VALIDATION_ERROR') {
    return {
      summary: GENERIC_ERROR,
      fieldErrors: {},
    };
  }

  const fieldErrors: {
    firstName?: string;
    lastName?: string;
    email?: string;
    password?: string;
    specialization?: string;
  } = {};
  const responseFieldErrors = error.body.fieldErrors;
  let hasUnmapped =
    responseFieldErrors === undefined || responseFieldErrors.length === 0;

  for (const fieldError of responseFieldErrors ?? []) {
    const message = fieldMessage(fieldError.field, fieldError.code);

    if (message === null) {
      hasUnmapped = true;
      continue;
    }

    const key = fieldError.field;

    if (
      (key === 'firstName' ||
        key === 'lastName' ||
        key === 'email' ||
        key === 'password' ||
        key === 'specialization') &&
      fieldErrors[key] === undefined
    ) {
      fieldErrors[key] = message;
    }
  }

  return {
    summary: hasUnmapped ? GENERIC_VALIDATION : null,
    fieldErrors,
  };
}

export function getRegisterGenericFailureMessage(): string {
  return GENERIC_ERROR;
}
