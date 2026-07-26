import { type Dispatch, type SetStateAction, useState } from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { LoginRequest } from '../../api/authTypes';
import { HttpApiError, type ErrorResponse } from '../../api/types';
import type { UserAccessProfile } from '../../app/config/access';
import {
  AuthContext,
  type AuthContextValue,
  type AuthOperation,
  type AuthState,
} from '../../auth/authState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { LoginPage } from './LoginPage';

type AuthStateSetter = Dispatch<SetStateAction<AuthState>>;
type LoginBehavior = (
  credentials: LoginRequest,
  setState: AuthStateSetter,
) => Promise<void>;
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

function unavailableState(): AuthState {
  return {
    status: 'unavailable',
    operation: null,
    reason: 'login-indeterminate',
    account: null,
    profile: null,
    accessProfile: null,
  };
}

function apiError(
  status: number,
  code: string,
  fieldErrors?: ErrorResponse['fieldErrors'],
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message: 'MESSAGGIO BACKEND NON VISIBILE',
    path: '/api/v1/auth/login',
    ...(fieldErrors === undefined ? {} : { fieldErrors }),
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
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

function LocationProbe() {
  const location = useLocation();

  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
      {location.hash}
    </output>
  );
}

function loginRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/app/client/*" element={<LocationProbe />} />
      <Route path="/app/professional/*" element={<LocationProbe />} />
    </Routes>
  );
}

function renderStaticLogin(
  state: AuthState,
  overrides: Partial<Omit<AuthContextValue, 'state'>> = {},
) {
  return renderWithAuthContext(
    loginRoutes(),
    createAuthContextValue(state, overrides),
    { initialEntries: ['/login'] },
  );
}

function renderLoginWithLocationState(
  state: AuthState,
  locationState: unknown,
) {
  return render(
    <MemoryRouter
      initialEntries={[{ pathname: '/login', state: locationState }]}
    >
      <AuthContext.Provider value={createAuthContextValue(state)}>
        {loginRoutes()}
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

interface StatefulLoginHarnessProps {
  readonly initialState?: AuthState;
  readonly loginBehavior?: LoginBehavior;
  readonly reconcileBehavior?: ReconcileBehavior;
  readonly showSessionInvalidationControl?: boolean;
}

function StatefulLoginHarness({
  initialState = createUnauthenticatedAuthState(),
  loginBehavior = async () => undefined,
  reconcileBehavior = async () => undefined,
  showSessionInvalidationControl = false,
}: StatefulLoginHarnessProps) {
  const [state, setState] = useState<AuthState>(initialState);
  const value: AuthContextValue = {
    state,
    login: (credentials) => loginBehavior(credentials, setState),
    logout: async () => undefined,
    reconcileSession: () => reconcileBehavior(setState),
  };

  return (
    <AuthContext.Provider value={value}>
      {showSessionInvalidationControl ? (
        <button
          type="button"
          onClick={() =>
            setState(createUnauthenticatedAuthState('session-invalidated'))
          }
        >
          Invalida sessione test
        </button>
      ) : null}
      {loginRoutes()}
      <output data-testid="auth-state">
        {state.status}:
        {state.status === 'unauthenticated' || state.status === 'unavailable'
          ? state.reason
          : state.operation}
      </output>
    </AuthContext.Provider>
  );
}

function renderStatefulLogin(props: StatefulLoginHarnessProps = {}) {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <StatefulLoginHarness {...props} />
    </MemoryRouter>,
  );
}

async function fillValidCredentials(
  email = 'User@Example.COM',
  password = 'Password sicura',
): Promise<void> {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('LoginPage auth state', () => {
  it.each<readonly [AuthOperation, string]>([
    ['bootstrap', 'Verifica della sessione in corso'],
    ['reconciliation', 'Verifica della sessione in corso'],
    ['logout', 'Verifica della sessione in corso'],
    ['login', 'Accesso in corso'],
    ['post-login-hydration', 'Accesso in corso'],
  ])('non monta il form durante %s', (operation, copy) => {
    renderStaticLogin(initializingState(operation));

    expect(screen.getByRole('status')).toHaveTextContent(copy);
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
  });

  it('monta il form soltanto da unauthenticated', () => {
    renderStaticLogin(createUnauthenticatedAuthState());

    const email = screen.getByLabelText('Email');
    const password = screen.getByLabelText('Password');

    expect(email).toHaveAttribute('type', 'email');
    expect(email).toHaveAttribute('name', 'email');
    expect(email).toHaveAttribute('autocomplete', 'username');
    expect(email).toHaveAttribute('maxlength', '100');
    expect(email).toBeRequired();
    expect(password).toHaveAttribute('type', 'password');
    expect(password).toHaveAttribute('name', 'password');
    expect(password).toHaveAttribute('autocomplete', 'current-password');
    expect(password).toHaveAttribute('minlength', '8');
    expect(password).not.toHaveAttribute('maxlength');
    expect(password).toBeRequired();
    expect(screen.getByRole('button', { name: 'Accedi' })).toHaveAttribute(
      'type',
      'submit',
    );
  });

  it('riusa la recovery unavailable senza montare il form', () => {
    const reconcileSession = vi.fn().mockResolvedValue(undefined);

    renderStaticLogin(unavailableState(), { reconcileSession });

    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();

    const retry = screen.getByRole('button', { name: 'Riprova' });
    fireEvent.click(retry);
    fireEvent.click(retry);
    expect(reconcileSession).toHaveBeenCalledTimes(1);
  });

  it.each<readonly [string, UserAccessProfile, string]>([
    [
      'client',
      { role: 'CLIENT', specialization: null },
      '/app/client/dashboard',
    ],
    [
      'personal trainer',
      { role: 'PROFESSIONAL', specialization: 'PERSONAL_TRAINER' },
      '/app/professional/dashboard',
    ],
    [
      'nutrizionista',
      { role: 'PROFESSIONAL', specialization: 'NUTRITIONIST' },
      '/app/professional/dashboard',
    ],
  ])('redirige un %s autenticato al dashboard', (_label, profile, target) => {
    renderStaticLogin(createAuthenticatedAuthState(profile));

    expect(screen.getByTestId('location')).toHaveTextContent(target);
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
  });

  it('redirige normalmente anche con profile.active false', () => {
    renderStaticLogin(
      createAuthenticatedAuthState(
        { role: 'CLIENT', specialization: null },
        { active: false },
      ),
    );

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/client/dashboard',
    );
  });

  it('preserva pathname, search e hash di un from sicuro', () => {
    renderLoginWithLocationState(
      createAuthenticatedAuthState({
        role: 'PROFESSIONAL',
        specialization: 'PERSONAL_TRAINER',
      }),
      {
        from: {
          pathname: '/app/client/bookings',
          search: '?filter=pending',
          hash: '#request-11',
        },
      },
    );

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/client/bookings?filter=pending#request-11',
    );
  });

  it('usa il dashboard se from è invalido', () => {
    renderLoginWithLocationState(
      createAuthenticatedAuthState({
        role: 'PROFESSIONAL',
        specialization: 'NUTRITIONIST',
      }),
      {
        from: {
          pathname: 'https://evil.example',
          search: '',
          hash: '',
        },
      },
    );

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/professional/dashboard',
    );
  });
});

describe('LoginPage submit', () => {
  it('lascia alla constraint validation HTML i dati non validi', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockResolvedValue(undefined);

    renderStaticLogin(createUnauthenticatedAuthState(), { login });

    await user.type(screen.getByLabelText('Email'), 'email-non-valida');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    expect(login).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Email')).toBeInvalid();
    expect(screen.getByLabelText('Password')).toBeInvalid();
  });

  it('inoltra valori invariati, blocca submit sincroni e mostra pending', async () => {
    const gate = deferred();
    const login = vi.fn(() => gate.promise);
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    renderStaticLogin(createUnauthenticatedAuthState(), { login });
    await fillValidCredentials('User@Example.COM', '  Password sicura  ');

    const submit = screen.getByRole('button', { name: 'Accedi' });
    const form = submit.closest('form');
    if (form === null) {
      throw new Error('Form login non trovato');
    }

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(login).toHaveBeenCalledTimes(1);
    expect(login).toHaveBeenCalledWith({
      email: 'User@Example.COM',
      password: '  Password sicura  ',
    });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(form).toHaveAttribute('aria-busy', 'true');
    expect(
      screen.getByRole('button', { name: 'Accesso in corso' }),
    ).toBeDisabled();
    expect(screen.getByLabelText('Email')).toBeDisabled();
    expect(screen.getByLabelText('Password')).toBeDisabled();

    await act(async () => {
      gate.resolve();
      await gate.promise;
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Accedi' })).toBeEnabled();
    });
  });
});

describe('LoginPage errori del tentativo', () => {
  it.each([
    [
      'AUTHENTICATION_ERROR',
      apiError(401, 'AUTHENTICATION_ERROR'),
      'Email o password non corrette.',
      'Password',
    ],
    [
      'ACCOUNT_NOT_ACTIVE',
      apiError(403, 'ACCOUNT_NOT_ACTIVE'),
      'L’account non è disponibile per l’accesso.',
      'Email',
    ],
    [
      'EMAIL_NOT_VERIFIED',
      apiError(403, 'EMAIL_NOT_VERIFIED'),
      'L’indirizzo email non è ancora verificato.',
      'Password',
    ],
  ])(
    'cancella %s quando cambia una credenziale senza mutare la reason',
    async (_label, error, copy, editedField) => {
      const user = userEvent.setup();
      const loginBehavior: LoginBehavior = async (_credentials, setState) => {
        setState(initializingState('login'));
        await Promise.resolve();
        setState(createUnauthenticatedAuthState('login-rejected'));
        throw error;
      };

      renderStatefulLogin({ loginBehavior });
      await fillValidCredentials();
      await user.click(screen.getByRole('button', { name: 'Accedi' }));

      expect(await screen.findByText(copy)).toBeVisible();
      expect(screen.getByTestId('auth-state')).toHaveTextContent(
        'unauthenticated:login-rejected',
      );

      await user.type(screen.getByLabelText(editedField), 'x');

      expect(screen.queryByText(copy)).not.toBeInTheDocument();
      expect(screen.getByTestId('auth-state')).toHaveTextContent(
        'unauthenticated:login-rejected',
      );
    },
  );

  it('rimuove un field error email e non lo ricrea da login-rejected', async () => {
    const user = userEvent.setup();
    const error = apiError(400, 'VALIDATION_ERROR', [
      { field: 'email', code: 'Email', message: 'server email' },
    ]);
    const loginBehavior: LoginBehavior = async (_credentials, setState) => {
      setState(initializingState('login'));
      await Promise.resolve();
      setState(createUnauthenticatedAuthState('login-rejected'));
      throw error;
    };

    renderStatefulLogin({ loginBehavior });
    await fillValidCredentials();
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    const email = screen.getByLabelText('Email');
    expect(
      await screen.findByText('Inserisci un indirizzo email valido.'),
    ).toBeVisible();
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAttribute('aria-describedby', 'login-email-error');
    expect(email).toHaveFocus();

    await user.type(email, 'x');

    expect(
      screen.queryByText('Inserisci un indirizzo email valido.'),
    ).not.toBeInTheDocument();
    expect(email).not.toHaveAttribute('aria-invalid');
    expect(email).not.toHaveAttribute('aria-describedby');
    expect(screen.getByTestId('auth-state')).toHaveTextContent(
      'unauthenticated:login-rejected',
    );
  });
});

describe('LoginPage copy lifecycle', () => {
  it('distingue post-login-session-missing dalle credenziali errate', () => {
    renderStaticLogin(
      createUnauthenticatedAuthState('post-login-session-missing'),
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Accesso non completato. Riprova.',
    );
    expect(
      screen.queryByText('Email o password non corrette.'),
    ).not.toBeInTheDocument();
  });

  it('mostra session-invalidated come avviso globale', () => {
    renderStaticLogin(createUnauthenticatedAuthState('session-invalidated'));

    expect(screen.getByRole('alert')).toHaveTextContent(
      'La sessione è terminata. Accedi di nuovo.',
    );
  });

  it.each(['no-session', 'logout-completed'] as const)(
    'non mostra avvisi autonomi per %s',
    (reason) => {
      renderStaticLogin(createUnauthenticatedAuthState(reason));

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    },
  );
});

describe('LoginPage stale attempt', () => {
  it('ignora E1 quando E2 è corrente senza alterarne pending o copy', async () => {
    const user = userEvent.setup();
    const firstGate = deferred();
    const secondGate = deferred();
    const oldError = apiError(401, 'AUTHENTICATION_ERROR');
    const currentError = apiError(403, 'ACCOUNT_NOT_ACTIVE');
    const loginBehavior = vi
      .fn<LoginBehavior>()
      .mockImplementationOnce(async (_credentials, setState) => {
        setState(initializingState('login'));
        await firstGate.promise;
        throw oldError;
      })
      .mockImplementationOnce(async (_credentials, setState) => {
        setState(initializingState('login'));
        await secondGate.promise;
        setState(createUnauthenticatedAuthState('login-rejected'));
        throw currentError;
      });

    renderStatefulLogin({
      loginBehavior,
      showSessionInvalidationControl: true,
    });
    await fillValidCredentials();
    await user.click(screen.getByRole('button', { name: 'Accedi' }));
    await user.click(
      screen.getByRole('button', { name: 'Invalida sessione test' }),
    );
    expect(
      await screen.findByText('La sessione è terminata. Accedi di nuovo.'),
    ).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Accedi' }));
    expect(screen.getByText('Accesso in corso')).toBeVisible();

    await act(async () => {
      firstGate.reject(oldError);
      await Promise.resolve();
    });

    expect(screen.getByText('Accesso in corso')).toBeVisible();
    expect(
      screen.queryByText('Email o password non corrette.'),
    ).not.toBeInTheDocument();

    await act(async () => {
      secondGate.resolve();
      await Promise.resolve();
    });

    expect(
      await screen.findByText('L’account non è disponibile per l’accesso.'),
    ).toBeVisible();
    expect(loginBehavior).toHaveBeenCalledTimes(2);
  });
});

describe('LoginPage unavailable recovery', () => {
  it('scarta le credenziali prima di tornare a un form pulito', async () => {
    const user = userEvent.setup();
    const loginBehavior = vi.fn<LoginBehavior>(
      async (_credentials, setState) => {
        setState(initializingState('login'));
        await Promise.resolve();
        setState(unavailableState());
        throw new Error('esito indeterminato');
      },
    );
    const reconcileBehavior: ReconcileBehavior = async (setState) => {
      setState(initializingState('reconciliation'));
      await Promise.resolve();
      setState(createUnauthenticatedAuthState());
    };

    renderStatefulLogin({ loginBehavior, reconcileBehavior });
    await fillValidCredentials('user@example.com', 'Password segreta');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));
    await user.click(await screen.findByRole('button', { name: 'Riprova' }));

    expect(await screen.findByLabelText('Email')).toHaveValue('');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(loginBehavior).toHaveBeenCalledTimes(1);
  });

  it.each([
    [
      'authenticated',
      createAuthenticatedAuthState({
        role: 'CLIENT',
        specialization: null,
      }),
    ],
    ['unauthenticated', createUnauthenticatedAuthState()],
    ['unavailable', unavailableState()],
  ])(
    'riconcilia una volta verso %s senza replay login',
    async (label, outcome) => {
      const gate = deferred();
      const loginBehavior = vi.fn<LoginBehavior>();
      const reconcileBehavior: ReconcileBehavior = async (setState) => {
        setState(initializingState('reconciliation'));
        await gate.promise;
        setState(outcome);
      };

      renderStatefulLogin({
        initialState: unavailableState(),
        loginBehavior,
        reconcileBehavior,
      });

      const retry = screen.getByRole('button', { name: 'Riprova' });
      fireEvent.click(retry);
      fireEvent.click(retry);
      expect(
        screen.getByText('Verifica della sessione in corso'),
      ).toBeVisible();

      await act(async () => {
        gate.resolve();
        await gate.promise;
      });

      if (label === 'authenticated') {
        expect(await screen.findByTestId('location')).toHaveTextContent(
          '/app/client/dashboard',
        );
      } else if (label === 'unauthenticated') {
        expect(await screen.findByLabelText('Email')).toHaveValue('');
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      } else {
        expect(
          await screen.findByRole('heading', {
            name: 'Sessione non verificabile',
          }),
        ).toBeVisible();
      }

      expect(loginBehavior).not.toHaveBeenCalled();
    },
  );
});
