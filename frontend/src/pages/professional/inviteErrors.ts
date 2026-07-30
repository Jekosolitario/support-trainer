import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
} from '../../api/types';

const GENERIC_LIST_ERROR =
  'Non è stato possibile caricare gli inviti. Riprova.';
const GENERATION_FAILED_ERROR = 'Generazione non riuscita. Riprova tra poco.';

export const CREATE_OUTCOME_UNCONFIRMED_MESSAGE =
  'Non è stato possibile confermare l’esito della generazione. Aggiorna l’elenco prima di creare un nuovo invito.';

export function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

export function getInviteListErrorMessage(error: unknown): string {
  if (
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError
  ) {
    return GENERIC_LIST_ERROR;
  }

  if (!(error instanceof HttpApiError)) {
    return GENERIC_LIST_ERROR;
  }

  switch (error.body?.code) {
    case 'ACCOUNT_NOT_ACTIVE':
      return 'L’account non è attivo. Non è possibile consultare gli inviti.';
    case 'EMAIL_NOT_VERIFIED':
      return 'L’email non risulta verificata. Completa la verifica per continuare.';
    case 'PROFESSIONAL_NOT_ACTIVE':
      return 'Il profilo professionista non è attivo.';
    case 'FORBIDDEN_OPERATION':
    case 'ACCESS_DENIED':
      return 'Non hai i permessi per consultare gli inviti.';
    default:
      return GENERIC_LIST_ERROR;
  }
}

/** Known HTTP create errors only — never StaleAuth / network ambiguity. */
export function getInviteCreateKnownErrorMessage(
  error: unknown,
): string | null {
  if (!(error instanceof HttpApiError)) {
    return null;
  }

  if (error.body?.code === 'INVITE_CODE_GENERATION_FAILED') {
    return GENERATION_FAILED_ERROR;
  }

  switch (error.body?.code) {
    case 'ACCOUNT_NOT_ACTIVE':
      return 'L’account non è attivo. Non è possibile generare inviti.';
    case 'EMAIL_NOT_VERIFIED':
      return 'L’email non risulta verificata. Completa la verifica per continuare.';
    case 'PROFESSIONAL_NOT_ACTIVE':
      return 'Il profilo professionista non è attivo.';
    case 'FORBIDDEN_OPERATION':
    case 'ACCESS_DENIED':
      return 'Non hai i permessi per generare inviti.';
    default:
      return 'Non è stato possibile generare l’invito. Riprova.';
  }
}

/**
 * Transport / ambiguous outcomes where the server may have created the invite.
 * StaleAuthOperationError is auth lifecycle, not ambiguous create.
 */
export function isAmbiguousCreateOutcome(error: unknown): boolean {
  if (error instanceof StaleAuthOperationError) {
    return false;
  }

  if (error instanceof HttpApiError) {
    return false;
  }

  if (isAbortError(error)) {
    return false;
  }

  if (error instanceof NetworkError) {
    return true;
  }

  if (error instanceof UnexpectedResponseError) {
    return true;
  }

  return true;
}

export function isStaleAuthCreateOutcome(error: unknown): boolean {
  return error instanceof StaleAuthOperationError;
}
