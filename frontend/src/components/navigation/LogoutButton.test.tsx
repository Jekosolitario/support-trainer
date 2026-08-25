import {
  type Dispatch,
  type ReactNode,
  type SetStateAction,
  StrictMode,
  useState,
} from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { RequireAuth } from '../../auth/RequireAuth';
import {
  AuthContext,
  type AuthContextValue,
  type AuthOperation,
  type AuthState,
} from '../../auth/authState';
import { AuthenticatedRouteLayout } from '../../layouts/authenticated/AuthenticatedRouteLayout';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { LogoutButton } from './LogoutButton';

type AuthStateSetter = Dispatch<SetStateAction<AuthState>>;
type LogoutBehavior = (setState: AuthStateSetter) => Promise<void>;
type ReconcileBehavior = (setState: AuthStateSetter) => Promise<void>;

function initializingState(operation: AuthOperation): AuthState {
  return {
    status: 'initializing',
    operation,
    reason: null,
    account: null,
    profile: null,
    accessProfile: null,
  };
}

function unavailableLogoutState(): AuthState {
  return {
    status: 'unavailable',
    operation: null,
    reason: 'logout-indeterminate',
    account: null,
    profile: null,
    accessProfile: null,
  };
}

function deferred<T = void>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}

function clientAuthenticatedState(active = true) {
  return createAuthenticatedAuthState(
    { role: 'CLIENT', specialization: null },
    { active },
  );
}

function privateRoutes(loginElement: ReactNode = <h1>Login</h1>) {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route element={<AuthenticatedRouteLayout />}>
          <Route path="/private" element={<p>Pagina privata</p>} />
        </Route>
      </Route>
      <Route path="/login" element={loginElement} />
    </Routes>
  );
}

interface StatefulHarnessProps {
  readonly initialState?: AuthState;
  readonly logoutBehavior?: LogoutBehavior;
  readonly reconcileBehavior?: ReconcileBehavior;
  readonly children?: ReactNode;
}

function StatefulHarness({
  initialState = clientAuthenticatedState(),
  logoutBehavior = async () => undefined,
  reconcileBehavior = async () => undefined,
  children,
}: StatefulHarnessProps) {
  const [state, setState] = useState<AuthState>(initialState);
  const value: AuthContextValue = {
    state,
    login: async () => undefined,
    logout: () => logoutBehavior(setState),
    reconcileSession: () => reconcileBehavior(setState),
    applyProfileSnapshot: () => undefined,
  };

  return (
    <AuthContext.Provider value={value}>
      {children ?? <LogoutButton />}
      <div data-testid="auth-state">
        {state.status}:
        {state.status === 'unauthenticated' || state.status === 'unavailable'
          ? state.reason
          : state.operation}
      </div>
    </AuthContext.Provider>
  );
}

function renderPrivateHarness(props: StatefulHarnessProps = {}) {
  return render(
    <MemoryRouter initialEntries={['/private']}>
      <StatefulHarness {...props}>{privateRoutes()}</StatefulHarness>
    </MemoryRouter>,
  );
}

function openMobileLogoutButton(): HTMLElement {
  fireEvent.click(screen.getByRole('button', { name: 'Menu' }));
  return within(screen.getByRole('dialog', { name: 'Navigazione' })).getByRole(
    'button',
    { name: 'Esci' },
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('LogoutButton', () => {
  it('mostra il button Esci in contesto authenticated', () => {
    renderWithAuthContext(
      <LogoutButton />,
      createAuthContextValue(clientAuthenticatedState()),
    );

    expect(screen.getByRole('button', { name: 'Esci' })).toBeEnabled();
  });

  it('chiama logout una sola volta al click', async () => {
    const user = userEvent.setup();
    const logout = vi.fn(async () => undefined);

    renderWithAuthContext(
      <LogoutButton />,
      createAuthContextValue(clientAuthenticatedState(), { logout }),
    );

    await user.click(screen.getByRole('button', { name: 'Esci' }));

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('disabilita il button durante il logout pending', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const logout = vi.fn(() => pending.promise);

    renderWithAuthContext(
      <LogoutButton />,
      createAuthContextValue(clientAuthenticatedState(), { logout }),
    );

    const button = screen.getByRole('button', { name: 'Esci' });
    await user.click(button);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    await act(async () => {
      pending.resolve();
    });
  });

  it('non avvia un secondo logout mentre il primo è in corso', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const logout = vi.fn(() => pending.promise);

    renderWithAuthContext(
      <LogoutButton />,
      createAuthContextValue(clientAuthenticatedState(), { logout }),
    );

    const button = screen.getByRole('button', { name: 'Esci' });
    await user.click(button);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    // Interazione utente realistica mentre la prima Promise è ancora pending.
    await user.click(button);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();

    await act(async () => {
      pending.resolve();
    });
  });

  it('consuma una rejection senza unmount e riabilita il button', async () => {
    const user = userEvent.setup();
    const logout = vi
      .fn<() => Promise<void>>()
      .mockRejectedValueOnce(new Error('CSRF_VALIDATION_FAILED MOCK'))
      .mockResolvedValueOnce(undefined);

    renderWithAuthContext(
      <LogoutButton />,
      createAuthContextValue(clientAuthenticatedState(), { logout }),
    );

    const button = screen.getByRole('button', { name: 'Esci' });
    await user.click(button);

    await waitFor(() => {
      expect(button).toBeEnabled();
    });

    expect(
      screen.queryByText(/CSRF_VALIDATION_FAILED/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/MOCK/i)).not.toBeInTheDocument();

    await user.click(button);
    expect(logout).toHaveBeenCalledTimes(2);
  });

  it('riabilita fence/pending dopo StrictMode su rejection mounted', async () => {
    const user = userEvent.setup();
    const logout = vi
      .fn<() => Promise<void>>()
      .mockRejectedValueOnce(new Error('errore tecnico nascosto'));

    render(
      <StrictMode>
        <AuthContext.Provider
          value={createAuthContextValue(clientAuthenticatedState(), {
            logout,
          })}
        >
          <LogoutButton />
        </AuthContext.Provider>
      </StrictMode>,
    );

    const button = screen.getByRole('button', { name: 'Esci' });
    await user.click(button);

    await waitFor(() => {
      expect(button).toBeEnabled();
    });

    expect(
      screen.queryByText(/errore tecnico nascosto/i),
    ).not.toBeInTheDocument();
  });

  it('non aggiorna stato locale dopo unmount durante initializing/logout', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);

    renderPrivateHarness({
      logoutBehavior: async (setState) => {
        setState(initializingState('logout'));
        await pending.promise;
      },
    });

    await user.click(openMobileLogoutButton());

    expect(screen.getByRole('status')).toHaveTextContent(
      'Disconnessione in corso.',
    );
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Pagina privata')).not.toBeInTheDocument();

    await act(async () => {
      pending.reject(new Error('fallimento dopo unmount'));
    });

    expect(
      screen.queryByText(/fallimento dopo unmount/i),
    ).not.toBeInTheDocument();
    expect(
      consoleError.mock.calls.some((call) =>
        call.some(
          (arg) =>
            typeof arg === 'string' &&
            arg.includes("Can't perform a React state update on a component"),
        ),
      ),
    ).toBe(false);
  });
});

describe('LogoutButton lifecycle con layout', () => {
  it('completa il successo verso /login tramite guard', async () => {
    const user = userEvent.setup();
    const pending = deferred();

    render(
      <MemoryRouter initialEntries={['/private']}>
        <StatefulHarness
          logoutBehavior={async (setState) => {
            setState(initializingState('logout'));
            await pending.promise;
            setState(createUnauthenticatedAuthState('logout-completed'));
          }}
        >
          {privateRoutes(
            <>
              <h1>Login</h1>
              <form aria-label="Accedi">
                <label>
                  Email
                  <input type="email" />
                </label>
              </form>
            </>,
          )}
        </StatefulHarness>
      </MemoryRouter>,
    );

    expect(screen.getByText('Pagina privata')).toBeVisible();
    await user.click(openMobileLogoutButton());

    expect(screen.getByRole('status')).toHaveTextContent(
      'Disconnessione in corso.',
    );
    expect(screen.queryByText('Pagina privata')).not.toBeInTheDocument();

    await act(async () => {
      pending.resolve();
    });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    });

    expect(screen.getByRole('form', { name: 'Accedi' })).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('auth-state')).toHaveTextContent(
      'unauthenticated:logout-completed',
    );
  });

  it('ripristina authenticated dopo CSRF restore silenzioso con nuova instance', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const logoutCalls = vi.fn();

    renderPrivateHarness({
      logoutBehavior: async (setState) => {
        logoutCalls();
        setState(initializingState('logout'));
        try {
          await pending.promise;
        } catch (error) {
          setState(clientAuthenticatedState());
          throw error;
        }
      },
    });

    await user.click(openMobileLogoutButton());
    expect(screen.getByRole('status')).toHaveTextContent(
      'Disconnessione in corso.',
    );
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();

    await act(async () => {
      pending.reject(new Error('CSRF_VALIDATION_FAILED'));
    });

    await waitFor(() => {
      expect(screen.getByText('Pagina privata')).toBeVisible();
    });

    const buttonB = openMobileLogoutButton();
    expect(buttonB).toBeEnabled();
    expect(
      screen.queryByRole('heading', { name: 'Login' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/CSRF_VALIDATION_FAILED/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/logout riuscito/i)).not.toBeInTheDocument();

    await user.click(buttonB);
    expect(logoutCalls).toHaveBeenCalledTimes(2);
  });

  it('mostra AuthUnavailableBoundary su logout ambiguo senza copy locale', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const logout = vi.fn();

    renderPrivateHarness({
      logoutBehavior: async (setState) => {
        logout();
        setState(initializingState('logout'));
        try {
          await pending.promise;
        } catch (error) {
          setState(unavailableLogoutState());
          throw error;
        }
      },
    });

    await user.click(openMobileLogoutButton());

    await act(async () => {
      pending.reject(new Error('network failure'));
    });

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'Sessione non verificabile' }),
      ).toBeVisible();
    });

    expect(screen.queryByText('Pagina privata')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Login' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/network failure/i)).not.toBeInTheDocument();
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('ripristina area e LogoutButton dopo reconciliation authenticated', async () => {
    const user = userEvent.setup();

    renderPrivateHarness({
      initialState: unavailableLogoutState(),
      reconcileBehavior: async (setState) => {
        setState(initializingState('reconciliation'));
        await Promise.resolve();
        setState(clientAuthenticatedState());
      },
    });

    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Riprova' }));

    await waitFor(() => {
      expect(screen.getByText('Pagina privata')).toBeVisible();
    });
    expect(openMobileLogoutButton()).toBeEnabled();
  });

  it('redirige a /login dopo reconciliation unauthenticated', async () => {
    const user = userEvent.setup();

    renderPrivateHarness({
      initialState: unavailableLogoutState(),
      reconcileBehavior: async (setState) => {
        setState(initializingState('reconciliation'));
        await Promise.resolve();
        setState(createUnauthenticatedAuthState('no-session'));
      },
    });

    await user.click(screen.getByRole('button', { name: 'Riprova' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    });
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();
  });
});
