import { StrictMode, useEffect, useState, type ReactNode } from 'react';
import { act, render, screen } from '@testing-library/react';
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../api/authOnboardingApi';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
} from './authState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';
import { ClientOnboardingAuthGate } from './ClientOnboardingAuthGate';
import { ClientOnboardingProvider } from './ClientOnboardingContext';
import { useClientOnboarding } from './clientOnboardingState';

const initializingState: AuthState = {
  status: 'initializing',
  operation: 'bootstrap',
  reason: null,
  account: null,
  profile: null,
  accessProfile: null,
};

const unavailableState: AuthState = {
  status: 'unavailable',
  operation: null,
  reason: 'bootstrap-failed',
  account: null,
  profile: null,
  accessProfile: null,
};

function DashboardProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <h1>Dashboard destination</h1>
      <output data-testid="path">{location.pathname}</output>
      <button type="button" onClick={() => navigate(-1)}>
        go-back
      </button>
    </>
  );
}

function ChildProbe() {
  return <h1>Onboarding child</h1>;
}

/**
 * Mounted inside the real provider but outside the Outlet, so invite state
 * remains observable after authenticated redirect unmounts onboarding children.
 */
function InviteContextProbe() {
  const { inviteCode, setValidatedInvite } = useClientOnboarding();
  return (
    <div>
      <p data-testid="context-invite">{inviteCode ?? 'null'}</p>
      <button
        type="button"
        onClick={() => {
          setValidatedInvite('INV-GATE000001');
        }}
      >
        set-context-invite
      </button>
    </div>
  );
}

function gatedTree(child: ReactNode = <ChildProbe />) {
  return (
    <ClientOnboardingProvider>
      <InviteContextProbe />
      <Routes>
        <Route path="/prior" element={<h1>Prior page</h1>} />
        <Route element={<ClientOnboardingAuthGate />}>
          <Route path="/invite/validate" element={child} />
          <Route path="/register/client" element={child} />
        </Route>
        <Route path="/app/client/dashboard" element={<DashboardProbe />} />
        <Route
          path="/app/professional/dashboard"
          element={<DashboardProbe />}
        />
      </Routes>
    </ClientOnboardingProvider>
  );
}

function ControllableAuth({
  children,
  initialState,
}: {
  readonly children: ReactNode;
  readonly initialState: AuthState;
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
          setState(createUnauthenticatedAuthState());
        }}
      >
        become-unauthenticated
      </button>
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
        become-authenticated-client
      </button>
      <button
        type="button"
        onClick={() => {
          setState(
            createAuthenticatedAuthState({
              role: 'PROFESSIONAL',
              specialization: 'PERSONAL_TRAINER',
            }),
          );
        }}
      >
        become-authenticated-professional
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

describe('ClientOnboardingAuthGate', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('in initializing non monta i child onboarding', () => {
    const childRender = vi.fn();

    function Sentinel() {
      childRender();
      return <p>child</p>;
    }

    renderWithAuthContext(
      gatedTree(<Sentinel />),
      createAuthContextValue(initializingState),
      { initialEntries: ['/invite/validate'] },
    );

    expect(screen.getByRole('status')).toHaveTextContent(
      'Verifica della sessione in corso.',
    );
    expect(screen.queryByText('child')).not.toBeInTheDocument();
    expect(childRender).not.toHaveBeenCalled();
  });

  it('in unauthenticated monta i child', () => {
    renderWithAuthContext(
      gatedTree(),
      createAuthContextValue(createUnauthenticatedAuthState()),
      { initialEntries: ['/invite/validate'] },
    );

    expect(
      screen.getByRole('heading', { name: 'Onboarding child' }),
    ).toBeVisible();
  });

  it('authenticated CLIENT redirecta al dashboard ruolo', () => {
    renderWithAuthContext(
      gatedTree(),
      createAuthContextValue(
        createAuthenticatedAuthState({
          role: 'CLIENT',
          specialization: null,
        }),
      ),
      { initialEntries: ['/invite/validate'] },
    );

    expect(
      screen.getByRole('heading', { name: 'Dashboard destination' }),
    ).toBeVisible();
    expect(screen.getByTestId('path')).toHaveTextContent(
      '/app/client/dashboard',
    );
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();
  });

  it('authenticated PROFESSIONAL redirecta al dashboard ruolo', () => {
    renderWithAuthContext(
      gatedTree(),
      createAuthContextValue(
        createAuthenticatedAuthState({
          role: 'PROFESSIONAL',
          specialization: 'PERSONAL_TRAINER',
        }),
      ),
      { initialEntries: ['/register/client'] },
    );

    expect(screen.getByTestId('path')).toHaveTextContent(
      '/app/professional/dashboard',
    );
  });

  it('unavailable usa AuthUnavailableBoundary fail-closed', () => {
    renderWithAuthContext(
      gatedTree(),
      createAuthContextValue(unavailableState),
      { initialEntries: ['/invite/validate'] },
    );

    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();
  });

  it('transizione initializing to authenticated non monta child e redirecta', () => {
    render(
      <MemoryRouter initialEntries={['/invite/validate']}>
        <ControllableAuth initialState={initializingState}>
          {gatedTree()}
        </ControllableAuth>
      </MemoryRouter>,
    );

    expect(screen.getByRole('status')).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();

    act(() => {
      screen
        .getByRole('button', { name: 'become-authenticated-client' })
        .click();
    });

    expect(
      screen.getByRole('heading', { name: 'Dashboard destination' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();
  });

  it.each([
    {
      label: 'CLIENT',
      button: 'become-authenticated-client',
      dashboard: '/app/client/dashboard',
    },
    {
      label: 'PROFESSIONAL',
      button: 'become-authenticated-professional',
      dashboard: '/app/professional/dashboard',
    },
  ] as const)(
    'unauthenticated to authenticated $label clears invite, replace-redirects, no onboarding API',
    ({ button, dashboard }) => {
      const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
      const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');

      render(
        <MemoryRouter
          initialEntries={['/prior', '/invite/validate']}
          initialIndex={1}
        >
          <ControllableAuth initialState={createUnauthenticatedAuthState()}>
            {gatedTree()}
          </ControllableAuth>
        </MemoryRouter>,
      );

      act(() => {
        screen.getByRole('button', { name: 'set-context-invite' }).click();
      });
      expect(screen.getByTestId('context-invite')).toHaveTextContent(
        'INV-GATE000001',
      );
      expect(
        screen.getByRole('heading', { name: 'Onboarding child' }),
      ).toBeVisible();

      act(() => {
        screen.getByRole('button', { name: button }).click();
      });

      expect(screen.getByTestId('context-invite')).toHaveTextContent('null');
      expect(screen.getByTestId('path')).toHaveTextContent(dashboard);
      expect(
        screen.queryByRole('heading', { name: 'Onboarding child' }),
      ).not.toBeInTheDocument();
      expect(validateSpy).not.toHaveBeenCalled();
      expect(registerSpy).not.toHaveBeenCalled();

      act(() => {
        screen.getByRole('button', { name: 'go-back' }).click();
      });
      expect(screen.getByRole('heading', { name: 'Prior page' })).toBeVisible();
      expect(
        screen.queryByRole('heading', { name: 'Onboarding child' }),
      ).not.toBeInTheDocument();
    },
  );

  it('unavailable con invite preesistente resta fail-closed e preserva invite su recovery unauthenticated', () => {
    const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
    const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');

    render(
      <MemoryRouter initialEntries={['/invite/validate']}>
        <ControllableAuth initialState={createUnauthenticatedAuthState()}>
          {gatedTree()}
        </ControllableAuth>
      </MemoryRouter>,
    );

    act(() => {
      screen.getByRole('button', { name: 'set-context-invite' }).click();
    });
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );

    act(() => {
      screen.getByRole('button', { name: 'become-unavailable' }).click();
    });

    expect(
      screen.getByRole('heading', { name: 'Sessione non verificabile' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );
    expect(validateSpy).not.toHaveBeenCalled();
    expect(registerSpy).not.toHaveBeenCalled();

    act(() => {
      screen.getByRole('button', { name: 'become-unauthenticated' }).click();
    });

    expect(
      screen.getByRole('heading', { name: 'Onboarding child' }),
    ).toBeVisible();
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );
    expect(validateSpy).not.toHaveBeenCalled();
    expect(registerSpy).not.toHaveBeenCalled();
  });

  it('unavailable con invite preesistente to authenticated clears invite e replace-redirecta', () => {
    const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
    const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');

    render(
      <MemoryRouter
        initialEntries={['/prior', '/register/client']}
        initialIndex={1}
      >
        <ControllableAuth initialState={createUnauthenticatedAuthState()}>
          {gatedTree()}
        </ControllableAuth>
      </MemoryRouter>,
    );

    act(() => {
      screen.getByRole('button', { name: 'set-context-invite' }).click();
    });
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );

    act(() => {
      screen.getByRole('button', { name: 'become-unavailable' }).click();
    });
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();

    act(() => {
      screen
        .getByRole('button', { name: 'become-authenticated-professional' })
        .click();
    });

    expect(screen.getByTestId('context-invite')).toHaveTextContent('null');
    expect(screen.getByTestId('path')).toHaveTextContent(
      '/app/professional/dashboard',
    );
    expect(
      screen.queryByRole('heading', { name: 'Onboarding child' }),
    ).not.toBeInTheDocument();
    expect(validateSpy).not.toHaveBeenCalled();
    expect(registerSpy).not.toHaveBeenCalled();

    act(() => {
      screen.getByRole('button', { name: 'go-back' }).click();
    });
    expect(screen.getByRole('heading', { name: 'Prior page' })).toBeVisible();
  });

  it('è StrictMode-safe sulla clear durante autenticazione', () => {
    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/invite/validate']}>
          <ControllableAuth initialState={createUnauthenticatedAuthState()}>
            {gatedTree()}
          </ControllableAuth>
        </MemoryRouter>
      </StrictMode>,
    );

    act(() => {
      screen.getByRole('button', { name: 'set-context-invite' }).click();
    });
    expect(screen.getByTestId('context-invite')).toHaveTextContent(
      'INV-GATE000001',
    );

    act(() => {
      screen
        .getByRole('button', { name: 'become-authenticated-client' })
        .click();
    });

    expect(screen.getByTestId('context-invite')).toHaveTextContent('null');
    expect(screen.getByTestId('path')).toHaveTextContent(
      '/app/client/dashboard',
    );
  });

  it('non invoca API onboarding mentre auth è initializing', () => {
    const validateSpy = vi.spyOn(authOnboardingApi, 'validateInviteCode');
    const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');

    function ApiCallingChild() {
      useEffect(() => {
        void authOnboardingApi.validateInviteCode({ code: 'INV-X' });
        void authOnboardingApi.registerClient({
          firstName: 'A',
          lastName: 'B',
          email: 'a@example.com',
          password: 'Password1!',
          inviteCode: 'INV-X',
          birthDate: '1996-01-01',
          heightCm: 170,
          primaryGoal: 'Goal',
          gender: 'OTHER',
        });
      }, []);
      return <h1>should-not-mount</h1>;
    }

    renderWithAuthContext(
      gatedTree(<ApiCallingChild />),
      createAuthContextValue(initializingState),
      { initialEntries: ['/invite/validate'] },
    );

    expect(validateSpy).not.toHaveBeenCalled();
    expect(registerSpy).not.toHaveBeenCalled();
    expect(
      screen.queryByRole('heading', { name: 'should-not-mount' }),
    ).not.toBeInTheDocument();
  });
});
