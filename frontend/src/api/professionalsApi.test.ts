import { afterEach, describe, expect, it, vi } from 'vitest';

import { advanceEpoch } from './authEpoch';
import { getProfessionalById, listMyProfessionals } from './professionalsApi';
import {
  decodeProfessionalDetail,
  decodeProfessionalSummary,
  decodeProfessionalSummaryList,
  type ProfessionalDetail,
  type ProfessionalSummary,
} from './professionalsTypes';
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

const PROFESSIONAL_SUMMARY: ProfessionalSummary = {
  id: 11,
  firstName: 'Grace',
  lastName: 'Hopper',
  profileImageUrl: 'https://example.test/professional.png',
  specialization: 'NUTRITIONIST',
  operationalStatus: 'FERIE',
  active: true,
};

const PROFESSIONAL_DETAIL: ProfessionalDetail = {
  ...PROFESSIONAL_SUMMARY,
  phoneNumber: null,
  bio: 'Nutrizionista sportiva',
  workplaceName: null,
  city: 'Roma',
  instagramUrl: null,
  websiteUrl: 'https://example.test',
};

describe('professional response decoders', () => {
  it('decodifica una summary completa e scarta i campi extra', () => {
    const decoded = decodeProfessionalSummary({
      ...PROFESSIONAL_SUMMARY,
      email: 'private@example.test',
    });

    expect(decoded).toEqual(PROFESSIONAL_SUMMARY);
    expect(decoded).not.toHaveProperty('email');
  });

  it('accetta profileImageUrl string oppure null', () => {
    expect(
      decodeProfessionalSummary(PROFESSIONAL_SUMMARY).profileImageUrl,
    ).toBe('https://example.test/professional.png');
    expect(
      decodeProfessionalSummary({
        ...PROFESSIONAL_SUMMARY,
        profileImageUrl: null,
      }).profileImageUrl,
    ).toBeNull();
  });

  it.each([
    ['missing field', { id: 11, firstName: 'Grace' }],
    ['wrong string type', { ...PROFESSIONAL_SUMMARY, lastName: 123 }],
    [
      'wrong nullable type',
      { ...PROFESSIONAL_SUMMARY, profileImageUrl: false },
    ],
    ['wrong active type', { ...PROFESSIONAL_SUMMARY, active: 'true' }],
  ])('rifiuta summary con %s', (_label, payload) => {
    expect(() => decodeProfessionalSummary(payload)).toThrowError();
  });

  it.each([
    ['zero', 0],
    ['negative', -1],
    ['fractional', 1.5],
    ['NaN', Number.NaN],
    ['infinite', Number.POSITIVE_INFINITY],
    ['unsafe', Number.MAX_SAFE_INTEGER + 1],
  ])('rifiuta ID %s', (_label, id) => {
    expect(() =>
      decodeProfessionalSummary({ ...PROFESSIONAL_SUMMARY, id }),
    ).toThrowError(/positive safe integer/);
  });

  it('rifiuta specialization e operationalStatus fuori contratto', () => {
    expect(() =>
      decodeProfessionalSummary({
        ...PROFESSIONAL_SUMMARY,
        specialization: 'PHYSIOTHERAPIST',
      }),
    ).toThrowError(/specialization/);
    expect(() =>
      decodeProfessionalSummary({
        ...PROFESSIONAL_SUMMARY,
        operationalStatus: 'ATTIVO',
      }),
    ).toThrowError(/operationalStatus/);
  });

  it('decodifica il detail completo mantenendo i nullable', () => {
    expect(decodeProfessionalDetail(PROFESSIONAL_DETAIL)).toEqual(
      PROFESSIONAL_DETAIL,
    );

    const allNull = decodeProfessionalDetail({
      ...PROFESSIONAL_DETAIL,
      phoneNumber: null,
      bio: null,
      workplaceName: null,
      city: null,
      instagramUrl: null,
      websiteUrl: null,
    });
    expect(allNull.phoneNumber).toBeNull();
    expect(allNull.bio).toBeNull();
    expect(allNull.websiteUrl).toBeNull();
  });

  it.each([
    ['phoneNumber number', { phoneNumber: 123 }],
    ['bio object', { bio: {} }],
    ['workplaceName missing', { workplaceName: undefined }],
    ['city boolean', { city: false }],
    ['instagramUrl array', { instagramUrl: [] }],
    ['websiteUrl number', { websiteUrl: 42 }],
  ])('rifiuta detail nullable non conforme: %s', (_label, override) => {
    expect(() =>
      decodeProfessionalDetail({ ...PROFESSIONAL_DETAIL, ...override }),
    ).toThrowError();
  });

  it('decodifica array vuoti e popolati e fallisce su un elemento invalido', () => {
    expect(decodeProfessionalSummaryList([])).toEqual([]);
    expect(decodeProfessionalSummaryList([PROFESSIONAL_SUMMARY])).toEqual([
      PROFESSIONAL_SUMMARY,
    ]);
    expect(() =>
      decodeProfessionalSummaryList([
        PROFESSIONAL_SUMMARY,
        { ...PROFESSIONAL_SUMMARY, active: null },
      ]),
    ).toThrowError();
    expect(() => decodeProfessionalSummaryList('not-an-array')).toThrowError(
      /array/,
    );
  });
});

describe('professionalsApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    advanceEpoch();
  });

  it('esegue GET /professionals/my con credentials, signal e decoder', async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse([PROFESSIONAL_SUMMARY]));
    globalThis.fetch = fetchMock;

    await expect(
      listMyProfessionals({ signal: controller.signal }),
    ).resolves.toEqual([PROFESSIONAL_SUMMARY]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/professionals/my',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        signal: controller.signal,
      }),
    );
  });

  it('esegue GET /professionals/{id} con credentials, signal e decoder', async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(PROFESSIONAL_DETAIL));
    globalThis.fetch = fetchMock;

    await expect(
      getProfessionalById(11, { signal: controller.signal }),
    ).resolves.toEqual(PROFESSIONAL_DETAIL);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/professionals/11',
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
      .mockResolvedValue(
        jsonResponse([{ ...PROFESSIONAL_SUMMARY, active: 'true' }]),
      );

    await expect(listMyProfessionals()).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
  });

  it('fallisce closed su un dettaglio success non conforme', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({ ...PROFESSIONAL_DETAIL, websiteUrl: false }),
      );

    await expect(getProfessionalById(11)).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
  });

  it.each([
    ['list', '/api/v1/professionals/my', () => listMyProfessionals()],
    ['detail', '/api/v1/professionals/11', () => getProfessionalById(11)],
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
  ])(
    'rifiuta professionalId %s senza eseguire HTTP',
    (_label, professionalId) => {
      const fetchMock = vi.fn();
      globalThis.fetch = fetchMock;

      expect(() => getProfessionalById(professionalId)).toThrowError(
        RangeError,
      );
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it('preserva 404 HttpApiError senza mapping UI', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        errorResponse(
          404,
          'PROFESSIONAL_NOT_FOUND',
          '/api/v1/professionals/11',
        ),
      );

    await expect(getProfessionalById(11)).rejects.toMatchObject({
      name: 'HttpApiError',
      status: 404,
      body: expect.objectContaining({ code: 'PROFESSIONAL_NOT_FOUND' }),
    });
  });

  it('preserva AbortError', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    globalThis.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(listMyProfessionals()).rejects.toBe(abortError);
  });
});
