import { describe, expect, it } from 'vitest';

import {
  javaUtf8ByteLength,
  validateRegistrationPassword,
} from './passwordPolicy';

describe('passwordPolicy', () => {
  it('accetta una password conforme', () => {
    expect(validateRegistrationPassword('Password1!')).toBeNull();
  });

  it('richiede almeno 8 unità UTF-16', () => {
    expect(validateRegistrationPassword('Pass1!')).toBe(
      'La password deve contenere almeno 8 caratteri.',
    );
    expect(validateRegistrationPassword('Passwor1!')).toBeNull();
  });

  it('richiede maiuscola ASCII, cifra ASCII e carattere speciale', () => {
    expect(validateRegistrationPassword('password1!')).toMatch(/maiuscola/);
    expect(validateRegistrationPassword('Password!')).toMatch(/numero/);
    expect(validateRegistrationPassword('Password1')).toMatch(/speciale/);
  });

  it('accetta esattamente 72 byte ASCII e rifiuta 73', () => {
    const seventyTwo = `${'A'.repeat(69)}1!x`;
    const seventyThree = `${'A'.repeat(70)}1!x`;

    expect(javaUtf8ByteLength(seventyTwo)).toBe(72);
    expect(validateRegistrationPassword(seventyTwo)).toBeNull();
    expect(javaUtf8ByteLength(seventyThree)).toBe(73);
    expect(validateRegistrationPassword(seventyThree)).toMatch(/72 byte/);
  });

  it('conta i byte UTF-8 per Unicode multibyte well-formed', () => {
    const withEuro = `Password1!${'€'.repeat(22)}`;
    expect(javaUtf8ByteLength(withEuro)).toBeGreaterThan(withEuro.length);
    expect(javaUtf8ByteLength(withEuro)).toBeGreaterThan(72);
    expect(validateRegistrationPassword(withEuro)).toMatch(/72 byte/);
  });

  it('accetta Unicode well-formed sotto il limite byte', () => {
    expect(validateRegistrationPassword('Password1!à')).toBeNull();
  });

  it('conta i surrogate isolati come 1 byte (replacement Java ?)', () => {
    const highIsolated = `Password1!${String.fromCharCode(0xd800)}`;
    const lowIsolated = `Password1!${String.fromCharCode(0xdc00)}`;

    expect(javaUtf8ByteLength(highIsolated)).toBe(
      javaUtf8ByteLength('Password1!') + 1,
    );
    expect(javaUtf8ByteLength(lowIsolated)).toBe(
      javaUtf8ByteLength('Password1!') + 1,
    );
    expect(validateRegistrationPassword(highIsolated)).toBeNull();
    expect(validateRegistrationPassword(lowIsolated)).toBeNull();
  });

  it('allinea il caso audit A1! + 68 a + high-surrogate al limite bcrypt', () => {
    const password = `A1!${'a'.repeat(68)}${String.fromCharCode(0xd800)}`;
    expect(password.length).toBe(72);
    expect(javaUtf8ByteLength(password)).toBe(72);
    expect(validateRegistrationPassword(password)).toBeNull();

    const over = `A1!${'a'.repeat(69)}${String.fromCharCode(0xd800)}`;
    expect(javaUtf8ByteLength(over)).toBe(73);
    expect(validateRegistrationPassword(over)).toMatch(/72 byte/);
  });

  it('non trimma né normalizza il valore valutato', () => {
    const withLeadingSpace = ' Password1!';
    expect(validateRegistrationPassword(withLeadingSpace)).toBeNull();
    expect(withLeadingSpace.startsWith(' ')).toBe(true);

    const withTrailingSpace = 'Password1! ';
    expect(validateRegistrationPassword(withTrailingSpace)).toBeNull();
    expect(withTrailingSpace.endsWith(' ')).toBe(true);
  });
});
