import { StaleAuthOperationError } from '../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
} from '../api/types';

export type EmailVerificationUiStatus =
  | 'success'
  | 'expired'
  | 'not-found'
  | 'already-used'
  | 'application-error'
  | 'temporary-error';

export interface EmailVerificationErrorPresentation {
  readonly status: EmailVerificationUiStatus;
  readonly summary: string;
}

export function getEmailVerificationErrorPresentation(
  error: unknown,
): EmailVerificationErrorPresentation {
  if (
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError
  ) {
    return {
      status: 'temporary-error',
      summary: 'Verifica temporaneamente non disponibile. Riprova.',
    };
  }

  if (error instanceof StaleAuthOperationError) {
    return {
      status: 'temporary-error',
      summary: 'Verifica temporaneamente non disponibile. Riprova.',
    };
  }

  if (!(error instanceof HttpApiError)) {
    return {
      status: 'temporary-error',
      summary: 'Verifica temporaneamente non disponibile. Riprova.',
    };
  }

  if (error.status >= 500) {
    return {
      status: 'temporary-error',
      summary: 'Verifica temporaneamente non disponibile. Riprova.',
    };
  }

  switch (error.body?.code) {
    case 'EMAIL_VERIFICATION_TOKEN_EXPIRED':
      return {
        status: 'expired',
        summary: 'Il link di verifica è scaduto.',
      };
    case 'EMAIL_VERIFICATION_TOKEN_NOT_FOUND':
      return {
        status: 'not-found',
        summary: 'Il link di verifica non è valido.',
      };
    case 'EMAIL_VERIFICATION_TOKEN_ALREADY_USED':
      return {
        status: 'already-used',
        summary: 'Questo link di verifica non è più utilizzabile.',
      };
    case 'PROFESSIONAL_NOT_ACTIVE':
    case 'CLIENT_NOT_ACTIVE':
      return {
        status: 'application-error',
        summary: 'L’account non può essere verificato in questo momento.',
      };
    case 'CSRF_VALIDATION_FAILED':
      return {
        status: 'temporary-error',
        summary: 'Verifica temporaneamente non disponibile. Riprova.',
      };
    default:
      if (error.status >= 400 && error.status < 500) {
        return {
          status: 'application-error',
          summary: 'La verifica non può essere completata.',
        };
      }

      return {
        status: 'temporary-error',
        summary: 'Verifica temporaneamente non disponibile. Riprova.',
      };
  }
}

export function getResendEmailFieldMessage(error: unknown): {
  readonly summary: string | null;
  readonly email?: string;
} {
  if (
    !(error instanceof HttpApiError) ||
    error.body?.code !== 'VALIDATION_ERROR'
  ) {
    return {
      summary: 'Invio non completato. Riprova.',
    };
  }

  for (const fieldError of error.body.fieldErrors ?? []) {
    if (fieldError.field !== 'email') {
      continue;
    }

    switch (fieldError.code) {
      case 'NotBlank':
        return { summary: null, email: 'Inserisci l’email.' };
      case 'Email':
        return { summary: null, email: 'Inserisci un indirizzo email valido.' };
      case 'Size':
        return {
          summary: null,
          email: 'L’email non può superare 100 caratteri.',
        };
      default:
        break;
    }
  }

  return { summary: 'Controlla i dati inseriti e riprova.' };
}
