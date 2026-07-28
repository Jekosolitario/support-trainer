import { describe, expect, it } from 'vitest';

import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
  type FieldErrorResponse,
} from '../../../api/types';
import { getProfileErrorPresentation } from './profileError';

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
    path: '/api/v1/me/profile',
    ...(fieldErrors === undefined ? {} : { fieldErrors }),
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

describe('getProfileErrorPresentation', () => {
  it('mappa VALIDATION_ERROR sui campi senza esporre messaggi grezzi', () => {
    const presentation = getProfileErrorPresentation(
      apiError(400, 'VALIDATION_ERROR', [
        { field: 'firstName', code: 'Size', message: 'raw' },
        { field: 'instagramUrl', code: 'Pattern', message: 'raw' },
        { field: 'birthDate', code: 'Past', message: 'raw' },
      ]),
    );

    expect(presentation.summary).toBeNull();
    expect(presentation.fieldErrors).toEqual({
      firstName: 'Il nome non può superare 100 caratteri.',
      instagramUrl: 'L’URL Instagram deve iniziare con http:// o https://.',
      birthDate: 'La data di nascita deve essere nel passato.',
    });
    expect(JSON.stringify(presentation)).not.toContain('MESSAGGIO BACKEND');
    expect(JSON.stringify(presentation)).not.toContain('VALIDATION_ERROR');
  });

  it('usa summary quando VALIDATION_ERROR non ha fieldErrors mappabili', () => {
    expect(
      getProfileErrorPresentation(apiError(400, 'VALIDATION_ERROR', [])),
    ).toEqual({
      summary: 'Controlla i dati inseriti e riprova.',
      fieldErrors: {},
    });
  });

  it('mappa errori globali di profilo e operational status', () => {
    expect(
      getProfileErrorPresentation(apiError(400, 'PROFILE_FIELDS_NOT_ALLOWED')),
    ).toEqual({
      summary: 'Alcuni campi non sono consentiti per il tuo profilo.',
      fieldErrors: {},
    });

    expect(
      getProfileErrorPresentation(
        apiError(400, 'INVALID_OPERATIONAL_STATUS'),
        'operational-status',
      ),
    ).toEqual({
      summary: 'Stato operativo non valido.',
      fieldErrors: {},
    });

    expect(
      getProfileErrorPresentation(apiError(400, 'INVALID_REQUEST')),
    ).toEqual({
      summary: 'I dati inviati non sono validi. Controlla i campi e riprova.',
      fieldErrors: {},
    });

    expect(
      getProfileErrorPresentation(apiError(400, 'MALFORMED_REQUEST')),
    ).toEqual({
      summary: 'La richiesta non è valida. Controlla i dati e riprova.',
      fieldErrors: {},
    });
  });

  it('fornisce fallback per network e errori inattesi senza codici raw', () => {
    const network = getProfileErrorPresentation(
      new NetworkError(new Error('offline')),
    );
    const unexpected = getProfileErrorPresentation(
      new UnexpectedResponseError(502, new Response(''), 'boom'),
    );
    const unknown = getProfileErrorPresentation(new Error('boom'));

    expect(network).toEqual({
      summary: 'Operazione non completata. Riprova.',
      fieldErrors: {},
    });
    expect(unexpected).toEqual({
      summary: 'Operazione non completata. Riprova.',
      fieldErrors: {},
    });
    expect(unknown).toEqual({
      summary: 'Operazione non completata. Riprova.',
      fieldErrors: {},
    });
    expect(JSON.stringify(network)).not.toContain('NetworkError');
  });
});
