import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  confirmEmailVerification,
  confirmPasswordRecovery,
  registerProfessional,
  requestPasswordRecovery,
  resendEmailVerification,
} from './authOnboardingApi';
import { advanceEpoch, currentEpoch } from './authEpoch';
import { clearCsrf } from './csrf';
import {
  HttpApiError,
  UnexpectedResponseError,
  type ErrorResponse,
} from './types';

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

function csrfResponse(): Response {
  return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
}

function errorResponse(status: number, code: string, path: string): Response {
  const body: ErrorResponse = {
    timestamp: '2026-07-29T10:00:00Z',
    status,
    code,
    message: code,
    path,
  };

  return jsonResponse(body, { status });
}

describe('authOnboardingApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('esegue registerProfessional con CSRF senza avanzare l’epoch', async () => {
    const epochBefore = currentEpoch();
    const body = {
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      password: 'Password1!',
      specialization: 'PERSONAL_TRAINER' as const,
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse({ message: 'neutro' }, { status: 202 }),
      );
    globalThis.fetch = fetchMock;

    await expect(registerProfessional(body)).resolves.toEqual({
      message: 'neutro',
    });

    expect(currentEpoch()).toBe(epochBefore);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/auth/register/professional',
    );
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(body),
        credentials: 'same-origin',
      }),
    );
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('esegue confirmEmailVerification e propaga errori', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        errorResponse(
          410,
          'EMAIL_VERIFICATION_TOKEN_EXPIRED',
          '/api/v1/auth/email-verification/confirm',
        ),
      );
    globalThis.fetch = fetchMock;

    await expect(
      confirmEmailVerification({ token: 'token-value' }),
    ).rejects.toBeInstanceOf(HttpApiError);

    expect(currentEpoch()).toBe(epochBefore);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/auth/email-verification/confirm',
    );
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        body: JSON.stringify({ token: 'token-value' }),
      }),
    );
  });

  it('esegue resendEmailVerification con 202', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse({ message: 'altro neutro' }, { status: 202 }),
      );
    globalThis.fetch = fetchMock;

    await expect(
      resendEmailVerification({ email: 'ada@example.com' }),
    ).resolves.toEqual({ message: 'altro neutro' });

    expect(currentEpoch()).toBe(epochBefore);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/auth/email-verification/resend',
    );
  });

  it('esegue requestPasswordRecovery con CSRF e accetta 202', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse(
          {
            message:
              'Se esiste un account associato a questa email, riceverai le istruzioni per reimpostare la password.',
          },
          { status: 202 },
        ),
      );
    globalThis.fetch = fetchMock;

    await expect(
      requestPasswordRecovery({ email: 'ada@example.com' }),
    ).resolves.toEqual({
      message:
        'Se esiste un account associato a questa email, riceverai le istruzioni per reimpostare la password.',
    });

    expect(currentEpoch()).toBe(epochBefore);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/auth/password-recovery/request',
    );
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'ada@example.com' }),
        credentials: 'same-origin',
      }),
    );
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
  });

  it('propaga VALIDATION_ERROR su requestPasswordRecovery', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        errorResponse(
          400,
          'VALIDATION_ERROR',
          '/api/v1/auth/password-recovery/request',
        ),
      );
    globalThis.fetch = fetchMock;

    await expect(
      requestPasswordRecovery({ email: 'not-an-email' }),
    ).rejects.toBeInstanceOf(HttpApiError);
  });

  it('propaga errori tecnici su requestPasswordRecovery', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockRejectedValueOnce(new TypeError('Failed to fetch'));
    globalThis.fetch = fetchMock;

    await expect(
      requestPasswordRecovery({ email: 'ada@example.com' }),
    ).rejects.toThrow();
  });

  it('esegue confirmPasswordRecovery con token e newPassword e accetta 204', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    globalThis.fetch = fetchMock;

    await expect(
      confirmPasswordRecovery({
        token: 'raw-token',
        newPassword: 'Password1!',
      }),
    ).resolves.toBeUndefined();

    expect(currentEpoch()).toBe(epochBefore);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/auth/password-recovery/confirm',
    );
    const body = String(fetchMock.mock.calls[1]?.[1]?.body);
    expect(body).toBe(
      JSON.stringify({ token: 'raw-token', newPassword: 'Password1!' }),
    );
    expect(body).not.toContain('confirmPassword');
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
  });

  it('propaga PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED su confirm', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        errorResponse(
          400,
          'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED',
          '/api/v1/auth/password-recovery/confirm',
        ),
      );
    globalThis.fetch = fetchMock;

    await expect(
      confirmPasswordRecovery({
        token: 'expired',
        newPassword: 'Password1!',
      }),
    ).rejects.toMatchObject({
      body: { code: 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED' },
    });
  });

  it.each([
    [200, 'VALIDATION_ERROR'],
    [201, 'VALIDATION_ERROR'],
  ] as const)(
    'rifiuta requestPasswordRecovery su HTTP %s con body ErrorResponse strutturato',
    async (status, code) => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          errorResponse(status, code, '/api/v1/auth/password-recovery/request'),
        );
      globalThis.fetch = fetchMock;

      const rejection = await requestPasswordRecovery({
        email: 'ada@example.com',
      }).then(
        () => null,
        (error: unknown) => error,
      );

      expect(rejection).toBeInstanceOf(UnexpectedResponseError);
      expect(rejection).not.toBeInstanceOf(HttpApiError);
    },
  );

  it('rifiuta requestPasswordRecovery su HTTP 204 inatteso senza body', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    globalThis.fetch = fetchMock;

    await expect(
      requestPasswordRecovery({ email: 'ada@example.com' }),
    ).rejects.toBeInstanceOf(UnexpectedResponseError);
  });

  it.each([
    [200, 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED'],
    [201, 'VALIDATION_ERROR'],
    [202, 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED'],
  ] as const)(
    'rifiuta confirmPasswordRecovery su HTTP %s con body ErrorResponse strutturato',
    async (status, code) => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          errorResponse(status, code, '/api/v1/auth/password-recovery/confirm'),
        );
      globalThis.fetch = fetchMock;

      const rejection = await confirmPasswordRecovery({
        token: 'raw-token',
        newPassword: 'Password1!',
      }).then(
        () => null,
        (error: unknown) => error,
      );

      expect(rejection).toBeInstanceOf(UnexpectedResponseError);
      expect(rejection).not.toBeInstanceOf(HttpApiError);
    },
  );

  it('propaga VALIDATION_ERROR su confirmPasswordRecovery', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        errorResponse(
          400,
          'VALIDATION_ERROR',
          '/api/v1/auth/password-recovery/confirm',
        ),
      );
    globalThis.fetch = fetchMock;

    await expect(
      confirmPasswordRecovery({
        token: 'raw-token',
        newPassword: 'weak',
      }),
    ).rejects.toMatchObject({ body: { code: 'VALIDATION_ERROR' } });
  });
});
