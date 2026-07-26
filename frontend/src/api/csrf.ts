import { request } from './httpClient';

export interface CsrfValue {
  readonly token: string;
  readonly headerName: string;
}

interface CacheEntry {
  generation: number;
  value: CsrfValue;
}

interface InFlightEntry {
  generation: number;
  promise: Promise<CsrfValue>;
}

export class InvalidCsrfResponseError extends Error {
  readonly payload: unknown;

  constructor(payload: unknown) {
    super('CSRF response did not match the expected contract');
    this.name = 'InvalidCsrfResponseError';
    this.payload = payload;
  }
}

let csrfGeneration = 0;
let cache: CacheEntry | null = null;
let inFlight: InFlightEntry | null = null;

function isCsrfValue(value: unknown): value is CsrfValue {
  if (value === null || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.token === 'string' &&
    candidate.token.trim().length > 0 &&
    typeof candidate.headerName === 'string' &&
    candidate.headerName.trim().length > 0
  );
}

export function ensureCsrf(): Promise<CsrfValue> {
  const generation = csrfGeneration;

  if (cache?.generation === generation) {
    return Promise.resolve(cache.value);
  }

  if (inFlight?.generation === generation) {
    return inFlight.promise;
  }

  const promise = request<unknown>('/api/v1/auth/csrf', {
    invalidateOn401: false,
  })
    .then((payload) => {
      if (!isCsrfValue(payload)) {
        throw new InvalidCsrfResponseError(payload);
      }

      const value: CsrfValue = Object.freeze({
        token: payload.token,
        headerName: payload.headerName,
      });

      if (csrfGeneration === generation) {
        cache = { generation, value };
      }

      return value;
    })
    .finally(() => {
      if (inFlight?.generation === generation && inFlight.promise === promise) {
        inFlight = null;
      }
    });

  inFlight = { generation, promise };
  return promise;
}

export function clearCsrf(): void {
  cache = null;
  csrfGeneration += 1;
}

export function invalidateCsrfIfCurrent(expected: CsrfValue): boolean {
  if (
    cache === null ||
    cache.generation !== csrfGeneration ||
    cache.value !== expected
  ) {
    return false;
  }

  clearCsrf();
  return true;
}
