import { HttpApiError } from '../api/types';

export interface LoginFieldErrors {
  readonly email?: string;
  readonly password?: string;
}

export interface LoginErrorPresentation {
  readonly summary: string | null;
  readonly fieldErrors: LoginFieldErrors;
}

const GENERIC_ACCESS_ERROR = 'Accesso non completato. Riprova.';
const GENERIC_VALIDATION_ERROR = 'Controlla i dati inseriti e riprova.';

function validationMessage(
  field: string | null | undefined,
  code: string,
): string | null {
  if (field === 'email') {
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
  }

  if (field === 'password') {
    switch (code) {
      case 'NotBlank':
        return 'Inserisci la password.';
      case 'Size':
        return 'La password deve contenere almeno 8 caratteri.';
      default:
        return null;
    }
  }

  return null;
}

function validationPresentation(error: HttpApiError): LoginErrorPresentation {
  const fieldErrors: { email?: string; password?: string } = {};
  const responseFieldErrors = error.body?.fieldErrors;
  let hasUnmappedError =
    responseFieldErrors === undefined || responseFieldErrors.length === 0;

  for (const fieldError of responseFieldErrors ?? []) {
    const message = validationMessage(fieldError.field, fieldError.code);

    if (message === null) {
      hasUnmappedError = true;
      continue;
    }

    if (fieldError.field === 'email' && fieldErrors.email === undefined) {
      fieldErrors.email = message;
    } else if (
      fieldError.field === 'password' &&
      fieldErrors.password === undefined
    ) {
      fieldErrors.password = message;
    }
  }

  return {
    summary: hasUnmappedError ? GENERIC_VALIDATION_ERROR : null,
    fieldErrors,
  };
}

export function getLoginErrorPresentation(
  error: unknown,
): LoginErrorPresentation | null {
  if (
    !(error instanceof HttpApiError) ||
    error.status < 400 ||
    error.status >= 500
  ) {
    return null;
  }

  switch (error.body?.code) {
    case 'AUTHENTICATION_ERROR':
      return {
        summary: 'Email o password non corrette.',
        fieldErrors: {},
      };
    case 'ACCOUNT_NOT_ACTIVE':
      return {
        summary: 'L’account non è disponibile per l’accesso.',
        fieldErrors: {},
      };
    case 'EMAIL_NOT_VERIFIED':
      return {
        summary: 'L’indirizzo email non è ancora verificato.',
        fieldErrors: {},
      };
    case 'VALIDATION_ERROR':
      return validationPresentation(error);
    default:
      return {
        summary: GENERIC_ACCESS_ERROR,
        fieldErrors: {},
      };
  }
}
