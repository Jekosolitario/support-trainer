import { afterEach, describe, expect, it, vi } from 'vitest';

import { registerClient, validateInviteCode } from './authOnboardingApi';
import { advanceEpoch, currentEpoch } from './authEpoch';
import { clearCsrf } from './csrf';
import { HttpApiError, NetworkError, UnexpectedResponseError } from './types';
import type { ErrorResponse } from './types';
import type { RegisterClientRequest } from './authTypes';

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
    timestamp: '2026-07-31T10:00:00Z',
    status,
    code,
    message: code,
    path,
  };

  return jsonResponse(body, { status });
}

const registerBody: RegisterClientRequest = {
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'ada@example.com',
  password: 'Password1!',
  inviteCode: 'INV-ABCDEF1234',
  birthDate: '1996-04-15',
  heightCm: 170,
  primaryGoal: 'Forza',
  gender: 'FEMALE',
};

describe('authOnboardingApi CLIENT', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  describe('validateInviteCode', () => {
    it('invia POST CSRF e richiede status 200 decodificato', async () => {
      const epochBefore = currentEpoch();
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          jsonResponse({
            valid: true,
            code: 'INV-ABCDEF1234',
            professionalId: 9,
            expiresAt: '2026-08-07T12:00:00.000Z',
          }),
        );
      globalThis.fetch = fetchMock;

      await expect(
        validateInviteCode({ code: '  inv-abcdef1234  ' }),
      ).resolves.toEqual({
        valid: true,
        code: 'INV-ABCDEF1234',
        professionalId: 9,
        expiresAt: '2026-08-07T12:00:00.000Z',
      });

      expect(currentEpoch()).toBe(epochBefore);
      expect(fetchMock.mock.calls[1]?.[0]).toBe(
        '/api/v1/auth/register/client/validate-invite',
      );
      expect(fetchMock.mock.calls[1]?.[1]).toEqual(
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ code: '  inv-abcdef1234  ' }),
          credentials: 'same-origin',
        }),
      );
      const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
      expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    });

    it.each([201, 202, 204])(
      'rifiuta status 2xx anomalo %s',
      async (status) => {
        globalThis.fetch = vi
          .fn()
          .mockResolvedValueOnce(csrfResponse())
          .mockResolvedValueOnce(
            status === 204
              ? new Response(null, { status })
              : jsonResponse({ valid: true }, { status }),
          );

        await expect(
          validateInviteCode({ code: 'INV-ABCDEF1234' }),
        ).rejects.toBeInstanceOf(UnexpectedResponseError);
      },
    );

    it('propaga HttpApiError per errori invite', async () => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          errorResponse(
            400,
            'INVITE_CODE_EXPIRED',
            '/api/v1/auth/register/client/validate-invite',
          ),
        );

      await expect(
        validateInviteCode({ code: 'INV-ABCDEF1234' }),
      ).rejects.toBeInstanceOf(HttpApiError);
    });

    it('fallisce closed su decoder failure a status 200', async () => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          jsonResponse({
            valid: true,
            code: 'INV-OTHER00001',
            professionalId: 1,
            expiresAt: '2026-08-07T12:00:00.000Z',
          }),
        );

      await expect(
        validateInviteCode({ code: 'INV-ABCDEF1234' }),
      ).rejects.toBeInstanceOf(UnexpectedResponseError);
    });

    it('fallisce closed su HTTP 200 con read_error del body', async () => {
      const readFailure = new TypeError('stream failed');
      const textMock = vi.fn().mockRejectedValue(readFailure);
      const response = new Response('payload', {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
      Object.defineProperty(response, 'text', {
        configurable: true,
        value: textMock,
      });

      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(response);
      globalThis.fetch = fetchMock;

      await expect(
        validateInviteCode({ code: 'INV-ABCDEF1234' }),
      ).rejects.toSatisfy((error: unknown) => {
        return (
          error instanceof UnexpectedResponseError &&
          error.status === 200 &&
          error.message.includes('not valid JSON')
        );
      });

      expect(textMock).toHaveBeenCalledTimes(1);
      const mutationCalls = fetchMock.mock.calls.filter(
        (call) => call[0] === '/api/v1/auth/register/client/validate-invite',
      );
      expect(mutationCalls).toHaveLength(1);
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('inoltra AbortSignal al fetch mutation e propaga AbortError senza retry', async () => {
      const controller = new AbortController();
      const fetchMock = vi.fn(
        async (input: RequestInfo | URL, init?: RequestInit) => {
          const url = String(input);
          if (url.includes('/auth/csrf')) {
            return csrfResponse();
          }

          if (url.includes('/auth/register/client/validate-invite')) {
            expect(init?.signal).toBe(controller.signal);
            return await new Promise<Response>((_resolve, reject) => {
              const signal = init?.signal;
              if (signal == null) {
                reject(new Error('missing signal'));
                return;
              }
              if (signal.aborted) {
                reject(
                  new DOMException('The operation was aborted.', 'AbortError'),
                );
                return;
              }
              signal.addEventListener(
                'abort',
                () => {
                  reject(
                    new DOMException(
                      'The operation was aborted.',
                      'AbortError',
                    ),
                  );
                },
                { once: true },
              );
            });
          }

          throw new Error(`Unexpected fetch: ${url}`);
        },
      );
      globalThis.fetch = fetchMock;

      const pending = validateInviteCode(
        { code: 'INV-ABORT00001' },
        { signal: controller.signal },
      );

      await vi.waitFor(() => {
        expect(
          fetchMock.mock.calls.some((call) =>
            String(call[0]).includes('/auth/register/client/validate-invite'),
          ),
        ).toBe(true);
      });

      controller.abort();

      await expect(pending).rejects.toSatisfy((error: unknown) => {
        return (
          error instanceof DOMException &&
          error.name === 'AbortError' &&
          !(error instanceof NetworkError)
        );
      });

      const mutationCalls = fetchMock.mock.calls.filter((call) =>
        String(call[0]).includes('/auth/register/client/validate-invite'),
      );
      expect(mutationCalls).toHaveLength(1);
      expect(mutationCalls[0]?.[1]?.signal).toBe(controller.signal);
    });
  });

  describe('registerClient', () => {
    it('una chiamata applicativa invia il payload completo sul percorso CSRF ordinario', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          jsonResponse({ message: 'neutro' }, { status: 202 }),
        );
      globalThis.fetch = fetchMock;

      await expect(registerClient(registerBody)).resolves.toEqual({
        kind: 'accepted',
      });

      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/register/client');
      expect(fetchMock.mock.calls[1]?.[1]).toEqual(
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(registerBody),
        }),
      );
    });

    it('omite i campi opzionali assenti dal payload', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(new Response('', { status: 202 }));
      globalThis.fetch = fetchMock;

      await registerClient(registerBody);

      const parsed = JSON.parse(
        String(fetchMock.mock.calls[1]?.[1]?.body),
      ) as Record<string, unknown>;
      expect(parsed).not.toHaveProperty('medicalNotes');
      expect(parsed).not.toHaveProperty('injuryNotes');
      expect(parsed).not.toHaveProperty('notes');
    });

    it('include i campi opzionali valorizzati', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(new Response('', { status: 202 }));
      globalThis.fetch = fetchMock;

      const withOptional: RegisterClientRequest = {
        ...registerBody,
        medicalNotes: 'Asma lieve',
        injuryNotes: 'Ginocchio',
        notes: 'Nota',
      };

      await registerClient(withOptional);

      expect(fetchMock.mock.calls[1]?.[1]?.body).toBe(
        JSON.stringify(withOptional),
      );
    });

    it('accetta 202 con body valido, vuoto o malformato', async () => {
      for (const response of [
        jsonResponse({ message: 'neutro' }, { status: 202 }),
        new Response('', { status: 202 }),
        new Response('{', {
          status: 202,
          headers: { 'Content-Type': 'application/json' },
        }),
      ]) {
        clearCsrf();
        globalThis.fetch = vi
          .fn()
          .mockResolvedValueOnce(csrfResponse())
          .mockResolvedValueOnce(response);

        await expect(registerClient(registerBody)).resolves.toEqual({
          kind: 'accepted',
        });
      }
    });

    it('accetta 202 quando la lettura reale del body produce read_error', async () => {
      const readFailure = new TypeError('body stream failed');
      const response = new Response(null, { status: 202 });
      const textSpy = vi.spyOn(response, 'text').mockRejectedValue(readFailure);
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(response);
      globalThis.fetch = fetchMock;

      await expect(registerClient(registerBody)).resolves.toEqual({
        kind: 'accepted',
      });

      expect(textSpy).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/register/client');
    });

    it('tratta 200 anomalo come ambiguous senza retry tecnico non-CSRF', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({ message: 'x' }, { status: 200 }));
      globalThis.fetch = fetchMock;

      const outcome = await registerClient(registerBody);
      expect(outcome.kind).toBe('ambiguous');
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('classifica 4xx allowlisted come known_failure', async () => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          errorResponse(
            400,
            'INVITE_CODE_ALREADY_USED',
            '/api/v1/auth/register/client',
          ),
        );

      const outcome = await registerClient(registerBody);
      expect(outcome.kind).toBe('known_failure');
      if (outcome.kind === 'known_failure') {
        expect(outcome.error.body?.code).toBe('INVITE_CODE_ALREADY_USED');
      }
    });

    it('classifica 5xx come ambiguous', async () => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(
          errorResponse(
            500,
            'INTERNAL_SERVER_ERROR',
            '/api/v1/auth/register/client',
          ),
        );

      await expect(registerClient(registerBody)).resolves.toMatchObject({
        kind: 'ambiguous',
      });
    });

    it('classifica transport failure come ambiguous', async () => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse())
        .mockRejectedValueOnce(new TypeError('offline'));

      const outcome = await registerClient(registerBody);
      expect(outcome.kind).toBe('ambiguous');
      if (outcome.kind === 'ambiguous') {
        expect(outcome.cause).toBeInstanceOf(NetworkError);
      }
    });
  });
});
