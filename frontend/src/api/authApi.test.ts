import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  AuthTransitionInProgressError,
  PostLoginCsrfRefreshError,
  StaleAuthOperationError,
  getMyAccount,
  getMyProfile,
  login,
  logout,
} from './authApi';
import type {
  LoginRequest,
  MyAccountResponse,
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
} from './authTypes';
import { advanceEpoch, currentEpoch } from './authEpoch';
import { clearCsrf } from './csrf';
import { subscribe } from './sessionInvalidation';
import { HttpApiError, NetworkError, type ErrorResponse } from './types';

const CREDENTIALS: LoginRequest = {
  email: 'trainer@example.com',
  password: 'password-sicura',
};

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

function errorResponse(
  status: number,
  code: string,
  path = '/api/v1/auth/login',
): Response {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message: code,
    path,
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

describe('authApi', () => {
  const originalFetch = globalThis.fetch;
  const unsubscribers: Array<() => void> = [];

  afterEach(() => {
    while (unsubscribers.length > 0) {
      unsubscribers.pop()?.();
    }

    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  function trackSubscribe(callback: () => void): void {
    unsubscribers.push(subscribe(callback));
  }

  it('esegue login una volta, invalida T0 e acquisisce T1', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(noContentResponse())
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'));
    globalThis.fetch = fetchMock;

    await login(CREDENTIALS);

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/login');
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(CREDENTIALS),
        credentials: 'same-origin',
      }),
    );
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-CSRF-A')).toBe('token-0');
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/csrf');
  });

  it('propaga il 401 login senza notificare session invalidation', async () => {
    const invalidated = vi.fn();
    trackSubscribe(invalidated);
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(401, 'AUTHENTICATION_ERROR'));
    globalThis.fetch = fetchMock;

    const result = login(CREDENTIALS);

    await expect(result).rejects.toMatchObject({
      status: 401,
      body: { code: 'AUTHENTICATION_ERROR' },
    });
    expect(invalidated).not.toHaveBeenCalled();
  });

  it.each([
    [403, 'ACCOUNT_NOT_ACTIVE'],
    [403, 'EMAIL_NOT_VERIFIED'],
    [400, 'VALIDATION_ERROR'],
  ])('propaga l’errore login %s %s', async (status, code) => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(status, code));
    globalThis.fetch = fetchMock;

    await expect(login(CREDENTIALS)).rejects.toMatchObject({
      status,
      body: { code },
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('ritenta login una volta dopo CSRF failure senza avanzare nuovamente epoch', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse())
      .mockResolvedValueOnce(csrfResponse('token-2', 'X-CSRF-C'));
    globalThis.fetch = fetchMock;

    await login(CREDENTIALS);

    expect(currentEpoch()).toBe(epochBefore + 1);
    const loginCalls = fetchMock.mock.calls.filter(
      ([input]) => input === '/api/v1/auth/login',
    );
    expect(loginCalls).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it('propaga un secondo CSRF failure login senza ripetere le credenziali oltre il replay', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'))
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(errorResponse(403, 'CSRF_VALIDATION_FAILED'));
    globalThis.fetch = fetchMock;

    await expect(login(CREDENTIALS)).rejects.toMatchObject({
      status: 403,
      body: { code: 'CSRF_VALIDATION_FAILED' },
    });
    expect(
      fetchMock.mock.calls.filter(([input]) => input === '/api/v1/auth/login'),
    ).toHaveLength(2);
  });

  it.each(['network', 'http-5xx'])(
    'distingue il fallimento T1 %s da una POST login fallita',
    async (failureKind) => {
      const failure =
        failureKind === 'network'
          ? new Error('offline after login')
          : errorResponse(503, 'SERVICE_UNAVAILABLE', '/api/v1/auth/csrf');
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
        .mockResolvedValueOnce(noContentResponse());

      if (failure instanceof Response) {
        fetchMock.mockResolvedValueOnce(failure);
      } else {
        fetchMock.mockRejectedValueOnce(failure);
      }
      globalThis.fetch = fetchMock;

      const result = login(CREDENTIALS);

      await expect(result).rejects.toBeInstanceOf(PostLoginCsrfRefreshError);
      try {
        await result;
        expect.fail('expected login to reject');
      } catch (error) {
        expect(error).toBeInstanceOf(PostLoginCsrfRefreshError);
        const refreshError = error as PostLoginCsrfRefreshError;
        expect(refreshError.cause).toBeInstanceOf(
          failureKind === 'network' ? NetworkError : HttpApiError,
        );
      }
      expect(
        fetchMock.mock.calls.filter(
          ([input]) => input === '/api/v1/auth/login',
        ),
      ).toHaveLength(1);
    },
  );

  it('fa prevalere stale durante T1 senza ripetere la POST login', async () => {
    const refreshGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(noContentResponse())
      .mockReturnValueOnce(refreshGate.promise);
    globalThis.fetch = fetchMock;

    const result = login(CREDENTIALS);
    await flushUntil(() => fetchMock.mock.calls.length === 3);

    advanceEpoch();
    refreshGate.resolve(csrfResponse('token-1', 'X-CSRF-B'));

    await expect(result).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(
      fetchMock.mock.calls.filter(([input]) => input === '/api/v1/auth/login'),
    ).toHaveLength(1);
  });

  it('dopo login committed diventato stale invalida T0 senza avviare T1', async () => {
    const loginGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(loginGate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    const first = login(CREDENTIALS);
    await flushUntil(() => fetchMock.mock.calls.length === 2);
    advanceEpoch();
    loginGate.resolve(noContentResponse());

    await expect(first).rejects.toBeInstanceOf(StaleAuthOperationError);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/csrf');
  });

  it('rifiuta login B mentre login A ha la POST pending', async () => {
    const loginGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(loginGate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'));
    globalThis.fetch = fetchMock;

    const first = login(CREDENTIALS);
    await flushUntil(() => fetchMock.mock.calls.length === 2);
    const epochWhileLocked = currentEpoch();

    const second = login(CREDENTIALS);
    await expect(second).rejects.toBeInstanceOf(AuthTransitionInProgressError);
    expect(currentEpoch()).toBe(epochWhileLocked);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    loginGate.resolve(noContentResponse());
    await first;
  });

  it('rifiuta logout mentre login è pending', async () => {
    const loginGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(loginGate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'));
    globalThis.fetch = fetchMock;

    const pendingLogin = login(CREDENTIALS);
    await flushUntil(() => fetchMock.mock.calls.length === 2);
    const epochWhileLocked = currentEpoch();

    await expect(logout()).rejects.toBeInstanceOf(
      AuthTransitionInProgressError,
    );
    expect(currentEpoch()).toBe(epochWhileLocked);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    loginGate.resolve(noContentResponse());
    await pendingLogin;
  });

  it('rifiuta login mentre logout è pending', async () => {
    const logoutGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(logoutGate.promise);
    globalThis.fetch = fetchMock;

    const pendingLogout = logout();
    await flushUntil(() => fetchMock.mock.calls.length === 2);
    const epochWhileLocked = currentEpoch();

    await expect(login(CREDENTIALS)).rejects.toBeInstanceOf(
      AuthTransitionInProgressError,
    );
    expect(currentEpoch()).toBe(epochWhileLocked);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    logoutGate.resolve(noContentResponse());
    await pendingLogout;
  });

  it.each([
    'success',
    'http-4xx',
    'http-5xx',
    'network',
    'abort',
    'post-login-refresh',
  ])('rilascia il lock dopo esito login %s', async (outcome) => {
    let csrfCount = 0;
    const networkError = new Error('offline');
    const abortError = new DOMException('aborted', 'AbortError');
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);

      if (url === '/api/v1/auth/csrf') {
        csrfCount += 1;
        if (outcome === 'post-login-refresh' && csrfCount === 2) {
          return Promise.reject(networkError);
        }
        return Promise.resolve(
          csrfResponse(`token-${String(csrfCount)}`, 'X-CSRF'),
        );
      }

      if (url === '/api/v1/auth/login') {
        if (outcome === 'http-4xx') {
          return Promise.resolve(errorResponse(401, 'AUTHENTICATION_ERROR'));
        }
        if (outcome === 'http-5xx') {
          return Promise.resolve(errorResponse(500, 'SERVER_ERROR'));
        }
        if (outcome === 'network') {
          return Promise.reject(networkError);
        }
        if (outcome === 'abort') {
          return Promise.reject(abortError);
        }
        return Promise.resolve(noContentResponse());
      }

      return Promise.resolve(noContentResponse());
    });
    globalThis.fetch = fetchMock;

    const first = login(CREDENTIALS);
    if (outcome === 'success') {
      await first;
    } else {
      await expect(first).rejects.toBeDefined();
    }

    await expect(logout()).resolves.toBeUndefined();
    expect(
      fetchMock.mock.calls.some(([input]) => input === '/api/v1/auth/logout'),
    ).toBe(true);
  });

  it('esegue logout senza body/content-type, avanza una volta e invalida CSRF', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(noContentResponse())
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    await logout();

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/logout');
    expect(fetchMock.mock.calls[1]?.[1]?.body).toBeUndefined();
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.has('Content-Type')).toBe(false);

    await logout();
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/csrf');
  });

  it('ritenta logout una volta su CSRF failure', async () => {
    const epochBefore = currentEpoch();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(
        errorResponse(403, 'CSRF_VALIDATION_FAILED', '/api/v1/auth/logout'),
      )
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    await logout();

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('propaga il secondo CSRF failure logout', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockResolvedValueOnce(
        errorResponse(403, 'CSRF_VALIDATION_FAILED', '/api/v1/auth/logout'),
      )
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(
        errorResponse(403, 'CSRF_VALIDATION_FAILED', '/api/v1/auth/logout'),
      );
    globalThis.fetch = fetchMock;

    await expect(logout()).rejects.toMatchObject({
      status: 403,
      body: { code: 'CSRF_VALIDATION_FAILED' },
    });
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it.each([
    ['http-401', 401],
    ['http-5xx', 500],
    ['network', 0],
  ])('propaga logout %s senza falso successo', async (kind, status) => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'));

    if (kind === 'network') {
      fetchMock.mockRejectedValueOnce(new Error('offline'));
    } else {
      fetchMock.mockResolvedValueOnce(
        errorResponse(status, 'LOGOUT_ERROR', '/api/v1/auth/logout'),
      );
    }
    globalThis.fetch = fetchMock;

    await expect(logout()).rejects.toBeDefined();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('su logout committed diventato stale invalida CSRF e rilascia il lock', async () => {
    const logoutGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('token-0', 'X-CSRF-A'))
      .mockReturnValueOnce(logoutGate.promise)
      .mockResolvedValueOnce(csrfResponse('token-1', 'X-CSRF-B'))
      .mockResolvedValueOnce(noContentResponse());
    globalThis.fetch = fetchMock;

    const first = logout();
    await flushUntil(() => fetchMock.mock.calls.length === 2);
    advanceEpoch();
    logoutGate.resolve(noContentResponse());

    await expect(first).rejects.toBeInstanceOf(StaleAuthOperationError);
    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/csrf');
  });

  it('legge account e profilo dagli endpoint corretti inoltrando signal', async () => {
    const account: MyAccountResponse = {
      id: 1,
      email: 'client@example.com',
      role: 'CLIENT',
      accountStatus: 'ACTIVE',
      emailVerified: true,
      createdAt: '2026-07-26T10:00:00Z',
      updatedAt: '2026-07-26T10:00:00Z',
    };
    const clientProfile: MyClientProfileResponse = {
      id: 1,
      role: 'CLIENT',
      firstName: 'Ada',
      lastName: 'Lovelace',
      profileImageUrl: null,
      operationalStatus: 'ATTIVO',
      active: true,
      specialization: null,
      phoneNumber: null,
      bio: null,
      workplaceName: null,
      city: null,
      instagramUrl: null,
      websiteUrl: null,
      birthDate: '1996-04-15',
      heightCm: 178,
      primaryGoal: 'Benessere',
      gender: 'FEMALE',
      medicalNotes: null,
      injuryNotes: null,
      notes: null,
    };
    const professionalProfile: MyProfessionalProfileResponse = {
      id: 2,
      role: 'PROFESSIONAL',
      firstName: 'Grace',
      lastName: 'Hopper',
      profileImageUrl: null,
      operationalStatus: 'DISPONIBILE',
      active: true,
      specialization: 'PERSONAL_TRAINER',
      phoneNumber: null,
      bio: null,
      workplaceName: null,
      city: null,
      instagramUrl: null,
      websiteUrl: null,
      birthDate: null,
      heightCm: null,
      primaryGoal: null,
      gender: null,
      medicalNotes: null,
      injuryNotes: null,
      notes: null,
    };
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(account))
      .mockResolvedValueOnce(jsonResponse(clientProfile))
      .mockResolvedValueOnce(jsonResponse(professionalProfile));
    globalThis.fetch = fetchMock;

    expect(await getMyAccount()).toEqual(account);
    expect(
      await getMyProfile({
        invalidateOn401: false,
        signal: controller.signal,
      }),
    ).toEqual(clientProfile);
    expect(await getMyProfile()).toEqual(professionalProfile);

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/me/account');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/me/profile');
    expect(fetchMock.mock.calls[1]?.[1]?.signal).toBe(controller.signal);
  });

  it('invalida la sessione sul 401 /me di default', async () => {
    const invalidated = vi.fn();
    trackSubscribe(invalidated);
    const epochBefore = currentEpoch();
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        errorResponse(401, 'UNAUTHORIZED', '/api/v1/me/account'),
      );

    await expect(getMyAccount()).rejects.toBeInstanceOf(HttpApiError);
    expect(invalidated).toHaveBeenCalledTimes(1);
    expect(currentEpoch()).toBe(epochBefore + 1);
  });

  it('non invalida globalmente il 401 /me in modalità reconciliation', async () => {
    const invalidated = vi.fn();
    trackSubscribe(invalidated);
    const epochBefore = currentEpoch();
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        errorResponse(401, 'UNAUTHORIZED', '/api/v1/me/profile'),
      );

    await expect(
      getMyProfile({ invalidateOn401: false }),
    ).rejects.toBeInstanceOf(HttpApiError);
    expect(invalidated).not.toHaveBeenCalled();
    expect(currentEpoch()).toBe(epochBefore);
  });
});
