import { describe, expect, it } from 'vitest';

import { canonicalizeInviteCode } from './inviteCode';
import { decodeValidateInviteCodeResponse } from './validateInviteCodeResponse';

const CODE = 'INV-ABCDEF1234';
const CANONICAL = canonicalizeInviteCode(CODE);

function validPayload(overrides: Record<string, unknown> = {}) {
  return {
    valid: true,
    code: CODE,
    professionalId: 42,
    expiresAt: '2026-08-07T12:00:00.000Z',
    ...overrides,
  };
}

describe('decodeValidateInviteCodeResponse', () => {
  it('accetta un payload corretto', () => {
    expect(decodeValidateInviteCodeResponse(validPayload(), CANONICAL)).toEqual(
      {
        valid: true,
        code: CANONICAL,
        professionalId: 42,
        expiresAt: '2026-08-07T12:00:00.000Z',
      },
    );
  });

  it('accetta code con casing diverso se canonico coincide', () => {
    expect(
      decodeValidateInviteCodeResponse(
        validPayload({ code: 'inv-abcdef1234' }),
        CANONICAL,
      ).code,
    ).toBe(CANONICAL);
  });

  it.each([
    ['undefined', undefined],
    ['null', null],
    ['string', 'not-json-object'],
    ['array', []],
    ['empty object body shape', {}],
  ])('rifiuta body %s', (_label, body) => {
    expect(() =>
      decodeValidateInviteCodeResponse(body, CANONICAL),
    ).toThrowError();
  });

  it('rifiuta valid:false', () => {
    expect(() =>
      decodeValidateInviteCodeResponse(
        validPayload({ valid: false }),
        CANONICAL,
      ),
    ).toThrowError(/valid must be true/);
  });

  it('rifiuta code mancante', () => {
    expect(() =>
      decodeValidateInviteCodeResponse(
        {
          valid: true,
          professionalId: 42,
          expiresAt: '2026-08-07T12:00:00.000Z',
        },
        CANONICAL,
      ),
    ).toThrowError(/code must be a string/);
  });

  it('rifiuta code di tipo errato', () => {
    expect(() =>
      decodeValidateInviteCodeResponse(validPayload({ code: 123 }), CANONICAL),
    ).toThrowError(/code must be a string/);
  });

  it('rifiuta code diverso dalla request canonica', () => {
    expect(() =>
      decodeValidateInviteCodeResponse(
        validPayload({ code: 'INV-OTHER00001' }),
        CANONICAL,
      ),
    ).toThrowError(/does not match/);
  });

  it('accetta professionalId 1 e safe integer positivo', () => {
    expect(
      decodeValidateInviteCodeResponse(
        validPayload({ professionalId: 1 }),
        CANONICAL,
      ).professionalId,
    ).toBe(1);
    expect(
      decodeValidateInviteCodeResponse(
        validPayload({ professionalId: Number.MAX_SAFE_INTEGER }),
        CANONICAL,
      ).professionalId,
    ).toBe(Number.MAX_SAFE_INTEGER);
  });

  it.each([
    ['missing', undefined],
    ['zero', 0],
    ['negative', -1],
    ['decimal', 1.5],
    ['string', '42'],
    ['NaN', Number.NaN],
    ['Infinity', Number.POSITIVE_INFINITY],
    ['beyond safe integer', Number.MAX_SAFE_INTEGER + 1],
  ])('rifiuta professionalId %s', (_label, professionalId) => {
    expect(() =>
      decodeValidateInviteCodeResponse(
        validPayload({ professionalId }),
        CANONICAL,
      ),
    ).toThrowError(/professionalId/);
  });

  it('accetta Instant ISO con Z e millisecondi', () => {
    expect(
      decodeValidateInviteCodeResponse(
        validPayload({ expiresAt: '2026-07-13T15:30:45.123456Z' }),
        CANONICAL,
      ).expiresAt,
    ).toBe('2026-07-13T15:30:45.123456Z');
  });

  it('accetta Instant con offset numerico', () => {
    expect(
      decodeValidateInviteCodeResponse(
        validPayload({ expiresAt: '2026-07-13T15:30:45+02:00' }),
        CANONICAL,
      ).expiresAt,
    ).toBe('2026-07-13T15:30:45+02:00');
  });

  it.each([
    ['missing', undefined],
    ['empty', ''],
    ['date-only', '2026-08-07'],
    ['local space form', '2026-08-07 12:00:00'],
    ['zero string', '0'],
    ['not a date', 'not-iso'],
    ['impossible calendar day', '2026-02-31T12:00:00.000Z'],
    ['number type', 0],
  ])('rifiuta expiresAt %s', (_label, expiresAt) => {
    expect(() =>
      decodeValidateInviteCodeResponse(validPayload({ expiresAt }), CANONICAL),
    ).toThrowError(/expiresAt/);
  });
});

describe('canonicalizeInviteCode', () => {
  it('applica trim e toUpperCase come il backend', () => {
    expect(canonicalizeInviteCode('  inv-ab12  ')).toBe('INV-AB12');
  });
});
