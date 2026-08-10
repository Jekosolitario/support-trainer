import {
  isAbortError,
  parseCanonicalPositiveIntegerParam,
} from '../shared/relationshipPageHelpers';

export const PROFESSIONAL_PROFILE_UNAVAILABLE_MESSAGE =
  'Profilo professionista non disponibile.';

export { isAbortError };

export function parseCanonicalProfessionalId(
  value: string | undefined,
): number | null {
  return parseCanonicalPositiveIntegerParam(value);
}

export function hasDisplayText(value: string | null): value is string {
  return value !== null && value.trim() !== '';
}

export function safeExternalHttpUrl(value: string | null): string | null {
  if (!hasDisplayText(value)) {
    return null;
  }

  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
      ? parsed.toString()
      : null;
  } catch {
    return null;
  }
}
