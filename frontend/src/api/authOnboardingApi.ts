import type {
  ConfirmEmailVerificationRequest,
  MessageResponse,
  RegisterProfessionalRequest,
  RegistrationAcceptedResponse,
  ResendEmailVerificationRequest,
} from './authTypes';
import { performCsrfMutation } from './csrfMutation';

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
