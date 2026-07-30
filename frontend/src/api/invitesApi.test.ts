import { afterEach, describe, expect, it, vi } from 'vitest';

import { advanceEpoch } from './authEpoch';
import { clearCsrf, ensureCsrf } from './csrf';
import * as csrfMutation from './csrfMutation';
import { createInvite, listMyInvites } from './invitesApi';
import type { InviteCodeResponse } from './invitesTypes';
import { HttpApiError } from './types';

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

function csrfResponse(token: string, headerName: string): Response {
  return jsonResponse({ token, headerName });
}

function errorResponse(
  status: number,
  code: string,
  message: string,
): Response {
  return jsonResponse(
    {
      timestamp: '2026-07-30T10:00:00Z',
      status,
      code,
      message,
      path: '/api/v1/invites',
    },
    { status },
  );
}

const SAMPLE_INVITE: InviteCodeResponse = {
  id: 10,
  code: 'INV-ABCDEF1234',
  professionalId: 2,
  expiresAt: '2026-08-06T10:00:00.000000Z',
  used: false,
  usedAt: null,
  active: true,
  createdAt: '2026-07-30T10:00:00.000000Z',
};

describe('invitesApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('esegue GET /invites con credentials e AbortSignal opzionale', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([SAMPLE_INVITE]));
    globalThis.fetch = fetchMock;

    const response = await listMyInvites();

    expect(response).toEqual([SAMPLE_INVITE]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/invites');
    expect(fetchMock.mock.calls[0]?.[1]).toEqual(
      expect.objectContaining({
        credentials: 'same-origin',
      }),
    );
  });

  it('propaga errori HTTP su GET /invites', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(errorResponse(403, 'ACCESS_DENIED', 'Forbidden'));

    await expect(listMyInvites()).rejects.toBeInstanceOf(HttpApiError);
  });

  it('inoltra AbortSignal a GET /invites', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse([]));
    globalThis.fetch = fetchMock;

    await listMyInvites({ signal: controller.signal });

    expect(fetchMock.mock.calls[0]?.[1]).toEqual(
      expect.objectContaining({
        signal: controller.signal,
      }),
    );
  });

  it('esegue POST /invites senza body né Content-Type e senza invalidare CSRF', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('csrf-token', 'X-CSRF-TOKEN'))
      .mockResolvedValueOnce(jsonResponse(SAMPLE_INVITE, { status: 201 }));
    globalThis.fetch = fetchMock;

    const response = await createInvite();

    expect(response).toEqual(SAMPLE_INVITE);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/invites');
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
      }),
    );

    const requestInit = fetchMock.mock.calls[1]?.[1] as RequestInit;
    expect(requestInit.body).toBeUndefined();

    const headers = new Headers(requestInit.headers as HeadersInit);
    expect(headers.get('Content-Type')).toBeNull();
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');

    await expect(ensureCsrf()).resolves.toEqual({
      token: 'csrf-token',
      headerName: 'X-CSRF-TOKEN',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('usa performCsrfMutation con invalidateOn401 e invalidateCsrfOnCommit false', async () => {
    const mutationSpy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValueOnce(SAMPLE_INVITE);

    await createInvite();

    expect(mutationSpy).toHaveBeenCalledWith('/invites', {
      method: 'POST',
      invalidateOn401: true,
      invalidateCsrfOnCommit: false,
    });
  });

  it('propaga 409 INVITE_CODE_GENERATION_FAILED su create', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('csrf-token', 'X-CSRF-TOKEN'))
      .mockResolvedValueOnce(
        errorResponse(
          409,
          'INVITE_CODE_GENERATION_FAILED',
          'Generation failed',
        ),
      );

    await expect(createInvite()).rejects.toMatchObject({
      name: 'HttpApiError',
      status: 409,
      body: expect.objectContaining({
        code: 'INVITE_CODE_GENERATION_FAILED',
      }),
    });
  });
});
