import { currentEpoch } from './authEpoch';
import {
  clearCsrf,
  ensureCsrf,
  invalidateCsrfIfCurrent,
  type CsrfValue,
} from './csrf';
import {
  observeHttpRequest,
  request,
  type HttpRequestOptions,
  type ObservedHttpResponse,
} from './httpClient';
import { HttpApiError, isErrorResponse } from './types';

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

function isObservedCsrfValidationFailure(
  observed: ObservedHttpResponse,
): boolean {
  return (
    observed.status === 403 &&
    observed.body.kind === 'json' &&
    isErrorResponse(observed.body.value) &&
    observed.body.value.code === 'CSRF_VALIDATION_FAILED'
  );
}

function withCsrfHeaders(
  requestOptions: HttpRequestOptions,
  csrf: CsrfValue,
): HttpRequestOptions {
  const headers = new Headers(requestOptions.headers);
  headers.set(csrf.headerName, csrf.token);

  return {
    ...requestOptions,
    headers,
  };
}

function requestWithCsrf<T>(
  path: string,
  requestOptions: HttpRequestOptions,
  csrf: CsrfValue,
): Promise<T> {
  return request<T>(path, withCsrfHeaders(requestOptions, csrf));
}

function observeWithCsrf(
  path: string,
  requestOptions: HttpRequestOptions,
  csrf: CsrfValue,
): Promise<ObservedHttpResponse> {
  return observeHttpRequest(path, withCsrfHeaders(requestOptions, csrf));
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

/**
 * CSRF-protected mutation that returns the observed HTTP exchange instead of
 * interpreting 2xx as typed success. Preserves one CSRF retry and epoch fencing.
 * Transport/abort and stale-epoch failures still throw.
 */
export async function performCsrfObservedMutation(
  path: string,
  options: CsrfMutationOptions = {},
): Promise<ObservedHttpResponse> {
  const expectedEpoch = currentEpoch();
  const { invalidateCsrfOnCommit = false, ...requestOptions } = options;
  const initialCsrf = await ensureCurrentCsrf(expectedEpoch);

  const firstObservation = await observeWithCsrf(
    path,
    requestOptions,
    initialCsrf,
  );

  if (!isObservedCsrfValidationFailure(firstObservation)) {
    if (firstObservation.ok) {
      commitProtocolSideEffects(invalidateCsrfOnCommit);
    }
    assertCurrentEpoch(expectedEpoch);
    return firstObservation;
  }

  assertCurrentEpoch(expectedEpoch);
  invalidateCsrfIfCurrent(initialCsrf);

  const refreshedCsrf = await ensureCurrentCsrf(expectedEpoch);
  assertCurrentEpoch(expectedEpoch);

  const secondObservation = await observeWithCsrf(
    path,
    requestOptions,
    refreshedCsrf,
  );
  if (secondObservation.ok) {
    commitProtocolSideEffects(invalidateCsrfOnCommit);
  }
  assertCurrentEpoch(expectedEpoch);
  return secondObservation;
}
