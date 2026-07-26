import { currentEpoch } from './authEpoch';
import {
  clearCsrf,
  ensureCsrf,
  invalidateCsrfIfCurrent,
  type CsrfValue,
} from './csrf';
import { request, type HttpRequestOptions } from './httpClient';
import { HttpApiError } from './types';

export interface CsrfMutationOptions extends HttpRequestOptions {
  readonly invalidateCsrfOnCommit?: boolean;
}

export class StaleAuthOperationError extends Error {
  readonly expectedEpoch: number;
  readonly actualEpoch: number;

  constructor(expectedEpoch: number, actualEpoch: number) {
    super(
      `Authentication lifecycle is stale (expected epoch ${String(expectedEpoch)}, current epoch ${String(actualEpoch)})`,
    );
    this.name = 'StaleAuthOperationError';
    this.expectedEpoch = expectedEpoch;
    this.actualEpoch = actualEpoch;
  }
}

function assertCurrentEpoch(expectedEpoch: number): void {
  const actualEpoch = currentEpoch();

  if (actualEpoch !== expectedEpoch) {
    throw new StaleAuthOperationError(expectedEpoch, actualEpoch);
  }
}

async function ensureCurrentCsrf(expectedEpoch: number): Promise<CsrfValue> {
  try {
    const csrf = await ensureCsrf();
    assertCurrentEpoch(expectedEpoch);
    return csrf;
  } catch (error) {
    assertCurrentEpoch(expectedEpoch);
    throw error;
  }
}

function isCsrfValidationFailure(error: unknown): error is HttpApiError {
  return (
    error instanceof HttpApiError &&
    error.status === 403 &&
    error.body?.code === 'CSRF_VALIDATION_FAILED'
  );
}

function requestWithCsrf<T>(
  path: string,
  requestOptions: HttpRequestOptions,
  csrf: CsrfValue,
): Promise<T> {
  const headers = new Headers(requestOptions.headers);
  headers.set(csrf.headerName, csrf.token);

  return request<T>(path, {
    ...requestOptions,
    headers,
  });
}

function commitProtocolSideEffects(invalidateCsrfOnCommit: boolean): void {
  if (invalidateCsrfOnCommit) {
    clearCsrf();
  }
}

export async function performCsrfMutation<T = unknown>(
  path: string,
  options: CsrfMutationOptions = {},
): Promise<T> {
  const expectedEpoch = currentEpoch();
  const { invalidateCsrfOnCommit = false, ...requestOptions } = options;
  const initialCsrf = await ensureCurrentCsrf(expectedEpoch);

  try {
    const result = await requestWithCsrf<T>(path, requestOptions, initialCsrf);
    commitProtocolSideEffects(invalidateCsrfOnCommit);
    assertCurrentEpoch(expectedEpoch);
    return result;
  } catch (error) {
    assertCurrentEpoch(expectedEpoch);

    if (!isCsrfValidationFailure(error)) {
      throw error;
    }
  }

  assertCurrentEpoch(expectedEpoch);
  invalidateCsrfIfCurrent(initialCsrf);

  const refreshedCsrf = await ensureCurrentCsrf(expectedEpoch);
  assertCurrentEpoch(expectedEpoch);

  try {
    const result = await requestWithCsrf<T>(
      path,
      requestOptions,
      refreshedCsrf,
    );
    commitProtocolSideEffects(invalidateCsrfOnCommit);
    assertCurrentEpoch(expectedEpoch);
    return result;
  } catch (error) {
    assertCurrentEpoch(expectedEpoch);
    throw error;
  }
}
