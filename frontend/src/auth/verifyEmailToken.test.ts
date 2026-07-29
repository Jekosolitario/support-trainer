import { describe, expect, it } from 'vitest';

import { parseVerifyEmailTokenFromHash } from './verifyEmailToken';

describe('parseVerifyEmailTokenFromHash', () => {
  it('accetta un token valido', () => {
    expect(parseVerifyEmailTokenFromHash('#token=abc-123')).toEqual({
      ok: true,
      token: 'abc-123',
    });
  });

  it('accetta %2B e + senza secondo decode', () => {
    expect(parseVerifyEmailTokenFromHash('#token=a%2Bb')).toEqual({
      ok: true,
      token: 'a+b',
    });
    expect(parseVerifyEmailTokenFromHash('#token=a+b')).toEqual({
      ok: true,
      token: 'a b',
    });
  });

  it('accetta token già encoded una volta', () => {
    expect(parseVerifyEmailTokenFromHash('#token=hello%20world')).toEqual({
      ok: true,
      token: 'hello world',
    });
  });

  it('non applica un secondo decodeURIComponent', () => {
    expect(parseVerifyEmailTokenFromHash('#token=%252B')).toEqual({
      ok: true,
      token: '%2B',
    });
  });

  it('rifiuta hash vuoto o senza token', () => {
    expect(parseVerifyEmailTokenFromHash('')).toEqual({
      ok: false,
      reason: 'missing',
    });
    expect(parseVerifyEmailTokenFromHash('#')).toEqual({
      ok: false,
      reason: 'missing',
    });
    expect(parseVerifyEmailTokenFromHash('#other=1')).toEqual({
      ok: false,
      reason: 'missing',
    });
  });

  it('rifiuta token vuoto o blank', () => {
    expect(parseVerifyEmailTokenFromHash('#token=')).toEqual({
      ok: false,
      reason: 'invalid',
    });
    expect(parseVerifyEmailTokenFromHash('#token=%20')).toEqual({
      ok: false,
      reason: 'invalid',
    });
    expect(parseVerifyEmailTokenFromHash('#token=   ')).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('rifiuta token oltre 500 caratteri', () => {
    const tooLong = `a${'b'.repeat(500)}`;
    expect(parseVerifyEmailTokenFromHash(`#token=${tooLong}`)).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('rifiuta token duplicato', () => {
    expect(parseVerifyEmailTokenFromHash('#token=a&token=b')).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('ignora parametri aggiuntivi con un solo token', () => {
    expect(parseVerifyEmailTokenFromHash('#token=ok&utm=1')).toEqual({
      ok: true,
      token: 'ok',
    });
  });
});
