import { afterEach, describe, expect, it, vi } from 'vitest';

import { advanceEpoch, currentEpoch } from './authEpoch';
import { observeHttpRequest, request } from './httpClient';
import { subscribe } from './sessionInvalidation';
import {
  HttpApiError,
  NetworkError,
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

function errorResponse(body: ErrorResponse, status = body.status): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('httpClient', () => {
  const unsubscribers: Array<() => void> = [];
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();

    while (unsubscribers.length > 0) {
      unsubscribers.pop()?.();
    }

    // Stale any in-flight work tied to the previous epoch without resetting to zero.
    advanceEpoch();
  });

  function trackSubscribe(callback: () => void) {
    const unsubscribe = subscribe(callback);
    unsubscribers.push(unsubscribe);
    return unsubscribe;
  }

  it('parsa una risposta JSON valida', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 1, email: 'a@example.com' }));
    globalThis.fetch = fetchMock;

    const result = await request<{ id: number; email: string }>(
      '/api/v1/me/account',
    );

    expect(result).toEqual({ id: 1, email: 'a@example.com' });
  });

  it('gestisce 204 senza tentare il parsing del body', async () => {
    const textSpy = vi.fn();
    const response = new Response(null, { status: 204 });
    Object.defineProperty(response, 'text', {
      value: textSpy,
    });

    globalThis.fetch = vi.fn().mockResolvedValue(response);

    const result = await request<void>('/api/v1/auth/login', {
      method: 'POST',
    });

    expect(result).toBeUndefined();
    expect(textSpy).not.toHaveBeenCalled();
  });

  it('parsa un ErrorResponse valido come HttpApiError', async () => {
    const body: ErrorResponse = {
      timestamp: '2026-07-26T10:00:00Z',
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Utente non autenticato',
      path: '/api/v1/me/account',
    };

    globalThis.fetch = vi.fn().mockResolvedValue(errorResponse(body));

    try {
      await request('/api/v1/me/account');
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({
        status: 401,
        body: { code: 'UNAUTHORIZED' },
      });
    }
  });

  it('mantiene un VALIDATION_ERROR con field null come HttpApiError', async () => {
    const body: ErrorResponse = {
      timestamp: '2026-07-26T10:00:00Z',
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Richiesta non valida',
      path: '/api/v1/auth/login',
      fieldErrors: [
        {
          field: null,
          code: 'Valid',
          message: 'Valore non valido',
        },
      ],
    };

    globalThis.fetch = vi.fn().mockResolvedValue(errorResponse(body));

    try {
      await request('/api/v1/auth/login', { method: 'POST' });
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({
        status: 400,
        body: {
          code: 'VALIDATION_ERROR',
          fieldErrors: [{ field: null }],
        },
      });
    }
  });

  it('tratta una risposta errore non conforme come UnexpectedResponseError', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ oops: true }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(request('/api/v1/me/account')).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
  });

  it('distingue un network failure da un HTTP error', async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(request('/api/v1/me/account')).rejects.toBeInstanceOf(
      NetworkError,
    );

    globalThis.fetch = vi.fn().mockResolvedValue(
      errorResponse({
        timestamp: '2026-07-26T10:00:00Z',
        status: 403,
        code: 'ACCESS_DENIED',
        message: 'Accesso negato',
        path: '/api/v1/me/account',
      }),
    );

    await expect(request('/api/v1/me/account')).rejects.toBeInstanceOf(
      HttpApiError,
    );
  });

  it('preserva AbortError senza convertirlo in NetworkError', async () => {
    const abortError = new DOMException('Request aborted', 'AbortError');
    globalThis.fetch = vi.fn().mockRejectedValue(abortError);

    try {
      await request('/api/v1/me/account');
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBe(abortError);
      expect(error).not.toBeInstanceOf(NetworkError);
      expect(error).toMatchObject({ name: 'AbortError' });
    }
  });

  it('usa URL relativi /api/v1 e credentials same-origin', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));
    globalThis.fetch = fetchMock;

    await request('/me/account');
    await request('/api/v1/me/profile');

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/me/account',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/me/profile',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
  });

  it('non invalida la sessione su 401 quando invalidateOn401 è false (default)', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const epochBefore = currentEpoch();
    globalThis.fetch = vi.fn().mockResolvedValue(
      errorResponse({
        timestamp: '2026-07-26T10:00:00Z',
        status: 401,
        code: 'UNAUTHORIZED',
        message: 'Utente non autenticato',
        path: '/api/v1/auth/login',
      }),
    );

    await expect(
      request('/api/v1/auth/login', { method: 'POST' }),
    ).rejects.toBeInstanceOf(HttpApiError);

    expect(onInvalidate).not.toHaveBeenCalled();
    expect(currentEpoch()).toBe(epochBefore);
  });

  it('invalida un 401 con body vuoto e propaga HttpApiError senza body', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const epochAtDispatch = currentEpoch();
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 401 }));

    try {
      await request('/api/v1/me/account', { invalidateOn401: true });
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({ status: 401, body: null });
    }

    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);
  });

  it('invalida un 401 anche con JSON non conforme e propaga HttpApiError', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const epochAtDispatch = currentEpoch();
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ unexpected: true }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    try {
      await request('/api/v1/me/account', { invalidateOn401: true });
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({ status: 401, body: null });
    }

    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);
  });

  it('invalida prima del parsing quando la lettura del body 401 rigetta', async () => {
    const events: string[] = [];
    const onInvalidate = vi.fn(() => {
      events.push('invalidated');
    });
    trackSubscribe(onInvalidate);

    const response = new Response(null, { status: 401 });
    const textError = new Error('body read failed');
    Object.defineProperty(response, 'text', {
      value: vi.fn().mockImplementation(() => {
        events.push('parse');
        expect(onInvalidate).toHaveBeenCalledTimes(1);
        expect(events).toEqual(['invalidated', 'parse']);
        return Promise.reject(textError);
      }),
    });
    globalThis.fetch = vi.fn().mockResolvedValue(response);

    const epochAtDispatch = currentEpoch();

    try {
      await request('/api/v1/me/account', { invalidateOn401: true });
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({ status: 401, body: null });
      expect(error).not.toBe(textError);
    }

    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(events).toEqual(['invalidated', 'parse']);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);
  });

  it('isola un subscriber che lancia e propaga comunque HttpApiError', async () => {
    const throwingSubscriber = vi.fn(() => {
      throw new Error('subscriber failure');
    });
    const nextSubscriber = vi.fn();
    trackSubscribe(throwingSubscriber);
    trackSubscribe(nextSubscriber);

    globalThis.fetch = vi.fn().mockResolvedValue(
      errorResponse({
        timestamp: '2026-07-26T10:00:00Z',
        status: 401,
        code: 'UNAUTHORIZED',
        message: 'Utente non autenticato',
        path: '/api/v1/me/account',
      }),
    );

    await expect(
      request('/api/v1/me/account', { invalidateOn401: true }),
    ).rejects.toBeInstanceOf(HttpApiError);

    expect(throwingSubscriber).toHaveBeenCalledTimes(1);
    expect(nextSubscriber).toHaveBeenCalledTimes(1);
  });

  it('con invalidateOn401 e epoch corrente notifica una sola volta e propaga HttpApiError', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const epochAtDispatch = currentEpoch();
    globalThis.fetch = vi.fn().mockResolvedValue(
      errorResponse({
        timestamp: '2026-07-26T10:00:00Z',
        status: 401,
        code: 'UNAUTHORIZED',
        message: 'Utente non autenticato',
        path: '/api/v1/me/account',
      }),
    );

    try {
      await request('/api/v1/me/account', { invalidateOn401: true });
      expect.fail('expected request to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpApiError);
      expect(error).toMatchObject({ status: 401 });
    }

    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);
  });

  it('due 401 concorrenti partiti dalla stessa epoch producono una sola notifica', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const firstGate = deferred<Response>();
    const secondGate = deferred<Response>();
    let call = 0;

    globalThis.fetch = vi.fn().mockImplementation(() => {
      call += 1;
      return call === 1 ? firstGate.promise : secondGate.promise;
    });

    const epochAtDispatch = currentEpoch();
    const firstRequest = request('/api/v1/me/account', {
      invalidateOn401: true,
    });
    const secondRequest = request('/api/v1/me/profile', {
      invalidateOn401: true,
    });

    const unauthorized: ErrorResponse = {
      timestamp: '2026-07-26T10:00:00Z',
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Utente non autenticato',
      path: '/api/v1/me/account',
    };

    firstGate.resolve(
      errorResponse({ ...unauthorized, path: '/api/v1/me/account' }),
    );
    await expect(firstRequest).rejects.toBeInstanceOf(HttpApiError);
    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);

    secondGate.resolve(
      errorResponse({ ...unauthorized, path: '/api/v1/me/profile' }),
    );
    await expect(secondRequest).rejects.toBeInstanceOf(HttpApiError);
    expect(onInvalidate).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochAtDispatch + 1);
  });

  it('un 401 partito da un’epoch precedente non notifica dopo advanceEpoch', async () => {
    const onInvalidate = vi.fn();
    trackSubscribe(onInvalidate);

    const gate = deferred<Response>();
    globalThis.fetch = vi.fn().mockReturnValue(gate.promise);

    const pending = request('/api/v1/me/account', { invalidateOn401: true });

    advanceEpoch();

    gate.resolve(
      errorResponse({
        timestamp: '2026-07-26T10:00:00Z',
        status: 401,
        code: 'UNAUTHORIZED',
        message: 'Utente non autenticato',
        path: '/api/v1/me/account',
      }),
    );

    await expect(pending).rejects.toBeInstanceOf(HttpApiError);
    expect(onInvalidate).not.toHaveBeenCalled();
  });
});

describe('observeHttpRequest', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    advanceEpoch();
  });

  it('espone status 200 e body JSON valido senza interpretarlo', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse({ valid: true, code: 'INV-ABC' }));

    const observed = await observeHttpRequest(
      '/auth/register/client/validate-invite',
      {
        method: 'POST',
      },
    );

    expect(observed.status).toBe(200);
    expect(observed.ok).toBe(true);
    expect(observed.body).toEqual({
      kind: 'json',
      value: { valid: true, code: 'INV-ABC' },
    });
  });

  it('espone status 202 con body JSON', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse({ message: 'neutro' }, { status: 202 }));

    const observed = await observeHttpRequest('/auth/register/client', {
      method: 'POST',
    });

    expect(observed.status).toBe(202);
    expect(observed.ok).toBe(true);
    expect(observed.body).toEqual({
      kind: 'json',
      value: { message: 'neutro' },
    });
  });

  it('espone body vuoto senza fallire', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(new Response('', { status: 202 }));

    const observed = await observeHttpRequest('/auth/register/client', {
      method: 'POST',
    });

    expect(observed.status).toBe(202);
    expect(observed.body).toEqual({ kind: 'empty' });
  });

  it('espone parse_error per body malformato', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response('<html>nope</html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    );

    const observed = await observeHttpRequest(
      '/auth/register/client/validate-invite',
    );

    expect(observed.status).toBe(200);
    expect(observed.body.kind).toBe('parse_error');
  });

  it('non lancia su non-2xx e conserva status/body', async () => {
    const body: ErrorResponse = {
      timestamp: '2026-07-31T10:00:00Z',
      status: 400,
      code: 'INVITE_CODE_EXPIRED',
      message: 'Codice invito scaduto',
      path: '/api/v1/auth/register/client/validate-invite',
    };
    globalThis.fetch = vi.fn().mockResolvedValue(errorResponse(body));

    const observed = await observeHttpRequest(
      '/auth/register/client/validate-invite',
      { method: 'POST' },
    );

    expect(observed.status).toBe(400);
    expect(observed.ok).toBe(false);
    expect(observed.body).toEqual({ kind: 'json', value: body });
  });

  it('propaga NetworkError su transport failure', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline'));

    await expect(
      observeHttpRequest('/auth/register/client', { method: 'POST' }),
    ).rejects.toBeInstanceOf(NetworkError);
  });

  it('classifica read_error quando la lettura del body fallisce', async () => {
    const readFailure = new TypeError('stream failed');
    const textMock = vi.fn().mockRejectedValue(readFailure);
    const response = new Response('payload', {
      status: 202,
      headers: { 'Content-Type': 'application/json' },
    });
    Object.defineProperty(response, 'text', {
      configurable: true,
      value: textMock,
    });
    globalThis.fetch = vi.fn().mockResolvedValue(response);

    const observed = await observeHttpRequest('/auth/register/client', {
      method: 'POST',
    });

    expect(observed.status).toBe(202);
    expect(observed.ok).toBe(true);
    expect(observed.body).toEqual({ kind: 'read_error', cause: readFailure });
    expect(textMock).toHaveBeenCalledTimes(1);
  });
});
