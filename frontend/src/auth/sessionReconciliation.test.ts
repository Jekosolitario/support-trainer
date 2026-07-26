import { afterEach, describe, expect, it, vi } from 'vitest';

import type {
  MyAccountResponse,
  MyClientProfileResponse,
} from '../api/authTypes';
import type { ErrorResponse } from '../api/types';
import { AuthConsistencyError } from './mapAccessProfile';
import { reconcileSessionSnapshot } from './sessionReconciliation';

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

function errorResponse(status: number, path: string): Response {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code: status === 401 ? 'UNAUTHORIZED' : 'SERVER_ERROR',
    message: 'Request failed',
    path,
  };

  return jsonResponse(body, { status });
}

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

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}

describe('reconcileSessionSnapshot', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('restituisce authenticated per account e profilo coerenti anche con active false', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(account()))
      .mockResolvedValueOnce(jsonResponse(profile({ active: false })));
    globalThis.fetch = fetchMock;

    const outcome = await reconcileSessionSnapshot(
      new AbortController().signal,
    );

    expect(outcome).toMatchObject({
      kind: 'authenticated',
      account: { id: 1, role: 'CLIENT' },
      profile: { id: 1, role: 'CLIENT', active: false },
      accessProfile: { role: 'CLIENT', specialization: null },
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/me/account');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/me/profile');
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(
      fetchMock.mock.calls[1]?.[1]?.signal,
    );
  });

  it.each([
    [
      '401 + 200',
      errorResponse(401, '/api/v1/me/account'),
      jsonResponse(profile()),
    ],
    [
      '200 + 401',
      jsonResponse(account()),
      errorResponse(401, '/api/v1/me/profile'),
    ],
    [
      'network + 401',
      new Error('account offline'),
      errorResponse(401, '/api/v1/me/profile'),
    ],
    [
      '401 + network',
      errorResponse(401, '/api/v1/me/account'),
      new Error('profile offline'),
    ],
    [
      '5xx + 401',
      errorResponse(500, '/api/v1/me/account'),
      errorResponse(401, '/api/v1/me/profile'),
    ],
    [
      '401 + 5xx',
      errorResponse(401, '/api/v1/me/account'),
      errorResponse(500, '/api/v1/me/profile'),
    ],
  ])('fa prevalere il 401 in %s', async (_label, first, second) => {
    const fetchMock = vi.fn();
    const responses = [first, second];
    for (const response of responses) {
      if (response instanceof Error) {
        fetchMock.mockRejectedValueOnce(response);
      } else {
        fetchMock.mockResolvedValueOnce(response);
      }
    }
    globalThis.fetch = fetchMock;

    const outcome = await reconcileSessionSnapshot(
      new AbortController().signal,
    );

    expect(outcome).toMatchObject({
      kind: 'unauthenticated',
      cause: { status: 401 },
    });
  });

  it.each([
    ['network + 200', new Error('offline'), jsonResponse(profile())],
    [
      '5xx + 200',
      errorResponse(500, '/api/v1/me/account'),
      jsonResponse(profile()),
    ],
  ])('classifica %s come unavailable', async (_label, first, second) => {
    const fetchMock = vi.fn();
    if (first instanceof Error) {
      fetchMock.mockRejectedValueOnce(first);
    } else {
      fetchMock.mockResolvedValueOnce(first);
    }
    fetchMock.mockResolvedValueOnce(second);
    globalThis.fetch = fetchMock;

    const outcome = await reconcileSessionSnapshot(
      new AbortController().signal,
    );

    expect(outcome).toMatchObject({
      kind: 'unavailable',
      reason: 'request-failed',
    });
  });

  it.each([
    [
      'id mismatch',
      account({ id: 1 }),
      profile({ id: 2 }),
      'IDENTITY_MISMATCH',
    ],
    [
      'role mismatch',
      account({ role: 'PROFESSIONAL' }),
      profile(),
      'ROLE_MISMATCH',
    ],
  ])(
    'classifica %s come consistency unavailable senza dati privati',
    async (_label, accountBody, profileBody, code) => {
      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(accountBody))
        .mockResolvedValueOnce(jsonResponse(profileBody));

      const outcome = await reconcileSessionSnapshot(
        new AbortController().signal,
      );

      expect(outcome).toMatchObject({
        kind: 'unavailable',
        reason: 'inconsistent-data',
        cause: expect.objectContaining({
          code,
        }),
      });
      expect(outcome).not.toHaveProperty('account');
      expect(outcome).not.toHaveProperty('profile');
      expect(outcome).not.toHaveProperty('accessProfile');
    },
  );

  it.each(['invalid-id', 'invalid-role'])(
    'non autentica una coppia 200/200 con %s runtime',
    async (invalidField) => {
      const accountBody = account();
      const profileBody = profile();

      if (invalidField === 'invalid-id') {
        Reflect.set(accountBody, 'id', '1');
      } else {
        Reflect.set(accountBody, 'role', 'ADMIN');
        Reflect.set(profileBody, 'role', 'ADMIN');
        Reflect.set(profileBody, 'specialization', 'PERSONAL_TRAINER');
      }

      globalThis.fetch = vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(accountBody))
        .mockResolvedValueOnce(jsonResponse(profileBody));

      const outcome = await reconcileSessionSnapshot(
        new AbortController().signal,
      );

      expect(outcome).toMatchObject({
        kind: 'unavailable',
        reason: 'inconsistent-data',
        cause: expect.objectContaining({
          code: 'INCOMPLETE_DATA',
        }),
      });
      expect(outcome).not.toHaveProperty('account');
      expect(outcome).not.toHaveProperty('profile');
      expect(outcome).not.toHaveProperty('accessProfile');
    },
  );

  it('fa prevalere un 401 tardivo su un errore non-401 già risolto', async () => {
    const accountGate = deferred<Response>();
    const profileGate = deferred<Response>();
    globalThis.fetch = vi
      .fn()
      .mockReturnValueOnce(accountGate.promise)
      .mockReturnValueOnce(profileGate.promise);

    const reconciliation = reconcileSessionSnapshot(
      new AbortController().signal,
    );
    accountGate.reject(new Error('account offline'));
    await Promise.resolve();
    profileGate.resolve(errorResponse(401, '/api/v1/me/profile'));

    await expect(reconciliation).resolves.toMatchObject({
      kind: 'unauthenticated',
      cause: { status: 401 },
    });
  });

  it('fa prevalere un 401 anticipato su un errore non-401 tardivo', async () => {
    const accountGate = deferred<Response>();
    const profileGate = deferred<Response>();
    globalThis.fetch = vi
      .fn()
      .mockReturnValueOnce(accountGate.promise)
      .mockReturnValueOnce(profileGate.promise);

    const reconciliation = reconcileSessionSnapshot(
      new AbortController().signal,
    );
    accountGate.resolve(errorResponse(401, '/api/v1/me/account'));
    await Promise.resolve();
    profileGate.reject(new Error('profile offline'));

    await expect(reconciliation).resolves.toMatchObject({
      kind: 'unauthenticated',
      cause: { status: 401 },
    });
  });

  it('resta deterministico quando profile risolve prima di account', async () => {
    const accountGate = deferred<Response>();
    const profileGate = deferred<Response>();
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(accountGate.promise)
      .mockReturnValueOnce(profileGate.promise);
    globalThis.fetch = fetchMock;

    const reconciliation = reconcileSessionSnapshot(
      new AbortController().signal,
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);

    profileGate.resolve(jsonResponse(profile()));
    await Promise.resolve();
    accountGate.resolve(jsonResponse(account()));

    await expect(reconciliation).resolves.toMatchObject({
      kind: 'authenticated',
      accessProfile: { role: 'CLIENT' },
    });
  });

  it('restituisce cancelled per AbortError senza unavailable', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    globalThis.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(
      reconcileSessionSnapshot(new AbortController().signal),
    ).resolves.toEqual({
      kind: 'cancelled',
    });
  });

  it('rende incompleto un payload 200 malformato', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(undefined))
      .mockResolvedValueOnce(jsonResponse(profile()));

    const outcome = await reconcileSessionSnapshot(
      new AbortController().signal,
    );

    expect(outcome).toMatchObject({
      kind: 'unavailable',
      reason: 'inconsistent-data',
      cause: expect.any(AuthConsistencyError),
    });
  });
});
