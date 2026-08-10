import {
  isAbortError,
  parseCanonicalPositiveIntegerParam,
} from '../shared/relationshipPageHelpers';

export const CLIENT_PROFILE_UNAVAILABLE_MESSAGE =
  'Profilo cliente non disponibile.';

export { isAbortError };

export function parseCanonicalClientId(
  value: string | undefined,
): number | null {
  return parseCanonicalPositiveIntegerParam(value);
}

export function formatClientBirthDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match === null) {
    return value;
  }

  return `${match[3]}/${match[2]}/${match[1]}`;
}

export function formatClientHeight(value: number): string {
  return `${new Intl.NumberFormat('it-IT', {
    maximumFractionDigits: 2,
  }).format(value)} cm`;
}
