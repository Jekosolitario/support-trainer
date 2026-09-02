import {
  classifyRegisterClientObservation,
  classifyRegisterClientThrown,
  type RegisterClientOutcome,
} from '../auth/clientOnboardingOutcome';
import { canonicalizeInviteCode } from '../auth/inviteCode';
import { decodeValidateInviteCodeResponse } from '../auth/validateInviteCodeResponse';
import type {
  ConfirmEmailVerificationRequest,
  MessageResponse,
  PasswordRecoveryAcceptedResponse,
  PasswordRecoveryConfirmRequest,
  PasswordRecoveryRequest,
  RegisterClientRequest,
  RegisterProfessionalRequest,
  RegistrationAcceptedResponse,
  ResendEmailVerificationRequest,
  ValidateInviteCodeRequest,
  ValidateInviteCodeResponse,
} from './authTypes';
import {
  performCsrfMutation,
  performCsrfObservedMutation,
} from './csrfMutation';
import type { ObservedHttpResponse } from './httpClient';
import {
  HttpApiError,
  UnexpectedResponseError,
  isErrorResponse,
} from './types';

export function registerProfessional(
  body: RegisterProfessionalRequest,
): Promise<RegistrationAcceptedResponse> {
  return performCsrfMutation<RegistrationAcceptedResponse>(
    '/auth/register/professional',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );
}

function httpApiErrorFromObservation(
  observed: ObservedHttpResponse,
): HttpApiError | null {
  if (observed.body.kind !== 'json' || !isErrorResponse(observed.body.value)) {
    return null;
  }

  return new HttpApiError(
    observed.status,
    observed.body.value,
    observed.response,
  );
}

function throwForNonExactValidateStatus(observed: ObservedHttpResponse): never {
  const apiError = httpApiErrorFromObservation(observed);
  if (apiError !== null) {
    throw apiError;
  }

  throw new UnexpectedResponseError(
    observed.status,
    observed.response,
    observed.body,
    `Validate invite expected HTTP 200, received ${String(observed.status)}`,
  );
}

function throwForNonExactPasswordRecoveryStatus(
  observed: ObservedHttpResponse,
  expectedStatus: number,
): never {
  if (observed.ok) {
    throw new UnexpectedResponseError(
      observed.status,
      observed.response,
      observed.body,
      `Password recovery expected HTTP ${String(expectedStatus)}, received ${String(observed.status)}`,
    );
  }

  const apiError = httpApiErrorFromObservation(observed);
  if (apiError !== null) {
    throw apiError;
  }

  throw new UnexpectedResponseError(
    observed.status,
    observed.response,
    observed.body,
    `Password recovery expected HTTP ${String(expectedStatus)}, received ${String(observed.status)}`,
  );
}

function decodePasswordRecoveryAccepted(
  observed: ObservedHttpResponse,
): PasswordRecoveryAcceptedResponse {
  if (observed.body.kind !== 'json' || observed.body.value === null) {
    return { message: '' };
  }

  const value = observed.body.value;
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return { message: '' };
  }

  if (!('message' in value) || typeof value.message !== 'string') {
    return { message: '' };
  }

  return { message: value.message };
}

/**
 * Public validate-invite mutation.
 * Success only on exact HTTP 200 with a runtime-decoded body whose code matches
 * the canonical request code.
 */
export async function validateInviteCode(
  body: ValidateInviteCodeRequest,
  options: { readonly signal?: AbortSignal } = {},
): Promise<ValidateInviteCodeResponse> {
  const canonicalCode = canonicalizeInviteCode(body.code);
  const observed = await performCsrfObservedMutation(
    '/auth/register/client/validate-invite',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ code: body.code }),
      signal: options.signal,
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );

  if (observed.status !== 200) {
    throwForNonExactValidateStatus(observed);
  }

  if (observed.body.kind === 'empty') {
    throw new UnexpectedResponseError(
      observed.status,
      observed.response,
      observed.body,
      'Validate invite response body was empty',
    );
  }

  if (observed.body.kind !== 'json') {
    throw new UnexpectedResponseError(
      observed.status,
      observed.response,
      observed.body,
      'Validate invite response body was not valid JSON',
    );
  }

  try {
    return decodeValidateInviteCodeResponse(observed.body.value, canonicalCode);
  } catch (cause) {
    throw new UnexpectedResponseError(
      observed.status,
      observed.response,
      cause,
      'Validate invite response body failed runtime decoding',
    );
  }
}

/**
 * Public register-client mutation.
 * Exact HTTP 202 → accepted (body ignored). Issues one application-level
 * registration mutation per invocation. The shared CSRF layer may perform one
 * technical POST replay after 403 CSRF_VALIDATION_FAILED; no application-level
 * retry is introduced here.
 */
export async function registerClient(
  body: RegisterClientRequest,
): Promise<RegisterClientOutcome> {
  try {
    const observed = await performCsrfObservedMutation(
      '/auth/register/client',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
        invalidateOn401: false,
        invalidateCsrfOnCommit: false,
      },
    );

    return classifyRegisterClientObservation(observed);
  } catch (error) {
    return classifyRegisterClientThrown(error);
  }
}

export function confirmEmailVerification(
  body: ConfirmEmailVerificationRequest,
): Promise<MessageResponse> {
  return performCsrfMutation<MessageResponse>(
    '/auth/email-verification/confirm',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );
}

export function resendEmailVerification(
  body: ResendEmailVerificationRequest,
): Promise<MessageResponse> {
  return performCsrfMutation<MessageResponse>(
    '/auth/email-verification/resend',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );
}

/**
 * Public password-recovery request. Success only on exact HTTP 202.
 * Other 2xx statuses are unexpected technical responses, not generic success.
 */
export async function requestPasswordRecovery(
  body: PasswordRecoveryRequest,
): Promise<PasswordRecoveryAcceptedResponse> {
  const observed = await performCsrfObservedMutation(
    '/auth/password-recovery/request',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );

  if (observed.status !== 202) {
    throwForNonExactPasswordRecoveryStatus(observed, 202);
  }

  return decodePasswordRecoveryAccepted(observed);
}

/**
 * Public password-recovery confirm. Success only on exact HTTP 204.
 * Other 2xx statuses are unexpected technical responses, not password-updated.
 */
export async function confirmPasswordRecovery(
  body: PasswordRecoveryConfirmRequest,
): Promise<void> {
  const observed = await performCsrfObservedMutation(
    '/auth/password-recovery/confirm',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: false,
      invalidateCsrfOnCommit: false,
    },
  );

  if (observed.status !== 204) {
    throwForNonExactPasswordRecoveryStatus(observed, 204);
  }
}
