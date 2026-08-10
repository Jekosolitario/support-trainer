import { afterEach, describe, expect, it, vi } from 'vitest';

import { advanceEpoch } from './authEpoch';
import { getClientById, listMyClients } from './clientsApi';
import {
  decodeClientDetail,
  decodeClientSummary,
  decodeClientSummaryList,
  type ClientDetail,
  type ClientSummary,
} from './clientsTypes';
import { subscribe } from './sessionInvalidation';
import { HttpApiError, UnexpectedResponseError } from './types';

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

function errorResponse(status: number, code: string, path: string): Response {
  return jsonResponse(
    {
      timestamp: '2026-08-10T12:00:00Z',
      status,
      code,
      message: 'Errore API',
      path,
    },
    { status },
  );
}

const CLIENT_SUMMARY: ClientSummary = {
  id: 7,
  firstName: 'Ada',
  lastName: 'Lovelace',
  profileImageUrl: null,
};

const CLIENT_DETAIL: ClientDetail = {
  ...CLIENT_SUMMARY,
  primaryGoal: 'Migliorare la mobilità',
  operationalStatus: 'INFORTUNATO',
  birthDate: '1992-02-29',
  heightCm: 168.5,
  gender: 'FEMALE',
};

describe('client response decoders', () => {
  it('decodifica una summary valida e scarta i campi extra', () => {
    const decoded = decodeClientSummary({
      ...CLIENT_SUMMARY,
      serverOnly: 'ignored',
    });

    expect(decoded).toEqual(CLIENT_SUMMARY);
    expect(decoded).not.toHaveProperty('serverOnly');
  });

  it('accetta profileImageUrl string oppure null', () => {
    expect(
      decodeClientSummary({
        ...CLIENT_SUMMARY,
        profileImageUrl: 'https://example.test/client.png',
      }).profileImageUrl,
    ).toBe('https://example.test/client.png');
    expect(decodeClientSummary(CLIENT_SUMMARY).profileImageUrl).toBeNull();
  });

  it.each([
    ['missing field', { id: 7, firstName: 'Ada', profileImageUrl: null }],
    ['wrong string type', { ...CLIENT_SUMMARY, firstName: 123 }],
    ['wrong nullable type', { ...CLIENT_SUMMARY, profileImageUrl: false }],
  ])('rifiuta summary con %s', (_label, payload) => {
    expect(() => decodeClientSummary(payload)).toThrowError();
  });

  it.each([
    ['zero', 0],
    ['negative', -1],
    ['fractional', 1.5],
    ['NaN', Number.NaN],
    ['infinite', Number.POSITIVE_INFINITY],
    ['unsafe', Number.MAX_SAFE_INTEGER + 1],
  ])('rifiuta ID %s', (_label, id) => {
    expect(() => decodeClientSummary({ ...CLIENT_SUMMARY, id })).toThrowError(
      /positive safe integer/,
    );
  });

  it('decodifica tutti i nove campi detail e scarta gli extra', () => {
    const decoded = decodeClientDetail({
      ...CLIENT_DETAIL,
      medicalNotes: 'non condividere',
    });

    expect(decoded).toEqual(CLIENT_DETAIL);
    expect(decoded).not.toHaveProperty('medicalNotes');
  });

  it.each([
    ['missing', undefined],
    ['wrong format', '29-02-1992'],
    ['impossible month', '1992-13-01'],
    ['impossible day', '2026-02-29'],
    ['datetime', '1992-02-29T00:00:00Z'],
  ])('rifiuta birthDate %s', (_label, birthDate) => {
    expect(() =>
      decodeClientDetail({ ...CLIENT_DETAIL, birthDate }),
    ).toThrowError(/birthDate/);
  });

  it.each([
    ['NaN', Number.NaN],
    ['infinite', Number.POSITIVE_INFINITY],
    ['string', '168.5'],
    ['null', null],
  ])('rifiuta heightCm %s', (_label, heightCm) => {
    expect(() =>
      decodeClientDetail({ ...CLIENT_DETAIL, heightCm }),
    ).toThrowError(/heightCm/);
  });

  it('rifiuta enum CLIENT e gender fuori contratto', () => {
    expect(() =>
      decodeClientDetail({
        ...CLIENT_DETAIL,
        operationalStatus: 'DISPONIBILE',
      }),
    ).toThrowError(/operationalStatus/);
    expect(() =>
      decodeClientDetail({ ...CLIENT_DETAIL, gender: 'UNKNOWN' }),
    ).toThrowError(/gender/);
  });

  it('rifiuta detail con campo obbligatorio mancante', () => {
    expect(() =>
      decodeClientDetail({ ...CLIENT_DETAIL, primaryGoal: undefined }),
    ).toThrowError(/primaryGoal/);
  });

  it('decodifica array vuoti e popolati e fallisce su un elemento invalido', () => {
    expect(decodeClientSummaryList([])).toEqual([]);
    expect(decodeClientSummaryList([CLIENT_SUMMARY])).toEqual([CLIENT_SUMMARY]);
    expect(() =>
      decodeClientSummaryList([CLIENT_SUMMARY, { ...CLIENT_SUMMARY, id: 0 }]),
    ).toThrowError();
    expect(() => decodeClientSummaryList({})).toThrowError(/array/);
  });
});

describe('clientsApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    advanceEpoch();
  });

  it('esegue GET /clients/my con credentials, signal e decoder', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([CLIENT_SUMMARY]));
    globalThis.fetch = fetchMock;

    await expect(listMyClients({ signal: controller.signal })).resolves.toEqual(
      [CLIENT_SUMMARY],
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/clients/my',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        signal: controller.signal,
      }),
    );
  });

  it('esegue GET /clients/{id} con credentials, signal e decoder', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(CLIENT_DETAIL));
    globalThis.fetch = fetchMock;

    await expect(
      getClientById(7, { signal: controller.signal }),
    ).resolves.toEqual(CLIENT_DETAIL);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/clients/7',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        signal: controller.signal,
      }),
    );
  });

  it('fallisce closed su una lista success non conforme', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse([{ ...CLIENT_SUMMARY, id: 0 }]));

    await expect(listMyClients()).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
  });

  it('fallisce closed su un dettaglio success non conforme', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({
        ...CLIENT_DETAIL,
        operationalStatus: 'UNKNOWN',
      }),
    );

    await expect(getClientById(7)).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
  });

  it.each([
    ['list', '/api/v1/clients/my', () => listMyClients()],
    ['detail', '/api/v1/clients/7', () => getClientById(7)],
  ])('abilita invalidateOn401 per %s', async (_label, path, invokeRequest) => {
    const onInvalidate = vi.fn();
    const unsubscribe = subscribe(onInvalidate);
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(errorResponse(401, 'UNAUTHORIZED', path));

    try {
      await expect(invokeRequest()).rejects.toBeInstanceOf(HttpApiError);
      expect(onInvalidate).toHaveBeenCalledTimes(1);
    } finally {
      unsubscribe();
    }
  });

  it.each([
    ['zero', 0],
    ['negative', -1],
    ['fractional', 1.5],
    ['NaN', Number.NaN],
    ['infinite', Number.POSITIVE_INFINITY],
    ['unsafe', Number.MAX_SAFE_INTEGER + 1],
  ])('rifiuta clientId %s senza eseguire HTTP', (_label, clientId) => {
    const fetchMock = vi.fn();
    globalThis.fetch = fetchMock;

    expect(() => getClientById(clientId)).toThrowError(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('preserva 404 HttpApiError senza mapping UI', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        errorResponse(404, 'CLIENT_NOT_FOUND', '/api/v1/clients/7'),
      );

    await expect(getClientById(7)).rejects.toMatchObject({
      name: 'HttpApiError',
      status: 404,
      body: expect.objectContaining({ code: 'CLIENT_NOT_FOUND' }),
    });
  });

  it('preserva AbortError', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    globalThis.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(listMyClients()).rejects.toBe(abortError);
  });
});
