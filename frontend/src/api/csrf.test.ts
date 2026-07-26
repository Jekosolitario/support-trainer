import { afterEach, describe, expect, it, vi } from 'vitest';

import { currentEpoch } from './authEpoch';
import {
  clearCsrf,
  ensureCsrf,
  InvalidCsrfResponseError,
  invalidateCsrfIfCurrent,
} from './csrf';
import { subscribe } from './sessionInvalidation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
} from './types';

interface ControlledResponse {
  promise: Promise<Response>;
  resolve: (response: Response) => void;
  reject: (reason: unknown) => void;
  isSettled: () => boolean;
}

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

function httpErrorResponse(status: number): Response {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code: status === 401 ? 'UNAUTHORIZED' : 'SERVER_ERROR',
    message: 'Request failed',
    path: '/api/v1/auth/csrf',
  };

  return jsonResponse(body, { status });
}

describe('csrf manager', () => {
  const originalFetch = globalThis.fetch;
  const controlledResponses: ControlledResponse[] = [];
  const unsubscribers: Array<() => void> = [];

  function controlledResponse(): ControlledResponse {
    let settled = false;
    let resolvePromise!: (response: Response) => void;
    let rejectPromise!: (reason: unknown) => void;

    const promise = new Promise<Response>((resolve, reject) => {
      resolvePromise = resolve;
      rejectPromise = reject;
    });

    const controlled: ControlledResponse = {
      promise,
      resolve: (response) => {
        settled = true;
        resolvePromise(response);
      },
      reject: (reason) => {
        settled = true;
        rejectPromise(reason);
      },
      isSettled: () => settled,
    };

    controlledResponses.push(controlled);
    return controlled;
  }

  function trackSubscribe(callback: () => void): void {
    unsubscribers.push(subscribe(callback));
  }

  afterEach(async () => {
    const unresolved = controlledResponses.filter(
      (controlled) => !controlled.isSettled(),
    );

    for (const controlled of unresolved) {
      controlled.reject(new Error('Unresolved controlled fetch in test'));
    }

    await Promise.allSettled(
      controlledResponses.map((controlled) => controlled.promise),
    );
    controlledResponses.length = 0;

    while (unsubscribers.length > 0) {
      unsubscribers.pop()?.();
    }

    clearCsrf();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();

    expect(unresolved).toHaveLength(0);
  });

  it('fetches, caches and preserves an immutable dynamic header snapshot', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(csrfResponse('token-0', 'X-DYNAMIC-CSRF'));
    globalThis.fetch = fetchMock;

    const first = await ensureCsrf();
    const cached = await ensureCsrf();

    expect(first).toEqual({
      token: 'token-0',
      headerName: 'X-DYNAMIC-CSRF',
    });
    expect(Object.isFrozen(first)).toBe(true);
    expect(cached).toBe(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/csrf',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
  });

  it('shares the exact promise for concurrent ensures in one generation', async () => {
    const gate = controlledResponse();
    const fetchMock = vi.fn().mockReturnValue(gate.promise);
    globalThis.fetch = fetchMock;

    const first = ensureCsrf();
    const second = ensureCsrf();

    expect(second).toBe(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    gate.resolve(csrfResponse('token-0', 'X-CSRF-0'));

    const [firstValue, secondValue] = await Promise.all([first, second]);
    expect(secondValue).toBe(firstValue);
  });

  it('clear invalidates the cache and the next ensure fetches T1', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-0'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    const token0 = await ensureCsrf();
    clearCsrf();
    const token1 = await ensureCsrf();
    const cached = await ensureCsrf();

    expect(token1).not.toBe(token0);
    expect(token1.token).toBe('token-1');
    expect(cached).toBe(token1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not recache a response made stale while ensure is pending', async () => {
    const token0Gate = controlledResponse();
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(token0Gate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    const stalePromise = ensureCsrf();
    clearCsrf();

    token0Gate.resolve(csrfResponse('token-0', 'X-CSRF-0'));
    const staleValue = await stalePromise;
    const currentValue = await ensureCsrf();

    expect(staleValue.token).toBe('token-0');
    expect(currentValue.token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('keeps T1 cached when T1 resolves before stale T0', async () => {
    const token0Gate = controlledResponse();
    const token1Gate = controlledResponse();
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(token0Gate.promise)
      .mockReturnValueOnce(token1Gate.promise);
    globalThis.fetch = fetchMock;

    const token0Promise = ensureCsrf();
    clearCsrf();
    const token1Promise = ensureCsrf();

    token1Gate.resolve(csrfResponse('token-1', 'X-CSRF-1'));
    const token1 = await token1Promise;

    token0Gate.resolve(csrfResponse('token-0', 'X-CSRF-0'));
    await token0Promise;

    expect(await ensureCsrf()).toBe(token1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('prevents stale finally from clearing the current in-flight refresh', async () => {
    const token0Gate = controlledResponse();
    const token1Gate = controlledResponse();
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(token0Gate.promise)
      .mockReturnValueOnce(token1Gate.promise);
    globalThis.fetch = fetchMock;

    const token0Promise = ensureCsrf();
    clearCsrf();
    const token1Promise = ensureCsrf();

    token0Gate.resolve(csrfResponse('token-0', 'X-CSRF-0'));
    await token0Promise;

    const sharedToken1Promise = ensureCsrf();
    expect(sharedToken1Promise).toBe(token1Promise);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    token1Gate.resolve(csrfResponse('token-1', 'X-CSRF-1'));
    await token1Promise;
  });

  it('invalidates only the exact current snapshot', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-0'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    const token0 = await ensureCsrf();

    expect(invalidateCsrfIfCurrent({ ...token0 })).toBe(false);
    expect(invalidateCsrfIfCurrent(token0)).toBe(true);

    const token1 = await ensureCsrf();
    expect(token1.token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not invalidate the current cache with a stale snapshot', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-0'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    const token0 = await ensureCsrf();
    clearCsrf();
    const token1 = await ensureCsrf();

    expect(invalidateCsrfIfCurrent(token0)).toBe(false);
    expect(await ensureCsrf()).toBe(token1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('allows only one of two T0 invalidations and single-flights T1', async () => {
    const token1Gate = controlledResponse();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-0'))
      .mockReturnValueOnce(token1Gate.promise);
    globalThis.fetch = fetchMock;

    const token0 = await ensureCsrf();

    expect(invalidateCsrfIfCurrent(token0)).toBe(true);
    expect(invalidateCsrfIfCurrent(token0)).toBe(false);

    const firstRefresh = ensureCsrf();
    const secondRefresh = ensureCsrf();

    expect(secondRefresh).toBe(firstRefresh);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    token1Gate.resolve(csrfResponse('token-1', 'X-CSRF-1'));
    await Promise.all([firstRefresh, secondRefresh]);
  });

  it('prevents a late T0 failure from invalidating cached T1', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-0'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    const token0 = await ensureCsrf();
    expect(invalidateCsrfIfCurrent(token0)).toBe(true);

    const token1 = await ensureCsrf();

    expect(invalidateCsrfIfCurrent(token0)).toBe(false);
    expect(await ensureCsrf()).toBe(token1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('propagates NetworkError without corrupting the cache', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    await expect(ensureCsrf()).rejects.toBeInstanceOf(NetworkError);

    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('preserves AbortError identity without corrupting the cache', async () => {
    const abortError = new DOMException('Request aborted', 'AbortError');
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(abortError)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    await expect(ensureCsrf()).rejects.toBe(abortError);

    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('preserves UnexpectedResponseError for syntactically invalid JSON', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response('{', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    await expect(ensureCsrf()).rejects.toBeInstanceOf(UnexpectedResponseError);

    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('classifies an empty successful body as InvalidCsrfResponseError', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 200 }));

    await expect(ensureCsrf()).rejects.toBeInstanceOf(InvalidCsrfResponseError);
  });

  it.each([
    ['missing token', { headerName: 'X-CSRF' }],
    ['missing headerName', { token: 'token-0' }],
    ['non-string token', { token: 123, headerName: 'X-CSRF' }],
    ['non-string headerName', { token: 'token-0', headerName: 123 }],
    ['blank token', { token: '   ', headerName: 'X-CSRF' }],
    ['blank headerName', { token: 'token-0', headerName: '   ' }],
  ])('rejects semantic CSRF payload error: %s', async (_label, payload) => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(payload));

    await expect(ensureCsrf()).rejects.toBeInstanceOf(InvalidCsrfResponseError);
  });

  it('propagates 401 without advancing auth epoch or notifying subscribers', async () => {
    const onSessionInvalidated = vi.fn();
    trackSubscribe(onSessionInvalidated);
    const authEpochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(httpErrorResponse(401))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
    globalThis.fetch = fetchMock;

    await expect(ensureCsrf()).rejects.toMatchObject({
      name: 'HttpApiError',
      status: 401,
    });

    expect(currentEpoch()).toBe(authEpochBefore);
    expect(onSessionInvalidated).not.toHaveBeenCalled();
    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each([403, 500])(
    'propagates HTTP %i without caching the failure',
    async (status) => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(httpErrorResponse(status))
        .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-1'));
      globalThis.fetch = fetchMock;

      await expect(ensureCsrf()).rejects.toBeInstanceOf(HttpApiError);

      expect((await ensureCsrf()).token).toBe('token-1');
      expect(fetchMock).toHaveBeenCalledTimes(2);
    },
  );
});
