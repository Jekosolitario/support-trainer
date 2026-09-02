const MAX_TOKEN_LENGTH = 500;

export type PasswordResetTokenParseResult =
  | { readonly ok: true; readonly token: string }
  | { readonly ok: false; readonly reason: 'missing' | 'invalid' };

export function parsePasswordResetTokenFromHash(
  hash: string,
): PasswordResetTokenParseResult {
  if (hash === '' || hash === '#') {
    return { ok: false, reason: 'missing' };
  }

  const query = hash.startsWith('#') ? hash.slice(1) : hash;
  const params = new URLSearchParams(query);
  const values = params.getAll('token');

  if (values.length === 0) {
    return { ok: false, reason: 'missing' };
  }

  if (values.length !== 1) {
    return { ok: false, reason: 'invalid' };
  }

  const token = values[0];

  if (
    token === null ||
    token.trim() === '' ||
    token.length > MAX_TOKEN_LENGTH
  ) {
    return { ok: false, reason: 'invalid' };
  }

  return { ok: true, token };
}
