import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode, useEffect, useState, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import {
  MemoryRouter,
  Outlet,
  Route,
  Router,
  Routes,
  UNSAFE_createMemoryHistory,
  useLocation,
  useNavigate,
  type To,
} from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import type { ValidateInviteCodeResponse } from '../../api/authTypes';
import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
} from '../../api/types';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
} from '../../auth/authState';
import { ClientOnboardingAuthGate } from '../../auth/ClientOnboardingAuthGate';
import { ClientOnboardingProvider } from '../../auth/ClientOnboardingContext';
import { useClientOnboarding } from '../../auth/clientOnboardingState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
} from '../../test/renderWithAuthContext';
import { VALIDATE_INVITE_TEMPORARY_ERROR } from '../../auth/validateInviteError';
import { RegisterClientPage } from './PublicPages';
import { ValidateInvitePage } from './ValidateInvitePage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function getReactOnClick(
  element: HTMLElement,
): ((event: unknown) => void) | null {
  const key = Object.keys(element).find((name) =>
    name.startsWith('__reactProps$'),
  );
  if (key === undefined) {
    return null;
  }

  const props = (
    element as unknown as Record<string, { onClick?: (event: unknown) => void }>
  )[key];
  return props.onClick ?? null;
}

function pathFromTo(to: To): string {
  if (typeof to === 'string') {
    return to.split('?')[0]?.split('#')[0] ?? to;
  }

  return to.pathname ?? '/';
}

/**
 * Router harness that can count navigate pushes without unmounting the page.
 * Used to prove same-tick CTA fence and stale-handler safety.
 */
function createNavigationCapture(options?: {
  readonly initialPath?: string;
  readonly applyNavigation?: boolean;
}) {
  const initialPath = options?.initialPath ?? '/invite/validate';
  const applyNavigation = options?.applyNavigation ?? false;
  const navigations: string[] = [];
  const history = UNSAFE_createMemoryHistory({
    initialEntries: [initialPath],
  });
  const originalPush = history.push.bind(history);
  const originalReplace = history.replace.bind(history);

  history.push = ((to: To, state?: unknown) => {
    navigations.push(pathFromTo(to));
    if (applyNavigation) {
      originalPush(to, state);
    }
  }) as typeof history.push;

  history.replace = ((to: To, state?: unknown) => {
    navigations.push(pathFromTo(to));
    if (applyNavigation) {
      originalReplace(to, state);
    }
  }) as typeof history.replace;

  function CaptureRouter({ children }: { readonly children: ReactNode }) {
    const [location, setLocation] = useState(history.location);

    useEffect(() => {
      return history.listen(({ location: nextLocation }) => {
        setLocation(nextLocation);
      });
    }, []);

    return (
      <Router
        location={location}
        navigator={history}
        navigationType={history.action}
      >
        {children}
      </Router>
    );
  }

  return { CaptureRouter, navigations };
}

function validResponse(
  code: string,
  overrides: Partial<ValidateInviteCodeResponse> = {},
): ValidateInviteCodeResponse {
  return {
    valid: true,
    code,
    professionalId: 42,
    expiresAt: '2026-12-31T23:59:59Z',
    ...overrides,
  };
}

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

function InviteProbe() {
  const { inviteCode } = useClientOnboarding();
  return <p data-testid="provider-invite">{inviteCode ?? 'null'}</p>;
}

function ClearInviteButton() {
  const { clearInvite } = useClientOnboarding();
  return (
    <button type="button" onClick={() => clearInvite()}>
      clear-invite
    </button>
  );
}

function LocationProbe() {
  const location = useLocation();
  return (
    <div>
      <p data-testid="pathname">{location.pathname}</p>
      <p data-testid="search">{location.search}</p>
      <p data-testid="hash">{location.hash}</p>
      <p data-testid="state">{JSON.stringify(location.state)}</p>
    </div>
  );
}

function HistoryBackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      history-back
    </button>
  );
}

function GoLoginButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate('/login')}>
      go-login
    </button>
  );
}

const unavailableState: AuthState = {
  status: 'unavailable',
  operation: null,
  reason: 'bootstrap-failed',
  account: null,
  profile: null,
  accessProfile: null,
};

function ControllableAuth({
  children,
  initialState = createUnauthenticatedAuthState(),
}: {
  readonly children: ReactNode;
  readonly initialState?: AuthState;
}) {
  const [state, setState] = useState<AuthState>(initialState);
  const value: AuthContextValue = {
    ...createAuthContextValue(state),
    state,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
      <button
        type="button"
        onClick={() => {
          setState(
            createAuthenticatedAuthState({
              role: 'CLIENT',
              specialization: null,
            }),
          );
        }}
      >
        become-authenticated
      </button>
      <button
        type="button"
        onClick={() => {
          setState(unavailableState);
        }}
      >
        become-unavailable
      </button>
    </AuthContext.Provider>
  );
}

function renderValidate(options?: {
  readonly initialEntries?: string[];
  readonly initialIndex?: number;
  readonly withAuthGate?: boolean;
}) {
  const initialEntries = options?.initialEntries ?? ['/invite/validate'];
  const initialIndex = options?.initialIndex ?? initialEntries.length - 1;

  const tree = (
    <>
      <LocationProbe />
      <HistoryBackButton />
      <GoLoginButton />
      <Routes>
        <Route path="/prior" element={<h1>Prior page</h1>} />
        <Route path="/login" element={<h1>Login page</h1>} />
        <Route path="/" element={<h1>Home page</h1>} />
        <Route
          element={
            <ClientOnboardingProvider>
              <InviteProbe />
              <ClearInviteButton />
              <Outlet />
            </ClientOnboardingProvider>
          }
        >
          {options?.withAuthGate ? (
            <>
              <Route element={<ClientOnboardingAuthGate />}>
                <Route
                  path="/invite/validate"
                  element={<ValidateInvitePage />}
                />
                <Route
                  path="/register/client"
                  element={<RegisterClientPage />}
                />
              </Route>
              <Route
                path="/app/client/dashboard"
                element={<h1>Client dashboard</h1>}
              />
            </>
          ) : (
            <>
              <Route path="/invite/validate" element={<ValidateInvitePage />} />
              <Route path="/register/client" element={<RegisterClientPage />} />
              <Route
                path="/app/client/dashboard"
                element={<h1>Client dashboard</h1>}
              />
            </>
          )}
        </Route>
      </Routes>
    </>
  );

  return render(
    <MemoryRouter initialEntries={initialEntries} initialIndex={initialIndex}>
      {options?.withAuthGate ? (
        <ControllableAuth>{tree}</ControllableAuth>
      ) : (
        tree
      )}
    </MemoryRouter>,
  );
}

async function submitCode(raw: string): Promise<void> {
  const user = userEvent.setup();
  const input = screen.getByLabelText('Codice invito');
  await user.clear(input);
  await user.type(input, raw);
  await user.click(screen.getByRole('button', { name: 'Verifica codice' }));
}

describe('ValidateInvitePage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('renderizza heading, label, input e submit', () => {
    renderValidate();

    expect(
      screen.getByRole('heading', { name: 'Validazione invito' }),
    ).toBeVisible();
    expect(screen.getByLabelText('Codice invito')).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Verifica codice' }),
    ).toBeVisible();
  });

  it('happy path: valida, salva solo il code canonico e naviga senza secret in URL/storage', async () => {
    const user = userEvent.setup();
    const validateSpy = vi
      .spyOn(authOnboardingApi, 'validateInviteCode')
      .mockResolvedValue(validResponse('INV-ABCDEF1234'));

    renderValidate();

    await submitCode('  inv-abcdef1234  ');

    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(validateSpy).toHaveBeenCalledTimes(1);
    expect(validateSpy).toHaveBeenCalledWith(
      { code: 'INV-ABCDEF1234' },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-ABCDEF1234',
    );
    const successPanel = screen.getByLabelText('Codice invito verificato');
    expect(
      within(successPanel).getByText(/Codice valido fino al/),
    ).toBeVisible();
    expect(successPanel.textContent ?? '').not.toContain('INV-ABCDEF1234');
    expect(successPanel.textContent ?? '').not.toContain('professionalId');
    expect(successPanel.textContent ?? '').not.toMatch(/\b42\b/);

    await user.click(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    );

    expect(
      screen.getByRole('heading', { name: 'Registrazione cliente' }),
    ).toBeVisible();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-ABCDEF1234',
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/register/client',
    );
    expect(screen.getByTestId('search')).toHaveTextContent('');
    expect(screen.getByTestId('hash')).toHaveTextContent('');
    expect(screen.getByTestId('state')).toHaveTextContent('null');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(window.location.pathname + window.location.search).not.toContain(
      'INV-ABCDEF1234',
    );
  });

  it.each([
    ['', 'Inserisci il codice invito.'],
    ['   ', 'Inserisci il codice invito.'],
  ] as const)(
    'validazione locale rifiuta codice vuoto/whitespace senza API',
    async (value, message) => {
      const user = userEvent.setup();
      const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
      renderValidate();

      if (value !== '') {
        await user.type(screen.getByLabelText('Codice invito'), value);
      }
      await user.click(screen.getByRole('button', { name: 'Verifica codice' }));

      expect(screen.getByText(message)).toBeVisible();
      expect(screen.getByLabelText('Codice invito')).toHaveFocus();
      expect(validateSpy).not.toHaveBeenCalled();
      expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    },
  );

  it('validazione locale rifiuta oltre 100 caratteri senza API', async () => {
    const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
    renderValidate();

    const input = screen.getByLabelText('Codice invito');
    fireEvent.change(input, { target: { value: 'A'.repeat(101) } });
    fireEvent.submit(input.closest('form')!);

    expect(
      screen.getByText('Il codice invito non può superare 100 caratteri.'),
    ).toBeVisible();
    expect(input).toHaveFocus();
    expect(validateSpy).not.toHaveBeenCalled();
  });

  it('nuova modifica elimina errore locale stale', async () => {
    const user = userEvent.setup();
    renderValidate();

    await user.click(screen.getByRole('button', { name: 'Verifica codice' }));
    expect(screen.getByText('Inserisci il codice invito.')).toBeVisible();

    await user.type(screen.getByLabelText('Codice invito'), 'INV-1');
    expect(
      screen.queryByText('Inserisci il codice invito.'),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['VALIDATION_ERROR', 400, 'Controlla i dati inseriti e riprova.'],
    ['INVITE_CODE_NOT_FOUND', 404, 'Codice invito non valido.'],
    ['INVITE_CODE_NOT_ACTIVE', 400, 'Questo codice invito non è disponibile.'],
    [
      'INVITE_CODE_ALREADY_USED',
      400,
      'Questo codice invito è già stato utilizzato.',
    ],
    ['INVITE_CODE_EXPIRED', 400, 'Questo codice invito è scaduto.'],
    [
      'MALFORMED_REQUEST',
      400,
      'La richiesta non è valida. Controlla i dati e riprova.',
    ],
  ] as const)(
    'error mapping %s: nessun navigate, provider vuoto, retry manuale',
    async (code, status, summary) => {
      const user = userEvent.setup();
      vi.spyOn(authOnboardingApi, 'validateInviteCode').mockRejectedValue(
        httpError(
          status,
          code,
          code === 'VALIDATION_ERROR'
            ? [{ field: 'other', code: 'X', message: 'backend-hidden' }]
            : undefined,
        ),
      );

      renderValidate();
      await submitCode('INV-FAIL000001');

      const alert = await screen.findByRole('alert');
      expect(within(alert).getByText(summary)).toBeVisible();
      expect(alert.textContent ?? '').not.toContain('backend:');
      expect(alert.textContent ?? '').not.toContain('INV-FAIL000001');
      expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        '/invite/validate',
      );
      expect(
        screen.getByRole('button', { name: 'Verifica codice' }),
      ).toBeEnabled();

      vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue(
        validResponse('INV-FAIL000001'),
      );
      await user.click(screen.getByRole('button', { name: 'Verifica codice' }));
      await screen.findByRole('heading', { name: 'Codice verificato' });
    },
  );

  it.each([
    ['network', new NetworkError(new TypeError('offline'))],
    ['5xx', httpError(503, 'SERVICE_UNAVAILABLE')],
    [
      'unexpected 2xx',
      new UnexpectedResponseError(202, new Response(), {
        kind: 'json',
        value: {},
      }),
    ],
    [
      'decoder failure',
      new UnexpectedResponseError(200, new Response(), new Error('decode')),
    ],
    [
      'read_error',
      new UnexpectedResponseError(200, new Response(), {
        kind: 'read_error',
        cause: new TypeError('stream'),
      }),
    ],
  ] as const)(
    'errore temporaneo %s: fail-closed senza navigate',
    async (_label, error) => {
      vi.spyOn(authOnboardingApi, 'validateInviteCode').mockRejectedValue(
        error,
      );
      renderValidate();
      await submitCode('INV-TEMP000001');

      const alert = await screen.findByRole('alert');
      expect(
        within(alert).getByText(VALIDATE_INVITE_TEMPORARY_ERROR),
      ).toBeVisible();
      expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        '/invite/validate',
      );
      expect(screen.getByLabelText('Codice invito')).toHaveValue(
        'INV-TEMP000001',
      );
    },
  );

  it('double submit same-tick produce una sola API call e un solo commit', async () => {
    const gate = deferred<ValidateInviteCodeResponse>();
    const validateSpy = vi
      .spyOn(authOnboardingApi, 'validateInviteCode')
      .mockReturnValue(gate.promise);

    renderValidate();
    const input = screen.getByLabelText('Codice invito');
    fireEvent.change(input, { target: { value: 'INV-DOUBLE0001' } });
    const form = input.closest('form')!;

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(validateSpy).toHaveBeenCalledTimes(1);

    await act(async () => {
      gate.resolve(validResponse('INV-DOUBLE0001'));
    });

    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-DOUBLE0001',
    );
    expect(validateSpy).toHaveBeenCalledTimes(1);
  });

  it('slow-first / fast-second: solo B viene salvato e navigato', async () => {
    const user = userEvent.setup();
    const first = deferred<ValidateInviteCodeResponse>();
    const second = deferred<ValidateInviteCodeResponse>();
    const validateSpy = vi
      .spyOn(authOnboardingApi, 'validateInviteCode')
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    renderValidate();

    await submitCode('INV-AAAA000001');
    await waitFor(() => expect(validateSpy).toHaveBeenCalledTimes(1));

    await user.clear(screen.getByLabelText('Codice invito'));
    await user.type(screen.getByLabelText('Codice invito'), 'INV-BBBB000002');
    await user.click(screen.getByRole('button', { name: 'Verifica codice' }));
    await waitFor(() => expect(validateSpy).toHaveBeenCalledTimes(2));

    await act(async () => {
      second.resolve(validResponse('INV-BBBB000002'));
    });
    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-BBBB000002',
    );

    await act(async () => {
      first.resolve(validResponse('INV-AAAA000001'));
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-BBBB000002',
    );
    await user.click(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/register/client',
    );
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-BBBB000002',
    );
  });

  it('primo successo lento non ripristina invite dopo failure del secondo', async () => {
    const user = userEvent.setup();
    const first = deferred<ValidateInviteCodeResponse>();
    const second = deferred<ValidateInviteCodeResponse>();
    vi.spyOn(authOnboardingApi, 'validateInviteCode')
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    renderValidate();
    await submitCode('INV-SLOW000001');

    await user.clear(screen.getByLabelText('Codice invito'));
    await user.type(screen.getByLabelText('Codice invito'), 'INV-FAST000002');
    await user.click(screen.getByRole('button', { name: 'Verifica codice' }));

    await act(async () => {
      second.reject(httpError(404, 'INVITE_CODE_NOT_FOUND'));
    });
    await screen.findByRole('alert');
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');

    await act(async () => {
      first.resolve(validResponse('INV-SLOW000001'));
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/invite/validate',
    );
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();
  });

  it('modifica input durante pending invalida/abortisce senza errore abort', async () => {
    const user = userEvent.setup();
    const gate = deferred<ValidateInviteCodeResponse>();
    const validateSpy = vi
      .spyOn(authOnboardingApi, 'validateInviteCode')
      .mockReturnValue(gate.promise);

    renderValidate();
    await submitCode('INV-PENDING001');
    await waitFor(() => expect(validateSpy).toHaveBeenCalledTimes(1));

    const signal = validateSpy.mock.calls[0]?.[1]?.signal;
    expect(signal).toBeInstanceOf(AbortSignal);

    await user.type(screen.getByLabelText('Codice invito'), 'X');
    expect(signal?.aborted).toBe(true);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');

    await act(async () => {
      gate.resolve(validResponse('INV-PENDING001'));
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();
  });

  it('unmount durante pending ignora response tardiva', async () => {
    const gate = deferred<ValidateInviteCodeResponse>();
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockReturnValue(
      gate.promise,
    );

    const view = renderValidate();
    await submitCode('INV-UNMOUNT001');
    view.unmount();

    await act(async () => {
      gate.resolve(validResponse('INV-UNMOUNT001'));
    });
  });

  it('auth authenticated durante pending non committa provider né naviga a register', async () => {
    const gate = deferred<ValidateInviteCodeResponse>();
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockReturnValue(
      gate.promise,
    );

    renderValidate({ withAuthGate: true });
    await submitCode('INV-AUTH000001');

    act(() => {
      screen.getByRole('button', { name: 'become-authenticated' }).click();
    });

    expect(
      screen.getByRole('heading', { name: 'Client dashboard' }),
    ).toBeVisible();

    await act(async () => {
      gate.resolve(validResponse('INV-AUTH000001'));
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/app/client/dashboard',
    );
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
  });

  it('Back da register invalida invite e consente una nuova validate diversa', async () => {
    const user = userEvent.setup();
    vi.spyOn(authOnboardingApi, 'validateInviteCode')
      .mockResolvedValueOnce(validResponse('INV-FIRST00001'))
      .mockResolvedValueOnce(validResponse('INV-SECOND0002'));

    renderValidate({
      initialEntries: ['/prior', '/invite/validate'],
      initialIndex: 1,
    });

    await submitCode('INV-FIRST00001');
    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-FIRST00001',
    );

    await user.click(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    );
    expect(
      screen.getByRole('heading', { name: 'Registrazione cliente' }),
    ).toBeVisible();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-FIRST00001',
    );

    await user.click(screen.getByRole('button', { name: 'history-back' }));
    await screen.findByRole('heading', { name: 'Validazione invito' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();

    await submitCode('INV-SECOND0002');
    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-SECOND0002',
    );
  });

  it('success feedback e aria-live non ripetono il codice secret', async () => {
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue(
      validResponse('INV-SECRET0001'),
    );
    renderValidate();
    await submitCode('INV-SECRET0001');

    const successPanel = await screen.findByLabelText(
      'Codice invito verificato',
    );
    const status = within(successPanel).getByRole('status');
    expect(status).toHaveTextContent('Puoi continuare con la registrazione.');
    expect(status.textContent ?? '').not.toContain('INV-SECRET0001');
    expect(successPanel.textContent ?? '').not.toContain('INV-SECRET0001');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clearInvite esterno dopo successo rimuove CTA/expires fail-closed senza warning React', async () => {
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue(
      validResponse('INV-CLEAR00001'),
    );

    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/invite/validate']}>
          <ClientOnboardingProvider>
            <InviteProbe />
            <ClearInviteButton />
            <LocationProbe />
            <Routes>
              <Route path="/invite/validate" element={<ValidateInvitePage />} />
              <Route path="/register/client" element={<RegisterClientPage />} />
            </Routes>
          </ClientOnboardingProvider>
        </MemoryRouter>
      </StrictMode>,
    );

    await submitCode('INV-CLEAR00001');

    await screen.findByRole('heading', { name: 'Codice verificato' });
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-CLEAR00001',
    );
    expect(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    ).toBeVisible();
    expect(screen.getByText(/Codice valido fino al/)).toBeVisible();

    act(() => {
      screen.getByRole('button', { name: 'clear-invite' }).click();
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(
      screen.queryByRole('button', { name: 'Continua con la registrazione' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Codice valido fino al/)).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Verifica codice' }),
    ).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/invite/validate',
    );
    expect(
      consoleError.mock.calls.filter((call) =>
        call.some(
          (arg) =>
            typeof arg === 'string' &&
            (arg.includes('Cannot update a component') ||
              arg.includes('during render') ||
              arg.includes('Warning:')),
        ),
      ),
    ).toHaveLength(0);
    consoleError.mockRestore();
  });

  it('handler Continua catturato non naviga dopo clear provider e non lascia lock permanente', async () => {
    const { CaptureRouter, navigations } = createNavigationCapture({
      applyNavigation: false,
    });
    vi.spyOn(authOnboardingApi, 'validateInviteCode')
      .mockResolvedValueOnce(validResponse('INV-STALE00001'))
      .mockResolvedValueOnce(validResponse('INV-STALE00002'));

    render(
      <CaptureRouter>
        <ClientOnboardingProvider>
          <InviteProbe />
          <ClearInviteButton />
          <Routes>
            <Route path="/invite/validate" element={<ValidateInvitePage />} />
            <Route path="/register/client" element={<RegisterClientPage />} />
          </Routes>
        </ClientOnboardingProvider>
      </CaptureRouter>,
    );

    await submitCode('INV-STALE00001');
    await screen.findByRole('heading', { name: 'Codice verificato' });

    const cta = screen.getByRole('button', {
      name: 'Continua con la registrazione',
    });
    const capturedHandler = getReactOnClick(cta);
    expect(capturedHandler).not.toBeNull();

    act(() => {
      flushSync(() => {
        screen.getByRole('button', { name: 'clear-invite' }).click();
      });

      expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
      expect(
        screen.queryByRole('button', { name: 'Continua con la registrazione' }),
      ).not.toBeInTheDocument();

      // The layout effect has run as part of flushSync, while passive effects
      // have not been flushed by the surrounding act yet.
      capturedHandler?.({
        type: 'click',
        preventDefault() {
          return undefined;
        },
      });
    });

    expect(navigations).toEqual([]);
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();

    await submitCode('INV-STALE00002');
    await screen.findByRole('heading', { name: 'Codice verificato' });
    fireEvent.click(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    );
    expect(navigations).toEqual(['/register/client']);
  });

  it('CTA Continua same-tick: entrambe le attivazioni raggiungono l’handler, navigate una sola volta', async () => {
    const { CaptureRouter, navigations } = createNavigationCapture({
      applyNavigation: false,
    });
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue(
      validResponse('INV-CTA0000001'),
    );

    render(
      <StrictMode>
        <CaptureRouter>
          <ClientOnboardingProvider>
            <InviteProbe />
            <Routes>
              <Route path="/invite/validate" element={<ValidateInvitePage />} />
              <Route path="/register/client" element={<RegisterClientPage />} />
            </Routes>
          </ClientOnboardingProvider>
        </CaptureRouter>
      </StrictMode>,
    );

    await submitCode('INV-CTA0000001');
    await screen.findByRole('heading', { name: 'Codice verificato' });

    const cta = screen.getByRole('button', {
      name: 'Continua con la registrazione',
    });
    expect(cta.isConnected).toBe(true);

    fireEvent.click(cta);
    // applyNavigation=false keeps success mounted so the second click is real.
    expect(cta.isConnected).toBe(true);
    expect(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    ).toBeVisible();
    fireEvent.click(cta);

    expect(navigations).toEqual(['/register/client']);
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-CTA0000001',
    );
  });

  it('CTA Continua: push singolo verso register e Back a validate', async () => {
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue(
      validResponse('INV-HIST000001'),
    );
    renderValidate({
      initialEntries: ['/prior', '/invite/validate'],
      initialIndex: 1,
    });
    await submitCode('INV-HIST000001');
    await screen.findByRole('heading', { name: 'Codice verificato' });

    fireEvent.click(
      screen.getByRole('button', { name: 'Continua con la registrazione' }),
    );

    expect(
      screen.getByRole('heading', { name: 'Registrazione cliente' }),
    ).toBeVisible();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/register/client',
    );

    fireEvent.click(screen.getByRole('button', { name: 'history-back' }));
    expect(
      screen.getByRole('heading', { name: 'Validazione invito' }),
    ).toBeVisible();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/invite/validate',
    );
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
  });

  it('auth unavailable durante pending non committa e ignora completion tardiva', async () => {
    const gate = deferred<ValidateInviteCodeResponse>();
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockReturnValue(
      gate.promise,
    );

    renderValidate({ withAuthGate: true });
    await submitCode('INV-UNAVAIL001');

    act(() => {
      screen.getByRole('button', { name: 'become-unavailable' }).click();
    });

    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Validazione invito' }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');

    await act(async () => {
      gate.resolve(validResponse('INV-UNAVAIL001'));
    });

    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();
  });

  it('navigazione fuori subtree durante pending ignora completion e distrugge provider', async () => {
    const gate = deferred<ValidateInviteCodeResponse>();
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockReturnValue(
      gate.promise,
    );

    renderValidate();
    await submitCode('INV-EXIT000001');
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');

    act(() => {
      screen.getByRole('button', { name: 'go-login' }).click();
    });

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeVisible();
    expect(screen.queryByTestId('provider-invite')).not.toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await act(async () => {
      gate.resolve(validResponse('INV-EXIT000001'));
    });

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Codice verificato' }),
    ).not.toBeInTheDocument();
    expect(document.body.textContent ?? '').not.toContain('INV-EXIT000001');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
