import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as invitesApi from '../../api/invitesApi';
import type { InviteCodeResponse } from '../../api/invitesTypes';
import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  type ErrorResponse,
} from '../../api/types';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { CREATE_OUTCOME_UNCONFIRMED_MESSAGE } from './inviteErrors';
import { ProfessionalInvitesPage } from './ProfessionalInvitesPage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

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

function invite(
  overrides: Partial<InviteCodeResponse> = {},
): InviteCodeResponse {
  return {
    id: overrides.id ?? 1,
    code: overrides.code ?? 'INV-ABCDEF1234',
    professionalId: overrides.professionalId ?? 2,
    expiresAt: overrides.expiresAt ?? '2099-01-01T12:00:00.000Z',
    used: overrides.used ?? false,
    usedAt: overrides.usedAt ?? null,
    active: overrides.active ?? true,
    createdAt: overrides.createdAt ?? '2026-07-30T10:00:00.000Z',
  };
}

function renderPage() {
  return renderWithAuthContext(
    <ProfessionalInvitesPage />,
    createAuthContextValue(
      createAuthenticatedAuthState({
        role: 'PROFESSIONAL',
        specialization: 'PERSONAL_TRAINER',
      }),
    ),
  );
}

describe('ProfessionalInvitesPage', () => {
  beforeEach(() => {
    vi.useRealTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('mostra loading poi empty; Generate OFF durante loading', async () => {
    const pending = deferred<InviteCodeResponse[]>();
    vi.spyOn(invitesApi, 'listMyInvites').mockReturnValueOnce(pending.promise);

    renderPage();

    expect(screen.getByText('Caricamento inviti…')).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();

    await act(async () => {
      pending.resolve([]);
    });

    expect(await screen.findByText(/Non hai ancora inviti/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Genera invito' })).toBeEnabled();
  });

  it('dopo load error Generate resta non disponibile', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockRejectedValueOnce(
      new NetworkError(new Error('offline')),
    );

    renderPage();

    expect(
      await screen.findByText(/Non è stato possibile caricare gli inviti/),
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeEnabled();
  });

  it('retry e generate nello stesso tick non inviano POST', async () => {
    const first = deferred<InviteCodeResponse[]>();
    const retry = deferred<InviteCodeResponse[]>();
    const listSpy = vi
      .spyOn(invitesApi, 'listMyInvites')
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(retry.promise);
    const createSpy = vi.spyOn(invitesApi, 'createInvite');

    renderPage();

    await act(async () => {
      first.reject(new NetworkError(new Error('offline')));
    });
    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeVisible();

    const retryButton = screen.getByRole('button', { name: 'Riprova' });
    const generateButton = screen.getByRole('button', {
      name: 'Genera invito',
    });

    act(() => {
      retryButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      generateButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(createSpy).not.toHaveBeenCalled();
    expect(listSpy).toHaveBeenCalledTimes(2);

    await act(async () => {
      retry.resolve([]);
    });
  });

  it('mostra stati inclusa Non disponibile senza CTA copia', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({ id: 1, code: 'INV-VALID00001' }),
      invite({
        id: 2,
        code: 'INV-USED000002',
        used: true,
        usedAt: '2026-07-29T10:00:00.000Z',
      }),
      invite({
        id: 3,
        code: 'INV-EXPIRED003',
        expiresAt: '2020-01-01T00:00:00.000Z',
      }),
      invite({ id: 4, code: 'INV-INACTIVE04', active: false }),
      invite({ id: 5, code: 'INV-BADTIME05', expiresAt: 'not-iso' }),
    ]);

    renderPage();

    expect(await screen.findByText('INV-VALID00001')).toBeVisible();
    expect(screen.getByText('Valido')).toBeVisible();
    expect(screen.getByText('Usato')).toBeVisible();
    expect(screen.getByText('Scaduto')).toBeVisible();
    expect(screen.getByText('Non attivo')).toBeVisible();
    expect(screen.getByLabelText('Stato Non disponibile')).toBeVisible();

    expect(
      screen.getByRole('button', { name: 'Copia codice INV-VALID00001' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Copia codice INV-BADTIME05' }),
    ).not.toBeInTheDocument();
  });

  it('nuovo dataset dopo clock avanzato riallinea nowMs (N-01)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.parse('2026-07-30T12:00:00.000Z'));

    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([]);
    renderPage();
    expect(await screen.findByText(/Non hai ancora inviti/)).toBeVisible();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    vi.spyOn(invitesApi, 'createInvite').mockResolvedValueOnce(
      invite({
        id: 9,
        code: 'INV-FRESH00009',
        expiresAt: '2026-07-30T12:00:30.000Z',
      }),
    );

    await act(async () => {
      screen.getByRole('button', { name: 'Genera invito' }).click();
    });

    expect(await screen.findByText('INV-FRESH00009')).toBeVisible();
    const card = screen
      .getByText('INV-FRESH00009')
      .closest('li') as HTMLElement;
    expect(within(card).getByText('Scaduto')).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Copia codice INV-FRESH00009' }),
    ).not.toBeInTheDocument();
  });

  it('timer porta alla prima scadenza poi alla successiva', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.parse('2026-07-30T12:00:00.000Z'));

    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({
        id: 1,
        code: 'INV-FIRST00001',
        expiresAt: '2026-07-30T12:00:00.100Z',
      }),
      invite({
        id: 2,
        code: 'INV-SECOND0002',
        expiresAt: '2026-07-30T12:00:00.250Z',
      }),
    ]);

    renderPage();
    expect(await screen.findByText('INV-FIRST00001')).toBeVisible();

    const first = screen
      .getByText('INV-FIRST00001')
      .closest('li') as HTMLElement;
    const second = screen
      .getByText('INV-SECOND0002')
      .closest('li') as HTMLElement;
    expect(within(first).getByText('Valido')).toBeVisible();
    expect(within(second).getByText('Valido')).toBeVisible();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(120);
    });
    expect(within(first).getByText('Scaduto')).toBeVisible();
    expect(within(second).getByText('Valido')).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Copia codice INV-FIRST00001' }),
    ).not.toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(200);
    });
    expect(within(second).getByText('Scaduto')).toBeVisible();
  });

  it('visibilitychange dopo salto temporale aggiorna lo stato', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.parse('2026-07-30T12:00:00.000Z'));

    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({
        code: 'INV-VIS0000001',
        expiresAt: '2026-07-30T12:00:05.000Z',
      }),
    ]);

    renderPage();
    const card = (await screen.findByText('INV-VIS0000001')).closest(
      'li',
    ) as HTMLElement;
    expect(within(card).getByText('Valido')).toBeVisible();

    await act(async () => {
      vi.setSystemTime(Date.parse('2026-07-30T12:00:06.000Z'));
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        get: () => 'visible',
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    expect(within(card).getByText('Scaduto')).toBeVisible();
  });

  it('unmount cancella il timer one-shot della prossima scadenza', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.parse('2026-07-30T12:00:00.000Z'));

    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout');

    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({
        code: 'INV-UNMOUNT001',
        expiresAt: '2026-07-30T12:00:00.200Z',
      }),
    ]);

    const { unmount } = renderPage();
    expect(await screen.findByText('INV-UNMOUNT001')).toBeVisible();

    await waitFor(() => {
      expect(vi.getTimerCount()).toBeGreaterThan(0);
    });
    const timersWhileMounted = vi.getTimerCount();
    const clearCallsBeforeUnmount = clearTimeoutSpy.mock.calls.length;

    unmount();

    expect(vi.getTimerCount()).toBeLessThan(timersWhileMounted);
    expect(clearTimeoutSpy.mock.calls.length).toBeGreaterThan(
      clearCallsBeforeUnmount,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(screen.queryByText('INV-UNMOUNT001')).not.toBeInTheDocument();
  });

  it('unmount rimuove il listener visibilitychange installato dalla pagina', async () => {
    const addSpy = vi.spyOn(document, 'addEventListener');
    const removeSpy = vi.spyOn(document, 'removeEventListener');

    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([]);

    const { unmount } = renderPage();
    expect(await screen.findByText(/Non hai ancora inviti/)).toBeVisible();

    const visibilityAdd = addSpy.mock.calls.find(
      (call) => call[0] === 'visibilitychange',
    );
    expect(visibilityAdd).toBeDefined();
    const pageHandler = visibilityAdd?.[1];
    expect(pageHandler).toBeTypeOf('function');

    unmount();

    expect(
      removeSpy.mock.calls.some(
        (call) => call[0] === 'visibilitychange' && call[1] === pageHandler,
      ),
    ).toBe(true);
  });

  it('GET lista ancora in-flight dopo prepend 201 non sostituisce il nuovo invito', async () => {
    const staleGet = deferred<InviteCodeResponse[]>();
    const ownerGet = deferred<InviteCodeResponse[]>();
    const listSpy = vi
      .spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new NetworkError(new Error('auto-reconcile-fail')))
      .mockReturnValueOnce(staleGet.promise)
      .mockReturnValueOnce(ownerGet.promise);

    vi.spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockResolvedValueOnce(invite({ id: 40, code: 'INV-CREATED040' }));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();

    const refreshButton = screen.getByRole('button', {
      name: 'Aggiorna elenco',
    });
    const listCallsBeforeOverlap = listSpy.mock.calls.length;

    act(() => {
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(listSpy.mock.calls.length).toBe(listCallsBeforeOverlap + 2);
    expect(listSpy).toHaveBeenNthCalledWith(
      listCallsBeforeOverlap + 1,
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
    expect(listSpy).toHaveBeenNthCalledWith(
      listCallsBeforeOverlap + 2,
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );

    await act(async () => {
      ownerGet.resolve([invite({ id: 7, code: 'INV-OWNER00007' })]);
    });
    expect(await screen.findByText('INV-OWNER00007')).toBeVisible();
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Genera invito' }),
      ).toBeEnabled();
    });

    await user.click(screen.getByRole('button', { name: 'Genera invito' }));
    expect(await screen.findByText('INV-CREATED040')).toBeVisible();
    expect(await screen.findByText('Invito generato')).toBeVisible();

    await act(async () => {
      staleGet.resolve([invite({ id: 99, code: 'INV-STALE00099' })]);
    });

    expect(screen.getByText('INV-CREATED040')).toBeVisible();
    expect(screen.queryByText('INV-STALE00099')).not.toBeInTheDocument();
  });

  it('genera con prepend e dedupe senza refetch', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({ id: 2, code: 'INV-OLD0000002' }),
    ]);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockResolvedValueOnce(invite({ id: 3, code: 'INV-NEW0000003' }));

    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('INV-OLD0000002')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(await screen.findByText('Invito generato')).toBeVisible();
    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(invitesApi.listMyInvites).toHaveBeenCalledTimes(1);
    const codes = screen.getAllByText(/^INV-/).map((n) => n.textContent);
    expect(codes[0]).toBe('INV-NEW0000003');
  });

  it('due click sincroni → un solo POST', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([]);
    const pending = deferred<InviteCodeResponse>();
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockReturnValueOnce(pending.promise);

    renderPage();
    expect(
      await screen.findByRole('button', { name: 'Genera invito' }),
    ).toBeEnabled();

    const button = screen.getByRole('button', { name: 'Genera invito' });
    act(() => {
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(createSpy).toHaveBeenCalledTimes(1);

    await act(async () => {
      pending.resolve(invite({ id: 11, code: 'INV-ONLYONE011' }));
    });
    expect(await screen.findByText('INV-ONLYONE011')).toBeVisible();
  });

  it('409 known-error riabilita Generate', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([]);
    vi.spyOn(invitesApi, 'createInvite').mockRejectedValueOnce(
      apiError(409, 'INVITE_CODE_GENERATION_FAILED'),
    );

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(await screen.findByText(/Generazione non riuscita/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Genera invito' })).toBeEnabled();
  });

  it('StaleAuthOperationError: nessun successo, nessun known-error, Create bloccata', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([]);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new StaleAuthOperationError(1, 2));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    await waitFor(() => {
      expect(screen.queryByText('Generazione…')).not.toBeInTheDocument();
    });
    expect(screen.queryByText('Invito generato')).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Generazione non riuscita/),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Non è stato possibile generare/),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();

    act(() => {
      screen
        .getByRole('button', { name: 'Genera invito' })
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(createSpy).toHaveBeenCalledTimes(1);
  });

  it('esito ambiguo → reconcile success unlock', async () => {
    const reconcile = deferred<InviteCodeResponse[]>();
    vi.spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([])
      .mockReturnValueOnce(reconcile.promise);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE),
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();

    await act(async () => {
      reconcile.resolve([invite({ id: 20, code: 'INV-RECONCILE20' })]);
    });

    expect(await screen.findByText('INV-RECONCILE20')).toBeVisible();
    await waitFor(() => {
      expect(
        screen.queryByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: 'Genera invito' })).toBeEnabled();
    expect(createSpy).toHaveBeenCalledTimes(1);
  });

  it('reconcile failure mantiene lock; retry manuale sblocca', async () => {
    vi.spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockResolvedValueOnce([invite({ id: 21, code: 'INV-AFTERRETRY21' })]);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();

    act(() => {
      screen
        .getByRole('button', { name: 'Genera invito' })
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(createSpy).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Aggiorna elenco' }));
    expect(await screen.findByText('INV-AFTERRETRY21')).toBeVisible();
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Genera invito' }),
      ).toBeEnabled();
    });
  });

  it('reconciliation retry dopo failure sblocca senza POST aggiuntivo', async () => {
    vi.spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockResolvedValueOnce([invite({ id: 77, code: 'INV-OWNER00077' })]);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();
    expect(createSpy).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Aggiorna elenco' }));
    expect(await screen.findByText('INV-OWNER00077')).toBeVisible();
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Genera invito' }),
      ).toBeEnabled();
    });
    expect(createSpy).toHaveBeenCalledTimes(1);
  });

  it('reconcile B success poi failure stale di A: nessun relock né ambiguity', async () => {
    const reconcileA = deferred<InviteCodeResponse[]>();
    const reconcileB = deferred<InviteCodeResponse[]>();
    const listSpy = vi
      .spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new NetworkError(new Error('auto-reconcile-fail')))
      .mockReturnValueOnce(reconcileA.promise)
      .mockReturnValueOnce(reconcileB.promise);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')));

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Genera invito' });
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();
    expect(screen.getByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE)).toBeVisible();

    const refreshButton = screen.getByRole('button', {
      name: 'Aggiorna elenco',
    });
    const callsBeforeOverlap = listSpy.mock.calls.length;

    act(() => {
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(listSpy.mock.calls.length).toBe(callsBeforeOverlap + 2);
    expect(screen.getByText(/Aggiornamento elenco/)).toBeVisible();

    await act(async () => {
      reconcileB.resolve([invite({ id: 88, code: 'INV-OWNER-B088' })]);
    });

    expect(await screen.findByText('INV-OWNER-B088')).toBeVisible();
    await waitFor(() => {
      expect(
        screen.queryByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: 'Genera invito' })).toBeEnabled();
    expect(
      screen.queryByRole('button', { name: 'Aggiorna elenco' }),
    ).not.toBeInTheDocument();

    await act(async () => {
      reconcileA.reject(new NetworkError(new Error('stale-A-fail')));
    });

    expect(screen.getByText('INV-OWNER-B088')).toBeVisible();
    expect(
      screen.queryByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Non è stato possibile caricare gli inviti/),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Genera invito' })).toBeEnabled();
    expect(
      screen.queryByRole('button', { name: 'Aggiorna elenco' }),
    ).not.toBeInTheDocument();
    expect(createSpy).toHaveBeenCalledTimes(1);
  });

  it('reconcile B failure poi success stale di A: nessun unlock né replace lista', async () => {
    const reconcileA = deferred<InviteCodeResponse[]>();
    const reconcileB = deferred<InviteCodeResponse[]>();
    const listSpy = vi
      .spyOn(invitesApi, 'listMyInvites')
      .mockResolvedValueOnce([invite({ id: 1, code: 'INV-BEFORE0001' })])
      .mockRejectedValueOnce(new NetworkError(new Error('auto-reconcile-fail')))
      .mockReturnValueOnce(reconcileA.promise)
      .mockReturnValueOnce(reconcileB.promise);
    const createSpy = vi
      .spyOn(invitesApi, 'createInvite')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')));

    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('INV-BEFORE0001')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Genera invito' }));

    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();

    const refreshButton = screen.getByRole('button', {
      name: 'Aggiorna elenco',
    });
    const callsBeforeOverlap = listSpy.mock.calls.length;

    act(() => {
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      refreshButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(listSpy.mock.calls.length).toBe(callsBeforeOverlap + 2);

    await act(async () => {
      reconcileB.reject(new NetworkError(new Error('owner-B-fail')));
    });

    expect(
      await screen.findByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE),
    ).toBeVisible();
    expect(
      await screen.findByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();
    expect(screen.queryByText('INV-STALE-A055')).not.toBeInTheDocument();

    await act(async () => {
      reconcileA.resolve([invite({ id: 55, code: 'INV-STALE-A055' })]);
    });

    expect(screen.queryByText('INV-STALE-A055')).not.toBeInTheDocument();
    expect(screen.queryByText('INV-BEFORE0001')).not.toBeInTheDocument();
    expect(screen.getByText(CREATE_OUTCOME_UNCONFIRMED_MESSAGE)).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Genera invito' }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', { name: 'Aggiorna elenco' }),
    ).toBeVisible();

    act(() => {
      screen
        .getByRole('button', { name: 'Genera invito' })
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(createSpy).toHaveBeenCalledTimes(1);
  });

  it('copia codice Valido success/failure', async () => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValueOnce([
      invite({ code: 'INV-COPY000001' }),
    ]);
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });

    renderPage();
    await screen.findByText('INV-COPY000001');

    await act(async () => {
      screen
        .getByRole('button', { name: 'Copia codice INV-COPY000001' })
        .click();
    });
    expect(await screen.findByText('Codice copiato')).toBeVisible();
    expect(writeText).toHaveBeenCalledWith('INV-COPY000001');

    writeText.mockRejectedValueOnce(new Error('denied'));
    await act(async () => {
      screen
        .getByRole('button', { name: 'Copia codice INV-COPY000001' })
        .click();
    });
    expect(await screen.findByText(/Copia non riuscita/)).toBeVisible();
  });
});
