import { describe, expect, it } from 'vitest';

import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
} from '../../api/types';
import {
  getInviteCreateKnownErrorMessage,
  getInviteListErrorMessage,
  isAbortError,
  isAmbiguousCreateOutcome,
  isStaleAuthCreateOutcome,
} from './inviteErrors';

function apiError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-30T10:00:00Z',
    status,
    code,
    message: code,
    path: '/api/v1/invites',
  };
  return new HttpApiError(status, body, new Response(null, { status }));
}

describe('inviteErrors classification', () => {
  it.each([
    {
      name: '409 INVITE_CODE_GENERATION_FAILED',
      error: apiError(409, 'INVITE_CODE_GENERATION_FAILED'),
      ambiguous: false,
      stale: false,
      knownCreate: /Generazione non riuscita/,
    },
    {
      name: '403 PROFESSIONAL_NOT_ACTIVE',
      error: apiError(403, 'PROFESSIONAL_NOT_ACTIVE'),
      ambiguous: false,
      stale: false,
      knownCreate: /profilo professionista non è attivo/,
      listMessage: /profilo professionista non è attivo/,
    },
    {
      name: 'NetworkError',
      error: new NetworkError(new Error('offline')),
      ambiguous: true,
      stale: false,
      knownCreate: null,
    },
    {
      name: 'UnexpectedResponseError',
      error: new UnexpectedResponseError(500, new Response(), new Error('x')),
      ambiguous: true,
      stale: false,
      knownCreate: null,
    },
    {
      name: 'AbortError',
      error: new DOMException('Aborted', 'AbortError'),
      ambiguous: false,
      stale: false,
      knownCreate: null,
    },
    {
      name: 'StaleAuthOperationError',
      error: new StaleAuthOperationError(1, 2),
      ambiguous: false,
      stale: true,
      knownCreate: null,
    },
    {
      name: 'generic Error',
      error: new Error('boom'),
      ambiguous: true,
      stale: false,
      knownCreate: null,
    },
  ])('$name', ({ error, ambiguous, stale, knownCreate, listMessage }) => {
    expect(isAmbiguousCreateOutcome(error)).toBe(ambiguous);
    expect(isStaleAuthCreateOutcome(error)).toBe(stale);
    const known = getInviteCreateKnownErrorMessage(error);
    if (knownCreate === null) {
      expect(known).toBeNull();
    } else {
      expect(known).toMatch(knownCreate);
    }
    if (listMessage) {
      expect(getInviteListErrorMessage(error)).toMatch(listMessage);
    }
  });

  it('riconosce AbortError', () => {
    expect(isAbortError(new DOMException('Aborted', 'AbortError'))).toBe(true);
    expect(
      isAbortError(Object.assign(new Error('x'), { name: 'AbortError' })),
    ).toBe(true);
    expect(isAbortError(new Error('other'))).toBe(false);
  });

  it('401 ACCESS_DENIED non è classificato come known create dedicato auth', () => {
    const error = apiError(401, 'UNAUTHORIZED');
    expect(isAmbiguousCreateOutcome(error)).toBe(false);
    expect(getInviteCreateKnownErrorMessage(error)).toMatch(/Riprova/);
  });
});
