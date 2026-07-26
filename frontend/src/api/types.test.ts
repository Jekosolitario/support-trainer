import { describe, expect, it } from 'vitest';

import { isErrorResponse } from './types';

const validationError = {
  timestamp: '2026-07-26T10:00:00Z',
  status: 400,
  code: 'VALIDATION_ERROR',
  message: 'Richiesta non valida',
  path: '/api/v1/auth/login',
};

describe('isErrorResponse', () => {
  it('accetta field come stringa', () => {
    expect(
      isErrorResponse({
        ...validationError,
        fieldErrors: [
          {
            field: 'email',
            code: 'Email',
            message: 'Formato email non valido',
          },
        ],
      }),
    ).toBe(true);
  });

  it('accetta field null', () => {
    expect(
      isErrorResponse({
        ...validationError,
        fieldErrors: [
          {
            field: null,
            code: 'Valid',
            message: 'Valore non valido',
          },
        ],
      }),
    ).toBe(true);
  });

  it('accetta field assente', () => {
    expect(
      isErrorResponse({
        ...validationError,
        fieldErrors: [
          {
            code: 'Valid',
            message: 'Valore non valido',
          },
        ],
      }),
    ).toBe(true);
  });

  it('rifiuta field con un tipo non valido', () => {
    expect(
      isErrorResponse({
        ...validationError,
        fieldErrors: [
          {
            field: 42,
            code: 'Valid',
            message: 'Valore non valido',
          },
        ],
      }),
    ).toBe(false);
  });
});
