import { describe, expect, it } from 'vitest';

import { parsePasswordResetTokenFromHash } from './passwordResetToken';

describe('parsePasswordResetTokenFromHash', () => {
  it('accetta un token valido', () => {
    expect(parsePasswordResetTokenFromHash('#token=abc-123')).toEqual({
      ok: true,
      token: 'abc-123',
    });
  });

  it('accetta %2B e + senza secondo decode', () => {
    expect(parsePasswordResetTokenFromHash('#token=a%2Bb')).toEqual({
      ok: true,
      token: 'a+b',
    });
    expect(parsePasswordResetTokenFromHash('#token=a+b')).toEqual({
      ok: true,
      token: 'a b',
    });
  });

  it('non applica un secondo decodeURIComponent', () => {
    expect(parsePasswordResetTokenFromHash('#token=%252B')).toEqual({
      ok: true,
      token: '%2B',
    });
  });

  it('rifiuta hash vuoto o senza token', () => {
    expect(parsePasswordResetTokenFromHash('')).toEqual({
      ok: false,
      reason: 'missing',
    });
    expect(parsePasswordResetTokenFromHash('#')).toEqual({
      ok: false,
      reason: 'missing',
    });
    expect(parsePasswordResetTokenFromHash('#other=1')).toEqual({
      ok: false,
      reason: 'missing',
    });
  });

  it('rifiuta token vuoto o blank', () => {
    expect(parsePasswordResetTokenFromHash('#token=')).toEqual({
      ok: false,
      reason: 'invalid',
    });
    expect(parsePasswordResetTokenFromHash('#token=%20')).toEqual({
      ok: false,
      reason: 'invalid',
    });
    expect(parsePasswordResetTokenFromHash('#token=   ')).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('rifiuta token oltre 500 caratteri', () => {
    const tooLong = `a${'b'.repeat(500)}`;
    expect(parsePasswordResetTokenFromHash(`#token=${tooLong}`)).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('rifiuta token duplicato', () => {
    expect(parsePasswordResetTokenFromHash('#token=a&token=b')).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('ignora parametri aggiuntivi con un solo token', () => {
    expect(parsePasswordResetTokenFromHash('#token=ok&utm=1')).toEqual({
      ok: true,
      token: 'ok',
    });
  });
});
