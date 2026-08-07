import { currentEpoch, invalidateIfCurrent } from './authEpoch';
import { notifySessionInvalidated } from './sessionInvalidation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  isErrorResponse,
  type ErrorResponse,
} from './types';

const API_PREFIX = '/api/v1';

export interface HttpRequestOptions extends Omit<RequestInit, 'credentials'> {
  /**
   * When true, a 401 response may invalidate the current auth epoch and notify
   * session-invalidation subscribers if the request's captured epoch is still current.
   * Defaults to false.
   */
  invalidateOn401?: boolean;
}

function isAbortError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'AbortError'
  );
}

function resolveApiUrl(path: string): string {
  if (!path.startsWith('/')) {
    throw new Error(`API path must be absolute relative to origin: ${path}`);
  }

  if (path.startsWith(API_PREFIX)) {
    return path;
  }

  if (path.startsWith('/api/')) {
    throw new Error(`Unsupported API path prefix: ${path}`);
  }

  return `${API_PREFIX}${path}`;
}

async function parseErrorBody(
  response: Response,
): Promise<{ body: ErrorResponse | null; unexpected: unknown }> {
  const contentType = response.headers.get('content-type') ?? '';
  const hasJsonContentType = contentType.includes('application/json');

  if (
    response.status === 204 ||
    response.headers.get('content-length') === '0'
  ) {
    return { body: null, unexpected: null };
  }

  let rawText: string;
  try {
    rawText = await response.text();
  } catch (cause) {
    return { body: null, unexpected: cause };
  }

  if (rawText.trim() === '') {
    return { body: null, unexpected: null };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(rawText) as unknown;
  } catch (cause) {
    return { body: null, unexpected: hasJsonContentType ? cause : rawText };
  }

  if (isErrorResponse(parsed)) {
    return { body: parsed, unexpected: null };
  }

  return { body: null, unexpected: parsed };
}

async function parseSuccessBody<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  const rawText = await response.text();

  if (rawText.trim() === '') {
    return undefined as T;
  }

  try {
    return JSON.parse(rawText) as T;
  } catch (cause) {
    throw new UnexpectedResponseError(
      response.status,
      response,
      cause,
      contentType.includes('application/json')
        ? 'Failed to parse JSON response body'
        : 'Unexpected non-JSON response body',
    );
  }
}

/**
 * Body observation for callers that need exact status semantics without treating
 * every 2xx as a typed success payload.
 * Does not interpret domain/business meaning.
 */
export type ObservedHttpBody =
  | { readonly kind: 'json'; readonly value: unknown }
  | { readonly kind: 'empty' }
  | {
      readonly kind: 'parse_error';
      readonly rawText: string;
      readonly cause: unknown;
    }
  | { readonly kind: 'read_error'; readonly cause: unknown };

export interface ObservedHttpResponse {
  readonly status: number;
  readonly ok: boolean;
  readonly response: Response;
  readonly body: ObservedHttpBody;
}

async function observeResponseBody(
  response: Response,
): Promise<ObservedHttpBody> {
  if (
    response.status === 204 ||
    response.headers.get('content-length') === '0'
  ) {
    return { kind: 'empty' };
  }

  let rawText: string;
  try {
    rawText = await response.text();
  } catch (cause) {
    return { kind: 'read_error', cause };
  }

  if (rawText.trim() === '') {
    return { kind: 'empty' };
  }

  try {
    return { kind: 'json', value: JSON.parse(rawText) as unknown };
  } catch (cause) {
    return { kind: 'parse_error', rawText, cause };
  }
}

/**
 * Response-aware HTTP primitive: returns status + body observation for any
 * completed HTTP exchange. Transport/abort failures still throw
 * (`NetworkError` / `AbortError`). Never maps business success from status alone.
 */
export async function observeHttpRequest(
  path: string,
  options: HttpRequestOptions = {},
): Promise<ObservedHttpResponse> {
  const { invalidateOn401 = false, ...init } = options;
  const url = resolveApiUrl(path);
  const expectedEpoch = invalidateOn401 ? currentEpoch() : null;

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      credentials: 'same-origin',
    });
  } catch (cause) {
    if (isAbortError(cause)) {
      throw cause;
    }

    throw new NetworkError(cause);
  }

  if (response.status === 401 && expectedEpoch !== null) {
    if (invalidateIfCurrent(expectedEpoch)) {
      notifySessionInvalidated();
    }
  }

  const body = await observeResponseBody(response);

  return {
    status: response.status,
    ok: response.ok,
    response,
    body,
  };
}

export async function request<T = unknown>(
  path: string,
  options: HttpRequestOptions = {},
): Promise<T> {
  const { invalidateOn401 = false, ...init } = options;
  const url = resolveApiUrl(path);
  const expectedEpoch = invalidateOn401 ? currentEpoch() : null;

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      credentials: 'same-origin',
    });
  } catch (cause) {
    if (isAbortError(cause)) {
      throw cause;
    }

    throw new NetworkError(cause);
  }

  if (response.ok) {
    try {
      return await parseSuccessBody<T>(response);
    } catch (error) {
      if (error instanceof UnexpectedResponseError) {
        throw error;
      }

      throw new UnexpectedResponseError(response.status, response, error);
    }
  }

  if (response.status === 401 && expectedEpoch !== null) {
    if (invalidateIfCurrent(expectedEpoch)) {
      notifySessionInvalidated();
    }
  }

  const { body, unexpected } = await parseErrorBody(response);

  if (body !== null) {
    throw new HttpApiError(response.status, body, response);
  }

  if (unexpected !== null && response.status !== 401) {
    throw new UnexpectedResponseError(
      response.status,
      response,
      unexpected,
      'Error response body was not a valid ErrorResponse',
    );
  }

  throw new HttpApiError(response.status, null, response);
}
