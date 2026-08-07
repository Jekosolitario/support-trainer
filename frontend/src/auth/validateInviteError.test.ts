import { describe, expect, it } from 'vitest';

import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
} from '../api/types';
import {
  getValidateInviteErrorPresentation,
  VALIDATE_INVITE_TEMPORARY_ERROR,
} from './validateInviteError';

function httpError(
  status: number,
  code: string,
  fieldErrors?: Array<{ field?: string | null; code: string; message: string }>,
): HttpApiError {
  return new HttpApiError(
    status,
    {
      timestamp: '2026-08-03T10:00:00Z',
      status,
      code,
      message: `backend:${code}`,
      path: '/api/v1/auth/register/client/validate-invite',
      fieldErrors,
    },
    new Response(null, { status }),
  );
}

describe('getValidateInviteErrorPresentation', () => {
  it('mappa VALIDATION_ERROR sul campo code quando conforme', () => {
    const presentation = getValidateInviteErrorPresentation(
      httpError(400, 'VALIDATION_ERROR', [
        { field: 'code', code: 'NotBlank', message: 'backend-hidden' },
      ]),
    );

    expect(presentation).toEqual({
      kind: 'invalid',
      summary: null,
      fieldError: 'Inserisci il codice invito.',
    });
  });

  it('usa fallback globale se fieldErrors VALIDATION_ERROR non sono conformi', () => {
    const presentation = getValidateInviteErrorPresentation(
      httpError(400, 'VALIDATION_ERROR', [
        { field: 'unknown', code: 'Whatever', message: 'backend-hidden' },
      ]),
    );

    expect(presentation.kind).toBe('invalid');
    expect(presentation.summary).toBe('Controlla i dati inseriti e riprova.');
    expect(presentation.summary).not.toContain('backend');
  });

  it.each([
    ['INVITE_CODE_NOT_FOUND', 'Codice invito non valido.'],
    ['INVITE_CODE_NOT_ACTIVE', 'Questo codice invito non è disponibile.'],
    [
      'INVITE_CODE_ALREADY_USED',
      'Questo codice invito è già stato utilizzato.',
    ],
    ['INVITE_CODE_EXPIRED', 'Questo codice invito è scaduto.'],
    [
      'MALFORMED_REQUEST',
      'La richiesta non è valida. Controlla i dati e riprova.',
    ],
  ] as const)('mappa %s senza esporre message backend', (code, summary) => {
    const presentation = getValidateInviteErrorPresentation(
      httpError(code === 'INVITE_CODE_NOT_FOUND' ? 404 : 400, code),
    );

    expect(presentation).toEqual({
      kind: 'invalid',
      summary,
    });
    expect(JSON.stringify(presentation)).not.toContain('backend:');
  });

  it('tratta CSRF_VALIDATION_FAILED residuo come temporary', () => {
    expect(
      getValidateInviteErrorPresentation(
        httpError(403, 'CSRF_VALIDATION_FAILED'),
      ),
    ).toEqual({
      kind: 'temporary',
      summary: VALIDATE_INVITE_TEMPORARY_ERROR,
    });
  });

  it.each([
    new NetworkError(new TypeError('offline')),
    httpError(500, 'INTERNAL_ERROR'),
    new UnexpectedResponseError(200, new Response(), { kind: 'read_error' }),
    new UnexpectedResponseError(202, new Response(), {
      kind: 'json',
      value: {},
    }),
    { mystery: true },
  ])('tratta errori temporanei/fail-closed come temporary', (error) => {
    expect(getValidateInviteErrorPresentation(error)).toEqual({
      kind: 'temporary',
      summary: VALIDATE_INVITE_TEMPORARY_ERROR,
    });
  });
});
