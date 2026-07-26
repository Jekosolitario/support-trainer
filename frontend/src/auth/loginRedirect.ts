import type { UserAccessProfile } from '../app/config/access';

const ENCODED_BACKSLASH = /%5c/i;
const PROTECTED_PATH = /^\/app\/(?:client|professional)\//;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 31 || code === 127) {
      return true;
    }
  }

  return false;
}

function isDotSegment(segment: string): boolean {
  return segment === '.' || segment === '..';
}

/**
 * Rejects pathnames that are not already canonical: raw or percent-decoded
 * segments equal to "." / ".." must not be accepted, and malformed percent
 * encoding is treated as invalid. The original pathname is never rewritten.
 */
function hasUnsafePathSegments(pathname: string): boolean {
  const segments = pathname.split('/');

  for (let index = 1; index < segments.length; index += 1) {
    const raw = segments[index];

    if (raw === undefined || raw === '') {
      continue;
    }

    if (isDotSegment(raw)) {
      return true;
    }

    let decoded: string;
    try {
      decoded = decodeURIComponent(raw);
    } catch {
      return true;
    }

    if (
      isDotSegment(decoded) ||
      decoded.includes('/') ||
      decoded.includes('\\') ||
      hasControlCharacter(decoded)
    ) {
      return true;
    }
  }

  return false;
}

function hasSafePathnameSyntax(pathname: string): boolean {
  return (
    pathname.startsWith('/') &&
    !pathname.startsWith('//') &&
    !pathname.includes('//') &&
    !pathname.includes('?') &&
    !pathname.includes('#') &&
    !pathname.includes('\\') &&
    !ENCODED_BACKSLASH.test(pathname) &&
    !hasControlCharacter(pathname)
  );
}

function isSafePathname(pathname: string): boolean {
  return (
    hasSafePathnameSyntax(pathname) &&
    !hasUnsafePathSegments(pathname) &&
    PROTECTED_PATH.test(pathname)
  );
}

function isSafeSearch(search: string): boolean {
  return (
    (search === '' || search.startsWith('?')) &&
    !search.includes('#') &&
    !search.includes('\\') &&
    !hasControlCharacter(search)
  );
}

function isSafeHash(hash: string): boolean {
  return (
    (hash === '' || hash.startsWith('#')) &&
    !hash.includes('\\') &&
    !hasControlCharacter(hash)
  );
}

export function getSafeLoginTarget(locationState: unknown): string | null {
  if (!isRecord(locationState) || !isRecord(locationState.from)) {
    return null;
  }

  const { pathname, search, hash } = locationState.from;

  if (
    typeof pathname !== 'string' ||
    typeof search !== 'string' ||
    typeof hash !== 'string' ||
    !isSafePathname(pathname) ||
    !isSafeSearch(search) ||
    !isSafeHash(hash)
  ) {
    return null;
  }

  return `${pathname}${search}${hash}`;
}

export function getDashboardTarget(accessProfile: UserAccessProfile): string {
  return accessProfile.role === 'CLIENT'
    ? '/app/client/dashboard'
    : '/app/professional/dashboard';
}
