import { HttpApiError } from '../api/types';

export interface ValidateInviteErrorPresentation {
  readonly kind: 'invalid' | 'temporary';
  readonly summary: string | null;
  readonly fieldError?: string;
}

export const VALIDATE_INVITE_TEMPORARY_ERROR =
  'Non è stato possibile verificare il codice. Riprova.';

const GENERIC_VALIDATION = 'Controlla i dati inseriti e riprova.';
const MALFORMED_REQUEST_MESSAGE =
  'La richiesta non è valida. Controlla i dati e riprova.';

function codeFieldMessage(code: string): string | null {
  switch (code) {
    case 'NotBlank':
      return 'Inserisci il codice invito.';
    case 'Size':
      return 'Il codice invito non può superare 100 caratteri.';
    default:
      return null;
  }
}

function validationPresentation(
  error: HttpApiError,
): ValidateInviteErrorPresentation {
  const responseFieldErrors = error.body?.fieldErrors;
  let fieldError: string | undefined;
  let hasUnmapped =
    responseFieldErrors === undefined || responseFieldErrors.length === 0;

  for (const entry of responseFieldErrors ?? []) {
    if (entry.field !== 'code') {
      hasUnmapped = true;
      continue;
    }

    const message = codeFieldMessage(entry.code);
    if (message === null) {
      hasUnmapped = true;
      continue;
    }

    if (fieldError === undefined) {
      fieldError = message;
    }
  }

  if (fieldError !== undefined && !hasUnmapped) {
    return {
      kind: 'invalid',
      summary: null,
      fieldError,
    };
  }

  return {
    kind: 'invalid',
    summary: GENERIC_VALIDATION,
    fieldError,
  };
}

/**
 * Maps validate-invite failures by ErrorResponse.code only.
 * Unknown / transport / anomalous outcomes stay temporary and fail-closed.
 */
export function getValidateInviteErrorPresentation(
  error: unknown,
): ValidateInviteErrorPresentation {
  if (
    !(error instanceof HttpApiError) ||
    error.status < 400 ||
    error.status >= 500
  ) {
    return {
      kind: 'temporary',
      summary: VALIDATE_INVITE_TEMPORARY_ERROR,
    };
  }

  switch (error.body?.code) {
    case 'VALIDATION_ERROR':
      return validationPresentation(error);
    case 'INVITE_CODE_NOT_FOUND':
      return {
        kind: 'invalid',
        summary: 'Codice invito non valido.',
      };
    case 'INVITE_CODE_NOT_ACTIVE':
      return {
        kind: 'invalid',
        summary: 'Questo codice invito non è disponibile.',
      };
    case 'INVITE_CODE_ALREADY_USED':
      return {
        kind: 'invalid',
        summary: 'Questo codice invito è già stato utilizzato.',
      };
    case 'INVITE_CODE_EXPIRED':
      return {
        kind: 'invalid',
        summary: 'Questo codice invito è scaduto.',
      };
    case 'MALFORMED_REQUEST':
      return {
        kind: 'invalid',
        summary: MALFORMED_REQUEST_MESSAGE,
      };
    case 'CSRF_VALIDATION_FAILED':
      return {
        kind: 'temporary',
        summary: VALIDATE_INVITE_TEMPORARY_ERROR,
      };
    default:
      return {
        kind: 'temporary',
        summary: VALIDATE_INVITE_TEMPORARY_ERROR,
      };
  }
}
