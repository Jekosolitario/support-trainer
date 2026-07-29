import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  confirmEmailVerification,
  registerProfessional,
  resendEmailVerification,
} from './authOnboardingApi';
import { advanceEpoch, currentEpoch } from './authEpoch';
import { clearCsrf } from './csrf';
import { HttpApiError, type ErrorResponse } from './types';

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
});
