import type { ReactNode } from 'react';
import { screen } from '@testing-library/react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { expect, it, vi } from 'vitest';

import type { AuthState } from './authState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';
import { RequireAuth } from './RequireAuth';

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

const unauthenticatedState: AuthState = {
  status: 'unauthenticated',
  operation: null,
  reason: 'no-session',
  account: null,
  profile: null,
  accessProfile: null,
};

function LoginDestination() {
  const location = useLocation();

  return (
    <>
      <h1>Login destination</h1>
      <output data-testid="redirect-state">
        {JSON.stringify(location.state)}
      </output>
    </>
  );
}

function guardedRoutes(privateElement: ReactNode) {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/private" element={privateElement} />
      </Route>
      <Route path="/login" element={<LoginDestination />} />
    </Routes>
  );
}

it('rende il boundary initializing senza montare l’outlet privato', () => {
  const privateRender = vi.fn();

  function PrivateSentinel() {
    privateRender();
    return <p>Contenuto privato</p>;
  }

  renderWithAuthContext(
    guardedRoutes(<PrivateSentinel />),
    createAuthContextValue(initializingState),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByRole('status')).toHaveTextContent(
    'Verifica della sessione in corso.',
  );
  expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
  expect(privateRender).not.toHaveBeenCalled();
});

it.each([
  ['bootstrap', 'Verifica della sessione in corso.'],
  ['reconciliation', 'Verifica della sessione in corso.'],
  ['login', 'Verifica della sessione in corso.'],
  ['logout', 'Disconnessione in corso.'],
] as const)(
  'usa la copy initializing corretta per operation=%s',
  (operation, copy) => {
    renderWithAuthContext(
      guardedRoutes(<p>Contenuto privato</p>),
      createAuthContextValue({
        status: 'initializing',
        operation,
        reason: null,
        account: null,
        profile: null,
        accessProfile: null,
      }),
      { initialEntries: ['/private'] },
    );

    expect(screen.getByRole('status')).toHaveTextContent(copy);
    expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
  },
);

it('rende il boundary unavailable senza outlet o redirect login', () => {
  renderWithAuthContext(
    guardedRoutes(<p>Contenuto privato</p>),
    createAuthContextValue(unavailableState),
    { initialEntries: ['/private'] },
  );

  expect(
    screen.getByRole('heading', { name: 'Sessione non verificabile' }),
  ).toBeVisible();
  expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
  expect(
    screen.queryByRole('heading', { name: 'Login destination' }),
  ).not.toBeInTheDocument();
});

it('redirige anonymous a login preservando pathname, search e hash interni', () => {
  renderWithAuthContext(
    guardedRoutes(<p>Contenuto privato</p>),
    createAuthContextValue(unauthenticatedState),
    { initialEntries: ['/private?tab=active#details'] },
  );

  expect(
    screen.getByRole('heading', { name: 'Login destination' }),
  ).toBeVisible();
  expect(screen.getByTestId('redirect-state')).toHaveTextContent(
    JSON.stringify({
      from: {
        pathname: '/private',
        search: '?tab=active',
        hash: '#details',
      },
    }),
  );
  expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
});

it('rende l’outlet per una sessione authenticated', () => {
  const state = createAuthenticatedAuthState({
    role: 'CLIENT',
    specialization: null,
  });

  renderWithAuthContext(
    guardedRoutes(<p>Contenuto privato</p>),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Contenuto privato')).toBeVisible();
});

it('non usa profile.active per bloccare l’outlet authenticated', () => {
  const state = createAuthenticatedAuthState(
    {
      role: 'CLIENT',
      specialization: null,
    },
    { active: false },
  );

  renderWithAuthContext(
    guardedRoutes(<p>Contenuto privato</p>),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Contenuto privato')).toBeVisible();
});
