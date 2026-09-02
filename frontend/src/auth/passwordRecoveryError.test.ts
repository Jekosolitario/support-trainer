import { describe, expect, it } from 'vitest';

import { StaleAuthOperationError } from '../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
} from '../api/types';
import {
  getPasswordRecoveryConfirmPresentation,
  getPasswordRecoveryRequestPresentation,
} from './passwordRecoveryError';

function apiError(
  status: number,
  code: string,
  fieldErrors?: ErrorResponse['fieldErrors'],
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-31T10:00:00Z',
    status,
    code,
    message: 'MESSAGGIO BACKEND NON VISIBILE',
    path: '/api/v1/auth/password-recovery/request',
    ...(fieldErrors === undefined ? {} : { fieldErrors }),
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

describe('getPasswordRecoveryRequestPresentation', () => {
  it('mappa VALIDATION_ERROR email', () => {
    expect(
      getPasswordRecoveryRequestPresentation(
        apiError(400, 'VALIDATION_ERROR', [
          { field: 'email', code: 'Email', message: 'hidden' },
        ]),
      ),
    ).toEqual({
      kind: 'email',
      email: 'Inserisci un indirizzo email valido.',
    });
  });

  it('mappa errori tecnici senza informazioni account', () => {
    expect(
      getPasswordRecoveryRequestPresentation(new NetworkError('down')),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile inviare la richiesta. Riprova.',
    });
    expect(
      getPasswordRecoveryRequestPresentation(new StaleAuthOperationError(1, 2)),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile inviare la richiesta. Riprova.',
    });
    expect(
      getPasswordRecoveryRequestPresentation(apiError(500, 'INTERNAL')),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile inviare la richiesta. Riprova.',
    });
  });
});

describe('getPasswordRecoveryConfirmPresentation', () => {
  it('mappa il code token in uno stato unico', () => {
    expect(
      getPasswordRecoveryConfirmPresentation(
        apiError(400, 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED'),
      ),
    ).toEqual({
      kind: 'invalid-or-expired',
      summary: 'Questo link non è valido o non è più utilizzabile.',
    });
  });

  it('mappa VALIDATION_ERROR newPassword senza trattarlo come token', () => {
    expect(
      getPasswordRecoveryConfirmPresentation(
        apiError(400, 'VALIDATION_ERROR', [
          { field: 'newPassword', code: 'Pattern', message: 'hidden' },
        ]),
      ),
    ).toEqual({
      kind: 'validation',
      password:
        'La password deve contenere almeno una maiuscola, un numero e un carattere speciale.',
      summary: null,
    });
  });

  it('non converte un 400 generico in invalid token', () => {
    expect(
      getPasswordRecoveryConfirmPresentation(apiError(400, 'SOMETHING_ELSE')),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile aggiornare la password. Riprova.',
    });
  });

  it('mappa network e unexpected come retry tecnico', () => {
    expect(
      getPasswordRecoveryConfirmPresentation(new NetworkError('down')),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile aggiornare la password. Riprova.',
    });
    expect(
      getPasswordRecoveryConfirmPresentation(
        new UnexpectedResponseError(
          502,
          new Response(null, { status: 502 }),
          'bad',
        ),
      ),
    ).toEqual({
      kind: 'technical',
      summary: 'Non è stato possibile aggiornare la password. Riprova.',
    });
  });
});
