import { StaleAuthOperationError } from '../api/csrfMutation';
import type { ObservedHttpResponse } from '../api/httpClient';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  isErrorResponse,
  type ErrorResponse,
} from '../api/types';

/**
 * Explicit status+code pairs demonstrated by the backend for register CLIENT.
 * Classification never trusts code alone.
 */
export const CLIENT_REGISTER_KNOWN_FAILURES = [
  { status: 400, code: 'VALIDATION_ERROR' },
  { status: 400, code: 'MALFORMED_REQUEST' },
  { status: 404, code: 'INVITE_CODE_NOT_FOUND' },
  { status: 400, code: 'INVITE_CODE_NOT_ACTIVE' },
  { status: 400, code: 'INVITE_CODE_ALREADY_USED' },
  { status: 400, code: 'INVITE_CODE_EXPIRED' },
  { status: 403, code: 'ACCOUNT_NOT_ACTIVE' },
  { status: 403, code: 'EMAIL_NOT_VERIFIED' },
  { status: 403, code: 'PROFESSIONAL_NOT_ACTIVE' },
  { status: 403, code: 'CSRF_VALIDATION_FAILED' },
] as const;

export type ClientRegisterKnownFailure =
  (typeof CLIENT_REGISTER_KNOWN_FAILURES)[number];

const KNOWN_FAILURE_PAIR_KEYS = new Set(
  CLIENT_REGISTER_KNOWN_FAILURES.map(
    (entry) => `${String(entry.status)}:${entry.code}`,
  ),
);

export type RegisterClientOutcome =
  | { readonly kind: 'accepted' }
  | { readonly kind: 'known_failure'; readonly error: HttpApiError }
  | { readonly kind: 'ambiguous'; readonly cause: unknown };

function errorBodyFromObservation(
  observed: ObservedHttpResponse,
): ErrorResponse | null {
  if (observed.body.kind !== 'json') {
    return null;
  }

  return isErrorResponse(observed.body.value) ? observed.body.value : null;
}

function isKnownFailurePair(status: number, code: string): boolean {
  return KNOWN_FAILURE_PAIR_KEYS.has(`${String(status)}:${code}`);
}

/**
 * Known only when HTTP status and ErrorResponse.code match an allowlisted pair
 * and body.status (when present) agrees with the HTTP status.
 */
export function isClientRegisterKnownFailure(
  status: number,
  body: ErrorResponse | null | undefined,
): boolean {
  if (body === null || body === undefined) {
    return false;
  }

  if (status >= 500) {
    return false;
  }

  if (body.status !== status) {
    return false;
  }

  return isKnownFailurePair(status, body.code);
}

/**
 * Classifies an observed register CLIENT HTTP exchange.
 * Exact HTTP 202 → accepted (body irrelevant).
 * Allowlisted status+code pairs → known_failure.
 * Everything else → ambiguous.
 */
export function classifyRegisterClientObservation(
  observed: ObservedHttpResponse,
): RegisterClientOutcome {
  if (observed.status === 202) {
    return { kind: 'accepted' };
  }

  if (observed.status >= 200 && observed.status < 300) {
    return {
      kind: 'ambiguous',
      cause: new UnexpectedResponseError(
        observed.status,
        observed.response,
        observed.body,
        `Register client expected HTTP 202, received ${String(observed.status)}`,
      ),
    };
  }

  if (observed.status >= 500) {
    return {
      kind: 'ambiguous',
      cause: new UnexpectedResponseError(
        observed.status,
        observed.response,
        observed.body,
        `Register client received server error status ${String(observed.status)}`,
      ),
    };
  }

  const errorBody = errorBodyFromObservation(observed);
  if (isClientRegisterKnownFailure(observed.status, errorBody)) {
    return {
      kind: 'known_failure',
      error: new HttpApiError(observed.status, errorBody, observed.response),
    };
  }

  return {
    kind: 'ambiguous',
    cause:
      errorBody !== null
        ? new HttpApiError(observed.status, errorBody, observed.response)
        : new UnexpectedResponseError(
            observed.status,
            observed.response,
            observed.body,
            'Register client response could not be classified as a known failure',
          ),
  };
}

/**
 * Classifies thrown failures around register CLIENT.
 * Conservative: StaleAuth / network / unexpected / unknown → ambiguous.
 * HttpApiError is known only for allowlisted status+code pairs.
 */
export function classifyRegisterClientThrown(
  error: unknown,
): RegisterClientOutcome {
  if (
    error instanceof HttpApiError &&
    isClientRegisterKnownFailure(error.status, error.body)
  ) {
    return { kind: 'known_failure', error };
  }

  if (
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError ||
    error instanceof StaleAuthOperationError
  ) {
    return { kind: 'ambiguous', cause: error };
  }

  return { kind: 'ambiguous', cause: error };
}
