/**
 * Stable HTTP error contract exposed by the Support Trainer backend.
 * Mirrors ErrorResponse / FieldErrorResponse on the server.
 */

export interface FieldErrorResponse {
  field?: string | null;
  code: string;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: FieldErrorResponse[];
}

export class HttpApiError extends Error {
  readonly kind = 'http' as const;
  readonly status: number;
  readonly body: ErrorResponse | null;
  readonly response: Response;

  constructor(
    status: number,
    body: ErrorResponse | null,
    response: Response,
    message?: string,
  ) {
    super(
      message ??
        body?.message ??
        `HTTP request failed with status ${String(status)}`,
    );
    this.name = 'HttpApiError';
    this.status = status;
    this.body = body;
    this.response = response;
  }
}

export class NetworkError extends Error {
  readonly kind = 'network' as const;
  readonly cause: unknown;

  constructor(cause: unknown, message = 'Network request failed') {
    super(message);
    this.name = 'NetworkError';
    this.cause = cause;
  }
}

export class UnexpectedResponseError extends Error {
  readonly kind = 'unexpected' as const;
  readonly status: number;
  readonly response: Response;
  readonly cause: unknown;

  constructor(
    status: number,
    response: Response,
    cause: unknown,
    message = 'Unexpected API response',
  ) {
    super(message);
    this.name = 'UnexpectedResponseError';
    this.status = status;
    this.response = response;
    this.cause = cause;
  }
}

export type ApiClientError =
  HttpApiError | NetworkError | UnexpectedResponseError;

export function isApiClientError(error: unknown): error is ApiClientError {
  return (
    error instanceof HttpApiError ||
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError
  );
}

export function isErrorResponse(value: unknown): value is ErrorResponse {
  if (value === null || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Record<string, unknown>;

  if (
    typeof candidate.timestamp !== 'string' ||
    typeof candidate.status !== 'number' ||
    typeof candidate.code !== 'string' ||
    typeof candidate.message !== 'string' ||
    typeof candidate.path !== 'string'
  ) {
    return false;
  }

  if (candidate.fieldErrors === undefined) {
    return true;
  }

  if (!Array.isArray(candidate.fieldErrors)) {
    return false;
  }

  return candidate.fieldErrors.every((entry) => {
    if (entry === null || typeof entry !== 'object') {
      return false;
    }

    const fieldError = entry as Record<string, unknown>;
    const hasValidField =
      fieldError.field === undefined ||
      fieldError.field === null ||
      typeof fieldError.field === 'string';

    return (
      hasValidField &&
      typeof fieldError.code === 'string' &&
      typeof fieldError.message === 'string'
    );
  });
}
