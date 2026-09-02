import { StaleAuthOperationError } from '../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
} from '../api/types';

export type PasswordRecoveryRequestPresentation =
  | { readonly kind: 'email'; readonly email: string }
  | { readonly kind: 'technical'; readonly summary: string };

export type PasswordRecoveryConfirmPresentation =
  | { readonly kind: 'invalid-or-expired'; readonly summary: string }
  | {
      readonly kind: 'validation';
      readonly password?: string;
      readonly summary: string | null;
    }
  | { readonly kind: 'technical'; readonly summary: string };

const REQUEST_TECHNICAL =
  'Non è stato possibile inviare la richiesta. Riprova.';
const CONFIRM_TECHNICAL =
  'Non è stato possibile aggiornare la password. Riprova.';
const INVALID_OR_EXPIRED = 'Questo link non è valido o non è più utilizzabile.';
const GENERIC_VALIDATION = 'Controlla i dati inseriti e riprova.';

function emailFieldMessage(code: string): string | null {
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

function passwordFieldMessage(code: string): string | null {
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
}

function isTechnicalTransport(error: unknown): boolean {
  return (
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError ||
    error instanceof StaleAuthOperationError
  );
}

export function getPasswordRecoveryRequestPresentation(
  error: unknown,
): PasswordRecoveryRequestPresentation {
  if (isTechnicalTransport(error)) {
    return { kind: 'technical', summary: REQUEST_TECHNICAL };
  }

  if (!(error instanceof HttpApiError)) {
    return { kind: 'technical', summary: REQUEST_TECHNICAL };
  }

  if (error.status >= 500 || error.body?.code === 'CSRF_VALIDATION_FAILED') {
    return { kind: 'technical', summary: REQUEST_TECHNICAL };
  }

  if (error.body?.code === 'VALIDATION_ERROR') {
    for (const fieldError of error.body.fieldErrors ?? []) {
      if (fieldError.field !== 'email') {
        continue;
      }
      const message = emailFieldMessage(fieldError.code);
      if (message !== null) {
        return { kind: 'email', email: message };
      }
    }

    return { kind: 'technical', summary: GENERIC_VALIDATION };
  }

  return { kind: 'technical', summary: REQUEST_TECHNICAL };
}

export function getPasswordRecoveryConfirmPresentation(
  error: unknown,
): PasswordRecoveryConfirmPresentation {
  if (isTechnicalTransport(error)) {
    return { kind: 'technical', summary: CONFIRM_TECHNICAL };
  }

  if (!(error instanceof HttpApiError)) {
    return { kind: 'technical', summary: CONFIRM_TECHNICAL };
  }

  if (error.body?.code === 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED') {
    return { kind: 'invalid-or-expired', summary: INVALID_OR_EXPIRED };
  }

  if (error.status >= 500 || error.body?.code === 'CSRF_VALIDATION_FAILED') {
    return { kind: 'technical', summary: CONFIRM_TECHNICAL };
  }

  if (error.body?.code === 'VALIDATION_ERROR') {
    let password: string | undefined;
    let tokenInvalid = false;
    let hasUnmapped =
      error.body.fieldErrors === undefined ||
      error.body.fieldErrors.length === 0;

    for (const fieldError of error.body.fieldErrors ?? []) {
      if (fieldError.field === 'token') {
        tokenInvalid = true;
        continue;
      }

      if (fieldError.field === 'newPassword') {
        const message = passwordFieldMessage(fieldError.code);
        if (message === null) {
          hasUnmapped = true;
          continue;
        }
        if (password === undefined) {
          password = message;
        }
        continue;
      }

      hasUnmapped = true;
    }

    if (tokenInvalid && password === undefined) {
      return { kind: 'invalid-or-expired', summary: INVALID_OR_EXPIRED };
    }

    return {
      kind: 'validation',
      password,
      summary:
        hasUnmapped || password === undefined ? GENERIC_VALIDATION : null,
    };
  }

  return { kind: 'technical', summary: CONFIRM_TECHNICAL };
}
