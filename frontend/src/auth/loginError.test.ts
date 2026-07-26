import { describe, expect, it } from 'vitest';

import {
  AuthTransitionInProgressError,
  PostLoginCsrfRefreshError,
  StaleAuthOperationError,
} from '../api/authApi';
import { InvalidCsrfResponseError } from '../api/csrf';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
  type FieldErrorResponse,
} from '../api/types';
import { AuthOperationNotAllowedError } from './authState';
import { getLoginErrorPresentation } from './loginError';
import { AuthConsistencyError } from './mapAccessProfile';

function apiError(
  status: number,
  code: string,
  fieldErrors?: FieldErrorResponse[],
  message = 'MESSAGGIO BACKEND DA NON ESPORRE',
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message,
    path: '/api/v1/auth/login',
    ...(fieldErrors === undefined ? {} : { fieldErrors }),
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

function expectSummary(error: unknown, summary: string): void {
  expect(getLoginErrorPresentation(error)).toEqual({
    summary,
    fieldErrors: {},
  });
}

describe('getLoginErrorPresentation', () => {
  it.each([
    [apiError(401, 'AUTHENTICATION_ERROR'), 'Email o password non corrette.'],
    [
      apiError(403, 'ACCOUNT_NOT_ACTIVE'),
      'L’account non è disponibile per l’accesso.',
    ],
    [
      apiError(403, 'EMAIL_NOT_VERIFIED'),
      'L’indirizzo email non è ancora verificato.',
    ],
  ])(
    'mappa un errore login noto senza usare il body tecnico',
    (error, copy) => {
      expectSummary(error, copy);
      expect(JSON.stringify(getLoginErrorPresentation(error))).not.toContain(
        'MESSAGGIO BACKEND',
      );
    },
  );

  it.each<readonly [string, string, string]>([
    ['email obbligatoria', 'email', 'NotBlank'],
    ['email non valida', 'email', 'Email'],
    ['email troppo lunga', 'email', 'Size'],
    ['password obbligatoria', 'password', 'NotBlank'],
    ['password troppo corta', 'password', 'Size'],
  ])('mappa VALIDATION_ERROR per %s', (_label, field, code) => {
    const presentation = getLoginErrorPresentation(
      apiError(400, 'VALIDATION_ERROR', [
        {
          field,
          code,
          message: 'COPY SERVER NON AFFIDABILE',
        },
      ]),
    );

    expect(presentation?.summary).toBeNull();
    if (field === 'email') {
      expect(presentation?.fieldErrors.email).toBeTruthy();
    } else {
      expect(presentation?.fieldErrors.password).toBeTruthy();
    }
    expect(JSON.stringify(presentation)).not.toContain('COPY SERVER');
  });

  it('mantiene i field noti e aggiunge summary per field sconosciuto', () => {
    expect(
      getLoginErrorPresentation(
        apiError(400, 'VALIDATION_ERROR', [
          { field: 'email', code: 'Email', message: 'server email' },
          { field: 'displayName', code: 'NotBlank', message: 'server unknown' },
        ]),
      ),
    ).toEqual({
      summary: 'Controlla i dati inseriti e riprova.',
      fieldErrors: {
        email: 'Inserisci un indirizzo email valido.',
      },
    });
  });

  it.each([
    ['field null', { field: null, code: 'NotBlank', message: 'server' }],
    ['field assente', { code: 'NotBlank', message: 'server' }],
    [
      'code sconosciuto',
      { field: 'password', code: 'Valid', message: 'server' },
    ],
  ])('usa il summary generico per %s', (_label, fieldError) => {
    expect(
      getLoginErrorPresentation(
        apiError(400, 'VALIDATION_ERROR', [fieldError]),
      ),
    ).toEqual({
      summary: 'Controlla i dati inseriti e riprova.',
      fieldErrors: {},
    });
  });

  it('usa il summary generico se VALIDATION_ERROR non contiene field errors', () => {
    expect(
      getLoginErrorPresentation(apiError(400, 'VALIDATION_ERROR')),
    ).toEqual({
      summary: 'Controlla i dati inseriti e riprova.',
      fieldErrors: {},
    });
  });

  it.each([
    [400, 'MALFORMED_REQUEST'],
    [403, 'CSRF_VALIDATION_FAILED'],
    [415, 'UNSUPPORTED_MEDIA_TYPE'],
    [429, 'UNKNOWN_CLIENT_ERROR'],
  ])('mappa %s %s come errore generico non credenziali', (status, code) => {
    expectSummary(apiError(status, code), 'Accesso non completato. Riprova.');
  });

  it('non espone body o message arbitrari per un 4xx senza body valido', () => {
    const error = new HttpApiError(
      400,
      null,
      new Response('testo tecnico segreto', { status: 400 }),
      'errore tecnico segreto',
    );

    const presentation = getLoginErrorPresentation(error);

    expect(presentation).toEqual({
      summary: 'Accesso non completato. Riprova.',
      fieldErrors: {},
    });
    expect(JSON.stringify(presentation)).not.toMatch(/tecnico|segreto/);
  });

  it.each([
    new Error('technical'),
    new NetworkError(new Error('offline')),
    new UnexpectedResponseError(
      200,
      new Response('invalid'),
      new Error('invalid'),
    ),
    new PostLoginCsrfRefreshError(new Error('refresh')),
    new StaleAuthOperationError(1, 2),
    new AuthTransitionInProgressError(),
    new AuthOperationNotAllowedError('login', 'authenticated'),
    new InvalidCsrfResponseError({ token: null }),
    new AuthConsistencyError('IDENTITY_MISMATCH'),
    new DOMException('aborted', 'AbortError'),
    apiError(500, 'INTERNAL_SERVER_ERROR'),
  ])('non presenta una classe indeterminata o non locale', (error) => {
    expect(getLoginErrorPresentation(error)).toBeNull();
  });
});
