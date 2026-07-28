import { afterEach, describe, expect, it, vi } from 'vitest';

import type {
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
} from './authTypes';
import { advanceEpoch } from './authEpoch';
import { clearCsrf, ensureCsrf } from './csrf';
import * as csrfMutation from './csrfMutation';
import { updateMyOperationalStatus, updateMyProfile } from './meProfileApi';

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

const CLIENT_PROFILE: MyClientProfileResponse = {
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
};

const PROFESSIONAL_PROFILE: MyProfessionalProfileResponse = {
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

describe('meProfileApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    advanceEpoch();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('esegue PATCH /me/profile con body differenziale e CSRF senza invalidare la cache', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('csrf-token', 'X-CSRF-TOKEN'))
      .mockResolvedValueOnce(jsonResponse(CLIENT_PROFILE));
    globalThis.fetch = fetchMock;

    const body = { firstName: 'Augusta' };
    const response = await updateMyProfile(body);

    expect(response).toEqual(CLIENT_PROFILE);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/me/profile');
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify(body),
        credentials: 'same-origin',
      }),
    );

    const headers = new Headers(
      fetchMock.mock.calls[1]?.[1]?.headers as HeadersInit,
    );
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');

    await expect(ensureCsrf()).resolves.toEqual({
      token: 'csrf-token',
      headerName: 'X-CSRF-TOKEN',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('esegue PATCH /me/profile/operational-status senza invalidare CSRF post-commit', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse('csrf-token', 'X-CSRF-TOKEN'))
      .mockResolvedValueOnce(
        jsonResponse({
          ...PROFESSIONAL_PROFILE,
          operationalStatus: 'FERIE',
        }),
      );
    globalThis.fetch = fetchMock;

    const body = { operationalStatus: 'FERIE' as const };
    const response = await updateMyOperationalStatus(body);

    expect(response.operationalStatus).toBe('FERIE');
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/me/profile/operational-status',
    );
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify(body),
        credentials: 'same-origin',
      }),
    );

    const headers = new Headers(
      fetchMock.mock.calls[1]?.[1]?.headers as HeadersInit,
    );
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token');

    await expect(ensureCsrf()).resolves.toEqual({
      token: 'csrf-token',
      headerName: 'X-CSRF-TOKEN',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('passa invalidateOn401 true e invalidateCsrfOnCommit false a updateMyProfile', async () => {
    const spy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValue(CLIENT_PROFILE);

    await updateMyProfile({ firstName: 'Augusta' });

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(
      '/me/profile',
      expect.objectContaining({
        method: 'PATCH',
        invalidateOn401: true,
        invalidateCsrfOnCommit: false,
      }),
    );
  });

  it('passa invalidateOn401 true e invalidateCsrfOnCommit false a updateMyOperationalStatus', async () => {
    const spy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValue({
        ...PROFESSIONAL_PROFILE,
        operationalStatus: 'FERIE',
      });

    await updateMyOperationalStatus({ operationalStatus: 'FERIE' });

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(
      '/me/profile/operational-status',
      expect.objectContaining({
        method: 'PATCH',
        invalidateOn401: true,
        invalidateCsrfOnCommit: false,
      }),
    );
  });
});
