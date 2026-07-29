import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  StrictMode,
  useLayoutEffect,
  useState,
  type ReactElement,
} from 'react';
import { flushSync } from 'react-dom';
import {
  BrowserRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import { advanceEpoch, currentEpoch } from '../../api/authEpoch';
import { clearCsrf } from '../../api/csrf';
import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type ErrorResponse,
} from '../../api/types';
import { VerifyEmailPage } from './VerifyEmailPage';

function httpError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-29T10:00:00Z',
    status,
    code,
    message: code,
    path: '/api/v1/auth/email-verification/confirm',
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="router-location">
      {JSON.stringify({
        pathname: location.pathname,
        search: location.search,
        hash: location.hash,
        state: location.state,
      })}
    </output>
  );
}

function SeedRouterStateThenVerify({ state }: { readonly state: unknown }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [ready, setReady] = useState(false);

  useLayoutEffect(() => {
    // Defer flushSync out of the layout phase (React 19 restriction).
    queueMicrotask(() => {
      flushSync(() => {
        navigate(
          {
            pathname: location.pathname,
            search: location.search,
            hash: location.hash,
          },
          { replace: true, state },
        );
      });
      setReady(true);
    });
    // Seed once from the initial URL; do not re-run after hash sanitization.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- one-shot seed
  }, []);

  if (!ready) {
    return <LocationProbe />;
  }

  return (
    <>
      <VerifyEmailPage />
      <LocationProbe />
    </>
  );
}

function renderVerify(
  hash: string,
  options: {
    readonly strictMode?: boolean;
    readonly search?: string;
    readonly state?: unknown;
    readonly seedState?: boolean;
  } = {},
) {
  const search = options.search ?? '';
  window.history.replaceState(null, '', `/verify-email${search}${hash}`);

  const page =
    options.seedState === true ? (
      <SeedRouterStateThenVerify state={options.state} />
    ) : (
      <>
        <VerifyEmailPage />
        <LocationProbe />
      </>
    );

  const tree = (
    <BrowserRouter useTransitions={false}>
      <Routes>
        <Route path="/verify-email" element={page} />
      </Routes>
    </BrowserRouter>
  );

  return render(
    options.strictMode === false ? tree : <StrictMode>{tree}</StrictMode>,
  );
}

function readRouterLocation(): {
  pathname: string;
  search: string;
  hash: string;
  state: unknown;
} {
  return JSON.parse(
    screen.getByTestId('router-location').textContent ?? '{}',
  ) as {
    pathname: string;
    search: string;
    hash: string;
    state: unknown;
  };
}

function SameMountHarness(): ReactElement {
  const navigate = useNavigate();

  return (
    <>
      <VerifyEmailPage />
      <LocationProbe />
      <button
        type="button"
        onClick={() => {
          navigate(
            { pathname: '/verify-email', hash: '#token=token-B' },
            { replace: true, state: null },
          );
        }}
      >
        Go B
      </button>
    </>
  );
}

function renderSameMount(initialHash: string) {
  window.history.replaceState(null, '', `/verify-email${initialHash}`);
  return render(
    <StrictMode>
      <BrowserRouter useTransitions={false}>
        <Routes>
          <Route path="/verify-email" element={<SameMountHarness />} />
        </Routes>
      </BrowserRouter>
    </StrictMode>,
  );
}

describe('VerifyEmailPage', () => {
  let confirmSpy: ReturnType<typeof vi.spyOn>;
  let resendSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    clearCsrf();
    confirmSpy = vi.spyOn(authOnboardingApi, 'confirmEmailVerification');
    resendSpy = vi.spyOn(authOnboardingApi, 'resendEmailVerification');
  });

  afterEach(() => {
    cleanup();
    clearCsrf();
    vi.useRealTimers();
    confirmSpy.mockRestore();
    resendSpy.mockRestore();
    vi.restoreAllMocks();
    window.history.replaceState(null, '', '/');
  });

  it('mostra missing-token senza hash', async () => {
    renderVerify('');
    expect(
      await screen.findByText('Link di verifica non disponibile.'),
    ).toBeInTheDocument();
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('under StrictMode sanitizza e conferma con hash vuoto al momento della call', async () => {
    confirmSpy.mockImplementation(async () => {
      expect(window.location.hash).toBe('');
      expect(readRouterLocation().hash).toBe('');
      return { message: 'ok' };
    });

    renderVerify('#token=token-A', { search: '?src=mail' });

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledTimes(1);
    });
    expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-A' });

    const loc = readRouterLocation();
    expect(window.location.hash).toBe('');
    expect(loc.hash).toBe('');
    expect(loc.pathname).toBe('/verify-email');
    expect(loc.search).toBe('?src=mail');
    expect(JSON.stringify(loc)).not.toContain('token-A');
    expect(document.body.textContent).not.toContain('token-A');
    expect(
      await screen.findByText('Email verificata correttamente.'),
    ).toBeInTheDocument();
  });

  it('preserva location.state utente non nullo dopo sanitizzazione', async () => {
    confirmSpy.mockResolvedValue({ message: 'ok' });
    renderVerify('#token=token-A', {
      search: '?src=mail',
      state: { from: 'invite' },
      seedState: true,
      strictMode: false,
    });
    await screen.findByText('Email verificata correttamente.');
    expect(readRouterLocation().state).toEqual({ from: 'invite' });
    expect(readRouterLocation().search).toBe('?src=mail');
  });

  it('preserva location.state null dopo sanitizzazione', async () => {
    confirmSpy.mockResolvedValue({ message: 'ok' });
    renderVerify('#token=token-A', {
      state: null,
      seedState: true,
      strictMode: false,
    });
    await screen.findByText('Email verificata correttamente.');
    expect(readRouterLocation().state).toBeNull();
  });

  it.each([
    ['#token='],
    ['#token=%20'],
    ['#token=a&token=b'],
    [`#token=${'a'.repeat(501)}`],
  ])('sanitizza fragment non valido %s senza confirm', async (hash) => {
    renderVerify(hash);
    expect(
      await screen.findByText(/Link di verifica non|non è valido/),
    ).toBeInTheDocument();
    expect(confirmSpy).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(window.location.hash).toBe('');
      expect(readRouterLocation().hash).toBe('');
    });
  });

  it('A pending → B nello stesso mount: A ignorato, B usato', async () => {
    const first = deferred<{ message: string }>();
    const second = deferred<{ message: string }>();
    confirmSpy
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    const user = userEvent.setup();
    renderSameMount('#token=token-A');

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-A' });
    });

    await user.click(screen.getByRole('button', { name: 'Go B' }));

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-B' });
    });

    first.resolve({ message: 'late-A' });
    second.resolve({ message: 'ok-B' });

    expect(
      await screen.findByText('Email verificata correttamente.'),
    ).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('token-A');
    expect(document.body.textContent).not.toContain('token-B');
  });

  it('A pending → B temporary-error: retry manuale usa solo token B', async () => {
    const pendingA = deferred<{ message: string }>();
    confirmSpy
      .mockReturnValueOnce(pendingA.promise)
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockResolvedValueOnce({ message: 'ok-B-retry' });

    const user = userEvent.setup();
    renderSameMount('#token=token-A');

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-A' });
    });

    await user.click(screen.getByRole('button', { name: 'Go B' }));

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-B' });
    });

    pendingA.resolve({ message: 'late-A' });

    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Email verificata correttamente.'),
    ).not.toBeInTheDocument();

    const callsBeforeRetry = confirmSpy.mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Riprova' }));

    await waitFor(() => {
      expect(confirmSpy.mock.calls.length).toBeGreaterThan(callsBeforeRetry);
    });

    const callsAfterRetry = confirmSpy.mock.calls as Array<
      [{ readonly token: string }]
    >;
    expect(callsAfterRetry.at(-1)?.[0]).toEqual({ token: 'token-B' });
    expect(
      callsAfterRetry.filter((call) => call[0]?.token === 'token-A'),
    ).toHaveLength(1);

    expect(
      await screen.findByText('Email verificata correttamente.'),
    ).toBeInTheDocument();
  });

  it('resend A pending non contamina flow B nello stesso mount', async () => {
    confirmSpy.mockRejectedValue(
      httpError(410, 'EMAIL_VERIFICATION_TOKEN_EXPIRED'),
    );
    const pendingResend = deferred<{ message: string }>();
    resendSpy.mockReturnValueOnce(pendingResend.promise);

    const user = userEvent.setup();
    renderSameMount('#token=token-A');

    expect(await screen.findByText(/scaduto/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email'), 'a@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(resendSpy).toHaveBeenCalledTimes(1);

    confirmSpy.mockResolvedValue({ message: 'ok' });
    await user.click(screen.getByRole('button', { name: 'Go B' }));

    await waitFor(() => {
      expect(confirmSpy).toHaveBeenCalledWith({ token: 'token-B' });
    });

    pendingResend.resolve({ message: 'late-resend-A' });

    expect(
      await screen.findByText('Email verificata correttamente.'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/Se l’indirizzo è associato/),
    ).not.toBeInTheDocument();
  });

  it('resend A pending → B: resend B parte e A non contamina finally/cooldown', async () => {
    confirmSpy.mockRejectedValue(
      httpError(410, 'EMAIL_VERIFICATION_TOKEN_EXPIRED'),
    );
    const pendingResendA = deferred<{ message: string }>();
    const pendingResendB = deferred<{ message: string }>();
    resendSpy
      .mockReturnValueOnce(pendingResendA.promise)
      .mockReturnValueOnce(pendingResendB.promise);

    const user = userEvent.setup();
    renderSameMount('#token=token-A');

    expect(await screen.findByText(/scaduto/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email'), 'a@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(resendSpy).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole('button', { name: 'Invio in corso' }),
    ).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Go B' }));

    expect(await screen.findByText(/scaduto/)).toBeInTheDocument();
    const resendButton = await screen.findByRole('button', {
      name: 'Invia di nuovo',
    });
    expect(resendButton).toBeEnabled();

    const emailField = screen.getByLabelText('Email');
    await user.clear(emailField);
    await user.type(emailField, 'b@example.com');
    await user.click(resendButton);

    await waitFor(() => {
      expect(resendSpy).toHaveBeenCalledTimes(2);
    });
    expect(resendSpy).toHaveBeenNthCalledWith(2, { email: 'b@example.com' });
    expect(
      screen.getByRole('button', { name: 'Invio in corso' }),
    ).toBeDisabled();

    pendingResendA.resolve({ message: 'late-resend-A' });

    await waitFor(() => {
      expect(
        screen.queryByText(/Se l’indirizzo è associato/),
      ).not.toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', { name: /Invia di nuovo tra/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Invio in corso' }),
    ).toBeDisabled();

    pendingResendB.resolve({ message: 'ok-resend-B' });

    expect(
      await screen.findByText(/Se l’indirizzo è associato/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Invia di nuovo tra/ }),
    ).toBeDisabled();
  });

  it('dopo unmount nuova visita senza hash è missing-token', async () => {
    confirmSpy.mockResolvedValue({ message: 'ok' });
    const first = renderVerify('#token=token-A');
    await screen.findByText('Email verificata correttamente.');
    first.unmount();

    renderVerify('');
    expect(
      await screen.findByText('Link di verifica non disponibile.'),
    ).toBeInTheDocument();
  });

  it('ignora completion dopo unmount', async () => {
    const pending = deferred<{ message: string }>();
    confirmSpy.mockReturnValue(pending.promise);
    const view = renderVerify('#token=token-A');
    await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
    view.unmount();
    pending.resolve({ message: 'late' });
    await waitFor(() => {
      expect(
        screen.queryByText('Email verificata correttamente.'),
      ).not.toBeInTheDocument();
    });
  });

  it('assorbe la prima StaleAuthOperationError', async () => {
    confirmSpy
      .mockRejectedValueOnce(new StaleAuthOperationError(1, 2))
      .mockResolvedValueOnce({ message: 'ok' });
    renderVerify('#token=token-A');
    expect(
      await screen.findByText('Email verificata correttamente.'),
    ).toBeInTheDocument();
    expect(confirmSpy).toHaveBeenCalledTimes(2);
  });

  it('seconda StaleAuthOperationError → temporary-error', async () => {
    confirmSpy
      .mockRejectedValueOnce(new StaleAuthOperationError(1, 2))
      .mockRejectedValueOnce(new StaleAuthOperationError(2, 3));
    renderVerify('#token=token-A');
    expect(
      await screen.findByText(/temporaneamente non disponibile/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeInTheDocument();
  });

  it.each([
    ['EMAIL_VERIFICATION_TOKEN_EXPIRED', 410, /scaduto/],
    ['EMAIL_VERIFICATION_TOKEN_NOT_FOUND', 404, /non è valido/],
    ['EMAIL_VERIFICATION_TOKEN_ALREADY_USED', 400, /non è più utilizzabile/],
    ['PROFESSIONAL_NOT_ACTIVE', 403, /non può essere verificato/],
    ['CLIENT_NOT_ACTIVE', 403, /non può essere verificato/],
    ['SOME_OTHER_4XX', 400, /non può essere completata/],
  ] as const)('mappa %s', async (code, status, copy) => {
    confirmSpy.mockRejectedValue(httpError(status, code));
    renderVerify('#token=token-A');
    expect(await screen.findByText(copy)).toBeInTheDocument();
  });

  it('tratta network, 5xx, UnexpectedResponse e CSRF esaurito come temporary-error', async () => {
    confirmSpy.mockRejectedValueOnce(new NetworkError(new Error('offline')));
    renderVerify('#token=token-A');
    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeInTheDocument();
    cleanup();

    confirmSpy.mockRejectedValueOnce(httpError(500, 'SERVER_ERROR'));
    renderVerify('#token=token-B');
    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeInTheDocument();
    cleanup();

    confirmSpy.mockRejectedValueOnce(
      new UnexpectedResponseError(200, new Response('nope'), new Error('bad')),
    );
    renderVerify('#token=token-C');
    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeInTheDocument();
    cleanup();

    confirmSpy.mockRejectedValueOnce(httpError(403, 'CSRF_VALIDATION_FAILED'));
    renderVerify('#token=token-D');
    expect(
      await screen.findByRole('button', { name: 'Riprova' }),
    ).toBeInTheDocument();
  });

  it('resend neutro con cooldown deadline e scadenza', async () => {
    confirmSpy.mockRejectedValue(
      httpError(410, 'EMAIL_VERIFICATION_TOKEN_EXPIRED'),
    );
    resendSpy.mockResolvedValue({ message: 'neutro' });

    const user = userEvent.setup();
    const start = Date.now();
    const dateNow = vi.spyOn(Date, 'now').mockImplementation(() => start);

    renderVerify('#token=token-A');
    expect(await screen.findByText(/scaduto/)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(
      await screen.findByText(/Se l’indirizzo è associato/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Invia di nuovo tra/ }),
    ).toBeDisabled();

    dateNow.mockImplementation(() => start + 30_000);
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /Invia di nuovo tra/ }),
      ).toBeDisabled();
    });

    dateNow.mockImplementation(() => start + 60_000);
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Invia di nuovo' }),
      ).toBeEnabled();
    });
  });

  it('non avanza epoch', async () => {
    const epochBefore = currentEpoch();
    confirmSpy.mockResolvedValue({ message: 'ok' });
    renderVerify('#token=token-A');
    await screen.findByText('Email verificata correttamente.');
    expect(currentEpoch()).toBe(epochBefore);
    advanceEpoch();
  });
});
