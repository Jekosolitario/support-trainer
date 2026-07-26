import { afterEach, describe, expect, it, vi } from 'vitest';

import { advanceEpoch } from './authEpoch';
import { clearCsrf, ensureCsrf } from './csrf';
import { performCsrfMutation, StaleAuthOperationError } from './csrfMutation';
import { HttpApiError, NetworkError, type ErrorResponse } from './types';

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

function noContentResponse(): Response {
  return new Response(null, { status: 204 });
}

function errorResponse(status: number, code: string): Response {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message: code,
    path: '/api/v1/test-mutation',
  };

  return jsonResponse(body, { status });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}

async function flushUntil(predicate: () => boolean): Promise<void> {
  for (let attempt = 0; attempt < 25; attempt += 1) {
    if (predicate()) {
      return;
    }

    await Promise.resolve();
  }

  expect(predicate()).toBe(true);
}

describe('performCsrfMutation', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('usa un header CSRF dinamico senza mutare o perdere gli header del caller', async () => {
    const callerHeaders = new Headers({
      'X-Custom': 'preserved',
      'X-CSRF-A': 'caller-value',
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    await performCsrfMutation<void>('/test-mutation', {
      method: 'POST',
      headers: callerHeaders,
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const mutationHeaders = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(mutationHeaders.get('X-Custom')).toBe('preserved');
    expect(mutationHeaders.get('X-CSRF-A')).toBe('token-0');
    expect(callerHeaders.get('X-CSRF-A')).toBe('caller-value');
  });

  it('non invia la mutation se epoch cambia mentre ensure iniziale è pending', async () => {
    const csrfGate = deferred<Response>();
    const fetchMock = vi.fn().mockReturnValue(csrfGate.promise);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    advanceEpoch();
    csrfGate.resolve(csrfResponse('token-0', 'X-CSRF-A'));

    await expect(mutation).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rende stale una mutation la cui response arriva dopo un cambio epoch', async () => {
    const mutationGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(mutationGate.promise);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    await flushUntil(() => fetchMock.mock.calls.length === 2);

    advanceEpoch();
    mutationGate.resolve(noContentResponse());

    await expect(mutation).rejects.toBeInstanceOf(StaleAuthOperationError);
  });

  it('ruota T0 e replaya una volta con token e headerName T1', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    await performCsrfMutation<void>('/test-mutation', {
      method: 'POST',
    });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const firstHeaders = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    const replayHeaders = new Headers(fetchMock.mock.calls[3]?.[1]?.headers);
    expect(firstHeaders.get('X-CSRF-A')).toBe('token-0');
    expect(replayHeaders.get('X-CSRF-A')).toBeNull();
    expect(replayHeaders.get('X-CSRF-B')).toBe('token-1');
  });

  it('propaga il secondo CSRF failure senza un ulteriore retry', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'));
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });

    await expect(mutation).rejects.toMatchObject({
      status: 403,
      body: { code: 'CSRF_VALIDATION_FAILED' },
    });
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it.each([
    [403, 'ACCESS_DENIED'],
    [401, 'UNAUTHORIZED'],
  ])('non ritenta errori HTTP non-CSRF (%s %s)', async (status, code) => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(status, code));
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });

    await expect(mutation).rejects.toBeInstanceOf(HttpApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('non ritenta errori network', async () => {
    const networkCause = new Error('offline');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockRejectedValueOnce(networkCause);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });

    await expect(mutation).rejects.toMatchObject({
      name: 'NetworkError',
      cause: networkCause,
    });
    await expect(mutation).rejects.toBeInstanceOf(NetworkError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('non ritenta AbortError e ne preserva l’identità', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockRejectedValueOnce(abortError);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });

    await expect(mutation).rejects.toBe(abortError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('non ruota o replaya se epoch cambia prima della CAS', async () => {
    const mutationGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(mutationGate.promise);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    await flushUntil(() => fetchMock.mock.calls.length === 2);

    advanceEpoch();
    mutationGate.resolve(errorResponse(403, 'CSRF_VALIDATION_FAILED'));

    await expect(mutation).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('non replaya se epoch cambia durante ensure T1', async () => {
    const refreshGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'))
      .mockReturnValueOnce(refreshGate.promise);
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    await flushUntil(() => fetchMock.mock.calls.length === 3);

    advanceEpoch();
    refreshGate.resolve(csrfResponse('token-1', 'X-CSRF-B'));

    await expect(mutation).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('condivide un solo T1 quando due mutation falliscono con lo stesso T0', async () => {
    const firstMutationGate = deferred<Response>();
    const secondMutationGate = deferred<Response>();
    const refreshGate = deferred<Response>();
    let mutationCount = 0;
    let csrfCount = 0;

    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (String(input) === '/api/v1/auth/csrf') {
        csrfCount += 1;
        return csrfCount === 1
          ? Promise.resolve(csrfResponse('token-0', 'X-CSRF-A'))
          : refreshGate.promise;
      }

      mutationCount += 1;
      if (mutationCount === 1) {
        return firstMutationGate.promise;
      }
      if (mutationCount === 2) {
        return secondMutationGate.promise;
      }

      return Promise.resolve(noContentResponse());
    });
    globalThis.fetch = fetchMock;

    await ensureCsrf();
    const first = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    const second = performCsrfMutation('/test-mutation', {
      method: 'POST',
    });
    await flushUntil(() => mutationCount === 2);

    firstMutationGate.resolve(errorResponse(403, 'CSRF_VALIDATION_FAILED'));
    await flushUntil(() => csrfCount === 2);
    secondMutationGate.resolve(errorResponse(403, 'CSRF_VALIDATION_FAILED'));
    await Promise.resolve();

    expect(csrfCount).toBe(2);
    refreshGate.resolve(csrfResponse('token-1', 'X-CSRF-B'));
    await Promise.all([first, second]);

    expect(csrfCount).toBe(2);
    expect(mutationCount).toBe(4);
    for (const replayCall of fetchMock.mock.calls.slice(4)) {
      const replayHeaders = new Headers(replayCall[1]?.headers);
      expect(replayHeaders.get('X-CSRF-A')).toBeNull();
      expect(replayHeaders.get('X-CSRF-B')).toBe('token-1');
    }
  });

  it('invalida CSRF dopo commit riuscito', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(noContentResponse())
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'));
    globalThis.fetch = fetchMock;

    await performCsrfMutation<void>('/test-mutation', {
      method: 'POST',
      invalidateCsrfOnCommit: true,
    });
    const refreshed = await ensureCsrf();

    expect(refreshed.token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('invalida CSRF anche se un successo committed è diventato stale', async () => {
    const mutationGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(mutationGate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'));
    globalThis.fetch = fetchMock;

    const mutation = performCsrfMutation('/test-mutation', {
      method: 'POST',
      invalidateCsrfOnCommit: true,
    });
    await flushUntil(() => fetchMock.mock.calls.length === 2);

    advanceEpoch();
    mutationGate.resolve(noContentResponse());

    await expect(mutation).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
