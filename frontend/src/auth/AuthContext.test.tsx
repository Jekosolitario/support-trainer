import { useEffect } from 'react';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as authApi from '../api/authApi';
import type {
  MyAccountResponse,
  MyClientProfileResponse,
} from '../api/authTypes';
import { advanceEpoch, currentEpoch } from '../api/authEpoch';
import { clearCsrf, ensureCsrf } from '../api/csrf';
import { notifySessionInvalidated } from '../api/sessionInvalidation';
import { HttpApiError, NetworkError, type ErrorResponse } from '../api/types';
import { renderWithAuthProvider } from '../test/renderWithAuthProvider';
import {
  AuthOperationNotAllowedError,
  useAuth,
  type AuthContextValue,
} from './authState';
import { AuthConsistencyError } from './mapAccessProfile';
import * as sessionReconciliation from './sessionReconciliation';
import type { SessionReconciliationOutcome } from './sessionReconciliation';

function account(
  overrides: Partial<MyAccountResponse> = {},
): MyAccountResponse {
  return {
    id: 1,
    email: 'client@example.com',
    role: 'CLIENT',
    accountStatus: 'ACTIVE',
    emailVerified: true,
    createdAt: '2026-07-26T10:00:00Z',
    updatedAt: '2026-07-26T10:00:00Z',
    ...overrides,
  };
}

function profile(
  overrides: Partial<MyClientProfileResponse> = {},
): MyClientProfileResponse {
  return {
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
    heightCm: 170,
    primaryGoal: 'Benessere',
    gender: 'FEMALE',
    medicalNotes: null,
    injuryNotes: null,
    notes: null,
    ...overrides,
  };
}

function apiError(
  status: number,
  code: string,
  path = '/api/v1/auth/login',
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message: code,
    path,
  };
  const response = new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

  return new HttpApiError(status, body, response);
}

function authenticatedOutcome(active = true): SessionReconciliationOutcome {
  const accountValue = account();
  const profileValue = profile({ active });

  return {
    kind: 'authenticated',
    account: accountValue,
    profile: profileValue,
    accessProfile: {
      role: 'CLIENT',
      specialization: null,
    },
  };
}

function unauthenticatedOutcome(): SessionReconciliationOutcome {
  return {
    kind: 'unauthenticated',
    cause: apiError(401, 'UNAUTHORIZED', '/api/v1/me/account'),
  };
}

function unavailableOutcome(): SessionReconciliationOutcome {
  return {
    kind: 'unavailable',
    reason: 'request-failed',
    cause: new NetworkError(new Error('offline')),
  };
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

let latestContext: AuthContextValue | null = null;

function Probe() {
  const context = useAuth();

  useEffect(() => {
    latestContext = context;

    return () => {
      if (latestContext === context) {
        latestContext = null;
      }
    };
  }, [context]);

  return (
    <div>
      <output data-testid="status">{context.state.status}</output>
      <output data-testid="operation">
        {context.state.operation ?? 'none'}
      </output>
      <output data-testid="account">
        {context.state.account?.id ?? 'null'}
      </output>
      <output data-testid="profile">
        {context.state.profile?.id ?? 'null'}
      </output>
      <output data-testid="access">
        {context.state.accessProfile?.role ?? 'null'}
      </output>
    </div>
  );
}

function auth(): AuthContextValue {
  if (latestContext === null) {
    throw new Error('Auth context probe has not rendered');
  }

  return latestContext;
}

async function waitForStatus(status: string): Promise<void> {
  await waitFor(() => {
    expect(screen.getByTestId('status')).toHaveTextContent(status);
  });
}

describe('AuthProvider', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    cleanup();
    latestContext = null;
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('rende esplicito l’uso di useAuth fuori dal provider', () => {
    expect(() => render(<Probe />)).toThrow(
      'useAuth must be used within an AuthProvider',
    );
  });

  it('parte initializing e completa il cold bootstrap authenticated', async () => {
    const gate = deferred<SessionReconciliationOutcome>();
    vi.spyOn(sessionReconciliation, 'reconcileSessionSnapshot').mockReturnValue(
      gate.promise,
    );

    renderWithAuthProvider(<Probe />);

    expect(screen.getByTestId('status')).toHaveTextContent('initializing');
    expect(screen.getByTestId('operation')).toHaveTextContent('bootstrap');
    expect(screen.getByTestId('account')).toHaveTextContent('null');

    gate.resolve(authenticatedOutcome(false));
    await waitForStatus('authenticated');

    expect(screen.getByTestId('account')).toHaveTextContent('1');
    expect(screen.getByTestId('profile')).toHaveTextContent('1');
    expect(screen.getByTestId('access')).toHaveTextContent('CLIENT');
    expect(auth().state.profile?.active).toBe(false);
  });

  it.each([
    ['unauthenticated', unauthenticatedOutcome()],
    ['unavailable', unavailableOutcome()],
  ])('applica bootstrap %s senza dati privati', async (status, outcome) => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(outcome);

    renderWithAuthProvider(<Probe />);
    await waitForStatus(status);

    expect(screen.getByTestId('account')).toHaveTextContent('null');
    expect(screen.getByTestId('profile')).toHaveTextContent('null');
    expect(screen.getByTestId('access')).toHaveTextContent('null');
  });

  it('isola bootstrap e listener tra i lifecycle StrictMode', async () => {
    const firstGate = deferred<SessionReconciliationOutcome>();
    const secondGate = deferred<SessionReconciliationOutcome>();
    const reconciliationSpy = vi
      .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockReturnValueOnce(firstGate.promise)
      .mockReturnValueOnce(secondGate.promise);

    renderWithAuthProvider(<Probe />, { strictMode: true });
    await waitFor(() => {
      expect(reconciliationSpy).toHaveBeenCalledTimes(2);
    });
    expect(reconciliationSpy.mock.calls[0]?.[0].aborted).toBe(true);
    expect(reconciliationSpy.mock.calls[1]?.[0].aborted).toBe(false);

    secondGate.resolve(authenticatedOutcome());
    await waitForStatus('authenticated');

    firstGate.resolve(unavailableOutcome());
    await act(async () => {
      await firstGate.promise;
      await Promise.resolve();
    });
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated');

    const epochBeforeListener = currentEpoch();
    act(() => {
      advanceEpoch();
      notifySessionInvalidated();
    });
    expect(currentEpoch()).toBe(epochBeforeListener + 1);
    expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated');
  });

  it('il listener invalida una reconciliation pending senza doppio advance', async () => {
    const gate = deferred<SessionReconciliationOutcome>();
    vi.spyOn(sessionReconciliation, 'reconcileSessionSnapshot').mockReturnValue(
      gate.promise,
    );

    renderWithAuthProvider(<Probe />);
    const epochBeforeListener = currentEpoch();

    act(() => {
      advanceEpoch();
      notifySessionInvalidated();
    });

    expect(currentEpoch()).toBe(epochBeforeListener + 1);
    expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated');
    expect(screen.getByTestId('account')).toHaveTextContent('null');

    gate.resolve(authenticatedOutcome());
    await act(async () => {
      await gate.promise;
      await Promise.resolve();
    });
    expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated');
  });

  it('il listener elimina anche il CSRF corrente', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: 'token-0', headerName: 'X-CSRF-A' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: 'token-1', headerName: 'X-CSRF-B' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    globalThis.fetch = fetchMock;

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');
    expect((await ensureCsrf()).token).toBe('token-0');

    act(() => {
      advanceEpoch();
      notifySessionInvalidated();
    });

    expect((await ensureCsrf()).token).toBe('token-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('rifiuta login authenticated senza cambiare dati, epoch o rete', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const loginSpy = vi.spyOn(authApi, 'login');
    const fetchMock = vi.fn();
    globalThis.fetch = fetchMock;

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');
    const epochBefore = currentEpoch();

    await expect(
      auth().login({
        email: 'other@example.com',
        password: 'password-sicura',
      }),
    ).rejects.toBeInstanceOf(AuthOperationNotAllowedError);

    expect(currentEpoch()).toBe(epochBefore);
    expect(auth().state.status).toBe('authenticated');
    expect(auth().state.account?.id).toBe(1);
    expect(loginSpy).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('mantiene AuthOperationNotAllowedError distinto dagli errori HTTP', () => {
    const error = new AuthOperationNotAllowedError('login', 'authenticated');

    expect(error).not.toBeInstanceOf(HttpApiError);
    expect(error).toMatchObject({
      operation: 'login',
      status: 'authenticated',
    });
    expect(error).not.toHaveProperty('account');
    expect(error).not.toHaveProperty('profile');
  });

  it('rifiuta login e logout da unavailable lasciando reconciliation come recovery', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(unavailableOutcome());
    const loginSpy = vi.spyOn(authApi, 'login');
    const logoutSpy = vi.spyOn(authApi, 'logout');

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unavailable');
    const epochBefore = currentEpoch();

    await expect(
      auth().login({
        email: 'client@example.com',
        password: 'password-sicura',
      }),
    ).rejects.toMatchObject({
      operation: 'login',
      status: 'unavailable',
    });
    await expect(auth().logout()).rejects.toMatchObject({
      operation: 'logout',
      status: 'unavailable',
    });

    expect(currentEpoch()).toBe(epochBefore);
    expect(loginSpy).not.toHaveBeenCalled();
    expect(logoutSpy).not.toHaveBeenCalled();
  });

  it('rifiuta logout unauthenticated e operazioni durante initializing', async () => {
    const gate = deferred<SessionReconciliationOutcome>();
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockReturnValueOnce(gate.promise);
    renderWithAuthProvider(<Probe />);

    await expect(
      auth().login({
        email: 'client@example.com',
        password: 'password-sicura',
      }),
    ).rejects.toBeInstanceOf(authApi.AuthTransitionInProgressError);

    gate.resolve(unauthenticatedOutcome());
    await waitForStatus('unauthenticated');
    const epochBefore = currentEpoch();

    await expect(auth().logout()).rejects.toBeInstanceOf(
      AuthOperationNotAllowedError,
    );
    expect(currentEpoch()).toBe(epochBefore);
  });

  it('reconcileSession è consentita solo da unavailable e avanza una volta', async () => {
    const reconciliationSpy = vi
      .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockResolvedValueOnce(unavailableOutcome())
      .mockResolvedValueOnce(authenticatedOutcome());

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unavailable');
    const epochBefore = currentEpoch();

    let reconciliation!: Promise<void>;
    act(() => {
      reconciliation = auth().reconcileSession();
    });
    expect(auth().state.status).toBe('initializing');
    expect(auth().state.account).toBeNull();
    await act(async () => {
      await reconciliation;
    });

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(auth().state.status).toBe('authenticated');
    expect(reconciliationSpy).toHaveBeenCalledTimes(2);

    await expect(auth().reconcileSession()).rejects.toBeInstanceOf(
      AuthOperationNotAllowedError,
    );
  });

  it('non sovrappone due reconciliation pubbliche', async () => {
    const reconciliationGate = deferred<SessionReconciliationOutcome>();
    vi.spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockResolvedValueOnce(unavailableOutcome())
      .mockReturnValueOnce(reconciliationGate.promise);

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unavailable');
    const epochBefore = currentEpoch();

    let first!: Promise<void>;
    act(() => {
      first = auth().reconcileSession();
    });
    const epochAfterFirst = currentEpoch();

    await expect(auth().reconcileSession()).rejects.toBeInstanceOf(
      authApi.AuthTransitionInProgressError,
    );
    expect(currentEpoch()).toBe(epochAfterFirst);
    expect(epochAfterFirst).toBe(epochBefore + 1);

    await act(async () => {
      reconciliationGate.resolve(authenticatedOutcome());
      await first;
    });
    expect(auth().state.status).toBe('authenticated');
  });

  it('login riusa la propria epoch per hydration e termina authenticated', async () => {
    const reconciliationSpy = vi
      .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockResolvedValueOnce(unauthenticatedOutcome())
      .mockResolvedValueOnce(authenticatedOutcome());
    const loginSpy = vi.spyOn(authApi, 'login').mockImplementation(() => {
      advanceEpoch();
      return Promise.resolve();
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unauthenticated');
    const epochBefore = currentEpoch();

    await act(async () => {
      await auth().login({
        email: 'client@example.com',
        password: 'password-sicura',
      });
    });

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(auth().state.status).toBe('authenticated');
    expect(loginSpy).toHaveBeenCalledTimes(1);
    expect(reconciliationSpy).toHaveBeenCalledTimes(2);
  });

  it.each([
    [
      'AUTHENTICATION_ERROR',
      apiError(401, 'AUTHENTICATION_ERROR'),
      'unauthenticated',
    ],
    [
      'ACCOUNT_NOT_ACTIVE',
      apiError(403, 'ACCOUNT_NOT_ACTIVE'),
      'unauthenticated',
    ],
    [
      'EMAIL_NOT_VERIFIED',
      apiError(403, 'EMAIL_NOT_VERIFIED'),
      'unauthenticated',
    ],
    ['VALIDATION_ERROR', apiError(400, 'VALIDATION_ERROR'), 'unauthenticated'],
    ['http-5xx', apiError(500, 'SERVER_ERROR'), 'unavailable'],
    ['network', new NetworkError(new Error('offline')), 'unavailable'],
    [
      'post-login',
      new authApi.PostLoginCsrfRefreshError(new Error('T1 failed')),
      'unavailable',
    ],
  ])(
    'classifica errore login %s e lo propaga',
    async (_label, error, expectedStatus) => {
      const reconciliationSpy = vi
        .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
        .mockResolvedValue(unauthenticatedOutcome());
      const loginSpy = vi.spyOn(authApi, 'login').mockImplementation(() => {
        advanceEpoch();
        return Promise.reject(error);
      });

      renderWithAuthProvider(<Probe />);
      await waitForStatus('unauthenticated');

      let result!: Promise<void>;
      act(() => {
        result = auth().login({
          email: 'client@example.com',
          password: 'password-sicura',
        });
      });

      await act(async () => {
        await expect(result).rejects.toBe(error);
      });
      expect(auth().state.status).toBe(expectedStatus);
      expect(auth().state.account).toBeNull();
      expect(auth().state.profile).toBeNull();
      expect(auth().state.accessProfile).toBeNull();
      expect(loginSpy).toHaveBeenCalledTimes(1);
      expect(reconciliationSpy).toHaveBeenCalledTimes(1);
    },
  );

  it.each([
    ['401', unauthenticatedOutcome(), 'unauthenticated'],
    ['network', unavailableOutcome(), 'unavailable'],
  ])(
    'classifica hydration post-login %s senza una seconda login',
    async (_label, hydrationOutcome, expectedStatus) => {
      vi.spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
        .mockResolvedValueOnce(unauthenticatedOutcome())
        .mockResolvedValueOnce(hydrationOutcome);
      const loginSpy = vi.spyOn(authApi, 'login').mockImplementation(() => {
        advanceEpoch();
        return Promise.resolve();
      });

      renderWithAuthProvider(<Probe />);
      await waitForStatus('unauthenticated');

      let result!: Promise<void>;
      act(() => {
        result = auth().login({
          email: 'client@example.com',
          password: 'password-sicura',
        });
      });
      await act(async () => {
        await expect(result).rejects.toBeDefined();
      });

      expect(auth().state.status).toBe(expectedStatus);
      expect(loginSpy).toHaveBeenCalledTimes(1);
    },
  );

  it('impedisce hydration E1 dopo invalidazione E2', async () => {
    const loginGate = deferred<void>();
    const reconciliationSpy = vi
      .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockResolvedValue(unauthenticatedOutcome());
    vi.spyOn(authApi, 'login').mockImplementation(() => {
      advanceEpoch();
      return loginGate.promise;
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unauthenticated');
    let loginResult!: Promise<void>;
    act(() => {
      loginResult = auth().login({
        email: 'client@example.com',
        password: 'password-sicura',
      });
    });
    const loginEpoch = currentEpoch();

    act(() => {
      loginGate.resolve();
      advanceEpoch();
      notifySessionInvalidated();
    });

    await expect(loginResult).rejects.toBeInstanceOf(
      authApi.StaleAuthOperationError,
    );
    expect(currentEpoch()).toBe(loginEpoch + 1);
    expect(auth().state.status).toBe('unauthenticated');
    expect(auth().state.reason).toBe('session-invalidated');
    expect(reconciliationSpy).toHaveBeenCalledTimes(1);
  });

  it('non committa una hydration già pending dopo invalidazione E2', async () => {
    const hydrationGate = deferred<SessionReconciliationOutcome>();
    const reconciliationSpy = vi
      .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
      .mockResolvedValueOnce(unauthenticatedOutcome())
      .mockReturnValueOnce(hydrationGate.promise);
    vi.spyOn(authApi, 'login').mockImplementation(() => {
      advanceEpoch();
      return Promise.resolve();
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('unauthenticated');

    let loginResult!: Promise<void>;
    act(() => {
      loginResult = auth().login({
        email: 'client@example.com',
        password: 'password-sicura',
      });
    });
    await waitFor(() => {
      expect(reconciliationSpy).toHaveBeenCalledTimes(2);
      expect(auth().state.operation).toBe('post-login-hydration');
    });
    const loginEpoch = currentEpoch();

    act(() => {
      advanceEpoch();
      notifySessionInvalidated();
    });
    expect(auth().state.status).toBe('unauthenticated');
    expect(auth().state.reason).toBe('session-invalidated');

    await act(async () => {
      hydrationGate.resolve(authenticatedOutcome());
      await expect(loginResult).rejects.toBeInstanceOf(
        authApi.StaleAuthOperationError,
      );
    });

    expect(currentEpoch()).toBe(loginEpoch + 1);
    expect(auth().state.status).toBe('unauthenticated');
    expect(auth().state.reason).toBe('session-invalidated');
    expect(auth().state.account).toBeNull();
    expect(auth().state.profile).toBeNull();
    expect(auth().state.accessProfile).toBeNull();
  });

  it('logout nasconde subito i dati e su 204 termina unauthenticated', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const logoutGate = deferred<void>();
    vi.spyOn(authApi, 'logout').mockImplementation(() => {
      advanceEpoch();
      return logoutGate.promise;
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');
    const epochBefore = currentEpoch();

    let logoutResult!: Promise<void>;
    act(() => {
      logoutResult = auth().logout();
    });
    expect(auth().state.status).toBe('initializing');
    expect(auth().state.operation).toBe('logout');
    expect(auth().state.account).toBeNull();

    await act(async () => {
      logoutGate.resolve();
      await logoutResult;
    });

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(auth().state.status).toBe('unauthenticated');
  });

  it('sul secondo CSRF failure logout pulisce T1 e ripristina lo snapshot', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const rejectedCsrf = apiError(
      403,
      'CSRF_VALIDATION_FAILED',
      '/api/v1/auth/logout',
    );
    const logoutSpy = vi.spyOn(authApi, 'logout').mockImplementation(() => {
      advanceEpoch();
      return Promise.reject(rejectedCsrf);
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: 'token-0', headerName: 'X-CSRF-A' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: 'token-1', headerName: 'X-CSRF-B' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    globalThis.fetch = fetchMock;

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');
    expect((await ensureCsrf()).token).toBe('token-0');
    const epochBefore = currentEpoch();

    let logoutResult!: Promise<void>;
    act(() => {
      logoutResult = auth().logout();
    });
    await act(async () => {
      await expect(logoutResult).rejects.toBe(rejectedCsrf);
    });

    expect(currentEpoch()).toBe(epochBefore + 1);
    expect(auth().state.status).toBe('authenticated');
    expect(auth().state.account?.id).toBe(1);
    expect((await ensureCsrf()).token).toBe('token-1');
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each([
    ['401', apiError(401, 'UNAUTHORIZED', '/api/v1/auth/logout')],
    ['http-5xx', apiError(500, 'SERVER_ERROR', '/api/v1/auth/logout')],
    ['network', new NetworkError(new Error('offline'))],
  ])(
    'classifica logout %s come unavailable senza retry',
    async (_label, error) => {
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockResolvedValue(authenticatedOutcome());
      const logoutSpy = vi.spyOn(authApi, 'logout').mockImplementation(() => {
        advanceEpoch();
        return Promise.reject(error);
      });

      renderWithAuthProvider(<Probe />);
      await waitForStatus('authenticated');

      let logoutResult!: Promise<void>;
      act(() => {
        logoutResult = auth().logout();
      });
      await act(async () => {
        await expect(logoutResult).rejects.toBe(error);
      });
      expect(auth().state.status).toBe('unavailable');
      expect(auth().state.account).toBeNull();
      expect(logoutSpy).toHaveBeenCalledTimes(1);
    },
  );

  it('impedisce a logout E1 di sovrascrivere invalidazione E2', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const logoutGate = deferred<void>();
    vi.spyOn(authApi, 'logout').mockImplementation(() => {
      advanceEpoch();
      return logoutGate.promise;
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');
    let logoutResult!: Promise<void>;
    act(() => {
      logoutResult = auth().logout();
    });
    const logoutEpoch = currentEpoch();

    act(() => {
      logoutGate.resolve();
      advanceEpoch();
      notifySessionInvalidated();
    });

    await expect(logoutResult).rejects.toBeInstanceOf(
      authApi.StaleAuthOperationError,
    );
    expect(currentEpoch()).toBe(logoutEpoch + 1);
    expect(auth().state.status).toBe('unauthenticated');
    expect(auth().state.reason).toBe('session-invalidated');
  });

  it('non ripristina lo snapshot se il CSRF failure logout appartiene a E1 stale', async () => {
    vi.spyOn(
      sessionReconciliation,
      'reconcileSessionSnapshot',
    ).mockResolvedValue(authenticatedOutcome());
    const csrfFailure = apiError(
      403,
      'CSRF_VALIDATION_FAILED',
      '/api/v1/auth/logout',
    );
    const logoutGate = deferred<void>();
    const logoutSpy = vi.spyOn(authApi, 'logout').mockImplementation(() => {
      advanceEpoch();
      return logoutGate.promise;
    });

    renderWithAuthProvider(<Probe />);
    await waitForStatus('authenticated');

    let logoutResult!: Promise<void>;
    act(() => {
      logoutResult = auth().logout();
    });
    const logoutEpoch = currentEpoch();

    act(() => {
      advanceEpoch();
      notifySessionInvalidated();
    });
    expect(auth().state.status).toBe('unauthenticated');

    await act(async () => {
      logoutGate.reject(csrfFailure);
      await expect(logoutResult).rejects.toBeInstanceOf(
        authApi.StaleAuthOperationError,
      );
    });

    expect(currentEpoch()).toBe(logoutEpoch + 1);
    expect(auth().state.status).toBe('unauthenticated');
    expect(auth().state.reason).toBe('session-invalidated');
    expect(auth().state.account).toBeNull();
    expect(auth().state.profile).toBeNull();
    expect(auth().state.accessProfile).toBeNull();
    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });

  describe('applyProfileSnapshot', () => {
    it('sostituisce profile preservando account, status e accessProfile ricalcolato', async () => {
      const reconcileSpy = vi
        .spyOn(sessionReconciliation, 'reconcileSessionSnapshot')
        .mockReturnValue(Promise.resolve(authenticatedOutcome()));
      const fetchMock = vi.fn();
      globalThis.fetch = fetchMock;
      renderWithAuthProvider(<Probe />);
      await waitForStatus('authenticated');

      const epochBefore = currentEpoch();
      const accountBefore = auth().state.account;
      const updatedProfile = profile({
        firstName: 'Augusta',
        primaryGoal: 'Forza',
        operationalStatus: 'PAUSA',
      });

      act(() => {
        auth().applyProfileSnapshot(updatedProfile, epochBefore);
      });

      expect(auth().state.status).toBe('authenticated');
      expect(auth().state.account).toEqual(accountBefore);
      expect(auth().state.profile).toEqual(updatedProfile);
      expect(auth().state.accessProfile).toEqual({
        role: 'CLIENT',
        specialization: null,
      });
      expect(currentEpoch()).toBe(epochBefore);
      expect(reconcileSpy).toHaveBeenCalledTimes(1);
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rifiuta snapshot con epoch stale e lascia lo stato invariato', async () => {
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockReturnValue(Promise.resolve(authenticatedOutcome()));
      renderWithAuthProvider(<Probe />);
      await waitForStatus('authenticated');

      const staleEpoch = currentEpoch();
      const snapshotBefore = {
        account: auth().state.account,
        profile: auth().state.profile,
        accessProfile: auth().state.accessProfile,
      };

      act(() => {
        advanceEpoch();
      });

      let thrown: unknown;
      act(() => {
        try {
          auth().applyProfileSnapshot(
            profile({ firstName: 'Stale' }),
            staleEpoch,
          );
        } catch (error) {
          thrown = error;
        }
      });

      expect(thrown).toBeInstanceOf(authApi.StaleAuthOperationError);
      expect(auth().state.status).toBe('authenticated');
      expect(auth().state.account).toEqual(snapshotBefore.account);
      expect(auth().state.profile).toEqual(snapshotBefore.profile);
      expect(auth().state.accessProfile).toEqual(snapshotBefore.accessProfile);
      expect(currentEpoch()).toBe(staleEpoch + 1);
    });

    it('rifiuta applyProfileSnapshot da initializing senza mutare lo stato', async () => {
      const deferredBootstrap = deferred<SessionReconciliationOutcome>();
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockReturnValue(deferredBootstrap.promise);
      renderWithAuthProvider(<Probe />);
      await waitForStatus('initializing');

      let thrown: unknown;
      act(() => {
        try {
          auth().applyProfileSnapshot(profile(), currentEpoch());
        } catch (error) {
          thrown = error;
        }
      });

      expect(thrown).toBeInstanceOf(AuthOperationNotAllowedError);
      expect(thrown).toMatchObject({
        operation: 'applyProfileSnapshot',
        status: 'initializing',
      });
      expect(auth().state.status).toBe('initializing');

      await act(async () => {
        deferredBootstrap.resolve(authenticatedOutcome());
        await deferredBootstrap.promise;
      });
      await waitForStatus('authenticated');
    });

    it('rifiuta applyProfileSnapshot da unauthenticated', async () => {
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockReturnValue(Promise.resolve(unauthenticatedOutcome()));
      renderWithAuthProvider(<Probe />);
      await waitForStatus('unauthenticated');

      let thrown: unknown;
      act(() => {
        try {
          auth().applyProfileSnapshot(profile(), currentEpoch());
        } catch (error) {
          thrown = error;
        }
      });

      expect(thrown).toBeInstanceOf(AuthOperationNotAllowedError);
      expect(thrown).toMatchObject({
        operation: 'applyProfileSnapshot',
        status: 'unauthenticated',
      });
      expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated');
      expect(auth().state.status).toBe('unauthenticated');
      expect(auth().state.account).toBeNull();
    });

    it('rifiuta applyProfileSnapshot da unavailable senza mutare lo stato', async () => {
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockReturnValue(Promise.resolve(unavailableOutcome()));
      renderWithAuthProvider(<Probe />);
      await waitForStatus('unavailable');

      const reasonBefore = auth().state.reason;
      let thrown: unknown;

      act(() => {
        try {
          auth().applyProfileSnapshot(profile(), currentEpoch());
        } catch (error) {
          thrown = error;
        }
      });

      expect(thrown).toBeInstanceOf(AuthOperationNotAllowedError);
      expect(thrown).toMatchObject({
        operation: 'applyProfileSnapshot',
        status: 'unavailable',
      });
      expect(screen.getByTestId('status')).toHaveTextContent('unavailable');
      expect(auth().state.status).toBe('unavailable');
      expect(auth().state.reason).toBe(reasonBefore);
    });

    it('rifiuta snapshot incoerente e non passa a unavailable', async () => {
      vi.spyOn(
        sessionReconciliation,
        'reconcileSessionSnapshot',
      ).mockReturnValue(Promise.resolve(authenticatedOutcome()));
      renderWithAuthProvider(<Probe />);
      await waitForStatus('authenticated');

      const epochBefore = currentEpoch();
      const snapshotBefore = {
        account: auth().state.account,
        profile: auth().state.profile,
        accessProfile: auth().state.accessProfile,
      };

      let thrown: unknown;
      act(() => {
        try {
          auth().applyProfileSnapshot(
            profile({ id: 999, firstName: 'Mismatch' }),
            epochBefore,
          );
        } catch (error) {
          thrown = error;
        }
      });

      expect(thrown).toBeInstanceOf(AuthConsistencyError);
      expect(auth().state.status).toBe('authenticated');
      expect(auth().state.account).toEqual(snapshotBefore.account);
      expect(auth().state.profile).toEqual(snapshotBefore.profile);
      expect(auth().state.accessProfile).toEqual(snapshotBefore.accessProfile);
      expect(currentEpoch()).toBe(epochBefore);
    });
  });
});
