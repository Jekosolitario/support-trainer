import { describe, expect, it } from 'vitest';

import { StaleAuthOperationError } from '../api/csrfMutation';
import type { ObservedHttpBody, ObservedHttpResponse } from '../api/httpClient';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
} from '../api/types';
import {
  CLIENT_REGISTER_KNOWN_FAILURES,
  classifyRegisterClientObservation,
  classifyRegisterClientThrown,
} from './clientOnboardingOutcome';

function observed(
  status: number,
  body: ObservedHttpBody,
): ObservedHttpResponse {
  return {
    status,
    ok: status >= 200 && status < 300,
    response: new Response(null, { status }),
    body,
  };
}

function errorBody(code: string, status: number): ErrorResponse {
  return {
    timestamp: '2026-07-31T10:00:00Z',
    status,
    code,
    message: code,
    path: '/api/v1/auth/register/client',
  };
}

describe('classifyRegisterClientObservation', () => {
  it('accetta esattamente HTTP 202 indipendentemente dal body', () => {
    expect(
      classifyRegisterClientObservation(
        observed(202, { kind: 'json', value: { message: 'neutro' } }),
      ),
    ).toEqual({ kind: 'accepted' });

    expect(
      classifyRegisterClientObservation(observed(202, { kind: 'empty' })),
    ).toEqual({ kind: 'accepted' });

    expect(
      classifyRegisterClientObservation(
        observed(202, {
          kind: 'parse_error',
          rawText: '{',
          cause: new SyntaxError('bad'),
        }),
      ),
    ).toEqual({ kind: 'accepted' });

    expect(
      classifyRegisterClientObservation(
        observed(202, {
          kind: 'read_error',
          cause: new TypeError('body stream failed'),
        }),
      ),
    ).toEqual({ kind: 'accepted' });
  });

  it('tratta 2xx anomali come ambiguous', () => {
    for (const status of [200, 201, 204]) {
      expect(
        classifyRegisterClientObservation(
          observed(status, { kind: 'json', value: { message: 'oops' } }),
        ).kind,
      ).toBe('ambiguous');
    }
  });

  it.each(CLIENT_REGISTER_KNOWN_FAILURES)(
    'classifica $status + $code come known_failure',
    ({ status, code }) => {
      const outcome = classifyRegisterClientObservation(
        observed(status, { kind: 'json', value: errorBody(code, status) }),
      );
      expect(outcome.kind).toBe('known_failure');
      if (outcome.kind === 'known_failure') {
        expect(outcome.error.status).toBe(status);
        expect(outcome.error.body?.code).toBe(code);
      }
    },
  );

  it('classifica 500 + VALIDATION_ERROR come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(500, {
          kind: 'json',
          value: errorBody('VALIDATION_ERROR', 500),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica 500 + INVITE_CODE_EXPIRED come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(500, {
          kind: 'json',
          value: errorBody('INVITE_CODE_EXPIRED', 500),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica 401 + INVITE_CODE_EXPIRED come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(401, {
          kind: 'json',
          value: errorBody('INVITE_CODE_EXPIRED', 401),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica 409 + INVITE_CODE_NOT_FOUND come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(409, {
          kind: 'json',
          value: errorBody('INVITE_CODE_NOT_FOUND', 409),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica code sconosciuto su 400 come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(400, {
          kind: 'json',
          value: errorBody('SOMETHING_ELSE', 400),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica status/body.status incoerenti come ambiguous', () => {
    expect(
      classifyRegisterClientObservation(
        observed(400, {
          kind: 'json',
          value: errorBody('INVITE_CODE_EXPIRED', 500),
        }),
      ).kind,
    ).toBe('ambiguous');
  });

  it('classifica qualunque 5xx come ambiguous', () => {
    for (const status of [500, 502, 503]) {
      expect(
        classifyRegisterClientObservation(
          observed(status, {
            kind: 'json',
            value: errorBody('INTERNAL_SERVER_ERROR', status),
          }),
        ).kind,
      ).toBe('ambiguous');
    }
  });
});

describe('classifyRegisterClientThrown', () => {
  it('mappa HttpApiError allowlisted status+code a known_failure', () => {
    const error = new HttpApiError(
      400,
      errorBody('INVITE_CODE_EXPIRED', 400),
      new Response(null, { status: 400 }),
    );
    expect(classifyRegisterClientThrown(error)).toEqual({
      kind: 'known_failure',
      error,
    });
  });

  it('non tratta HttpApiError con status incoerente come known', () => {
    const error = new HttpApiError(
      500,
      errorBody('VALIDATION_ERROR', 500),
      new Response(null, { status: 500 }),
    );
    expect(classifyRegisterClientThrown(error).kind).toBe('ambiguous');
  });

  it.each([
    ['NetworkError', new NetworkError(new TypeError('offline'))],
    [
      'UnexpectedResponseError',
      new UnexpectedResponseError(
        502,
        new Response(null, { status: 502 }),
        null,
      ),
    ],
    ['StaleAuthOperationError', new StaleAuthOperationError(1, 2)],
    ['unknown', new Error('boom')],
  ])('classifica %s come ambiguous', (_label, error) => {
    expect(classifyRegisterClientThrown(error).kind).toBe('ambiguous');
  });

  it('classifica stale potenzialmente post-dispatch come ambiguous', () => {
    const outcome = classifyRegisterClientThrown(
      new StaleAuthOperationError(3, 4),
    );
    expect(outcome).toEqual({
      kind: 'ambiguous',
      cause: expect.any(StaleAuthOperationError),
    });
  });
});
