const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;
const MAX_UTF8_BYTES = 72;

/**
 * Conta i byte UTF-8 con la stessa semantica di
 * `String.getBytes(StandardCharsets.UTF_8)` in Java (encoder con replacement `?`
 * da 1 byte per input malformato, inclusi surrogate isolati).
 * Non muta né normalizza la stringa.
 */
export function javaUtf8ByteLength(value: string): number {
  let length = 0;
  let index = 0;

  while (index < value.length) {
    const unit = value.charCodeAt(index);

    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        const codePoint = ((unit - 0xd800) << 10) + (next - 0xdc00) + 0x10000;
        length += utf8BytesForCodePoint(codePoint);
        index += 2;
        continue;
      }

      // High surrogate isolato → replacement Java '?' (1 byte)
      length += 1;
      index += 1;
      continue;
    }

    if (unit >= 0xdc00 && unit <= 0xdfff) {
      // Low surrogate isolato → replacement Java '?' (1 byte)
      length += 1;
      index += 1;
      continue;
    }

    length += utf8BytesForCodePoint(unit);
    index += 1;
  }

  return length;
}

function utf8BytesForCodePoint(codePoint: number): number {
  if (codePoint <= 0x7f) {
    return 1;
  }
  if (codePoint <= 0x7ff) {
    return 2;
  }
  if (codePoint <= 0xffff) {
    return 3;
  }
  return 4;
}

export function validateRegistrationPassword(password: string): string | null {
  if (password.length === 0) {
    return 'Inserisci la password.';
  }

  if (password.length < 8) {
    return 'La password deve contenere almeno 8 caratteri.';
  }

  if (!PASSWORD_COMPLEXITY_PATTERN.test(password)) {
    return 'La password deve contenere almeno una maiuscola, un numero e un carattere speciale.';
  }

  if (javaUtf8ByteLength(password) > MAX_UTF8_BYTES) {
    return 'La password non può superare 72 byte in codifica UTF-8.';
  }

  return null;
}
