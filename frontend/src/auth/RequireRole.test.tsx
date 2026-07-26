import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { expect, it } from 'vitest';

import type { UserAccessProfile, UserRole } from '../app/config/access';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';
import type { AuthState } from './authState';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';

const professionalAccessProfiles = [
  {
    role: 'PROFESSIONAL',
    specialization: 'PERSONAL_TRAINER',
  },
  {
    role: 'PROFESSIONAL',
    specialization: 'NUTRITIONIST',
  },
] satisfies UserAccessProfile[];

function roleRoutes(role: UserRole) {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route element={<RequireRole role={role} />}>
          <Route path="/private" element={<p>Contenuto autorizzato</p>} />
        </Route>
      </Route>
      <Route path="/forbidden" element={<p>Forbidden destination</p>} />
      <Route path="/login" element={<p>Login destination</p>} />
    </Routes>
  );
}

it('consente una route CLIENT a un client anche con profile.active false', () => {
  const state = createAuthenticatedAuthState(
    { role: 'CLIENT', specialization: null },
    { active: false },
  );

  renderWithAuthContext(roleRoutes('CLIENT'), createAuthContextValue(state), {
    initialEntries: ['/private'],
  });

  expect(screen.getByText('Contenuto autorizzato')).toBeVisible();
});

it.each(professionalAccessProfiles)(
  'manda $specialization a forbidden su una route CLIENT',
  (accessProfile) => {
    const state = createAuthenticatedAuthState(accessProfile);

    renderWithAuthContext(roleRoutes('CLIENT'), createAuthContextValue(state), {
      initialEntries: ['/private'],
    });

    expect(screen.getByText('Forbidden destination')).toBeVisible();
    expect(screen.queryByText('Contenuto autorizzato')).not.toBeInTheDocument();
    expect(screen.queryByText('Login destination')).not.toBeInTheDocument();
  },
);

it.each(professionalAccessProfiles)(
  'consente una route PROFESSIONAL a $specialization',
  (accessProfile) => {
    const state = createAuthenticatedAuthState(accessProfile);

    renderWithAuthContext(
      roleRoutes('PROFESSIONAL'),
      createAuthContextValue(state),
      { initialEntries: ['/private'] },
    );

    expect(screen.getByText('Contenuto autorizzato')).toBeVisible();
  },
);

it('manda un client a forbidden su una route PROFESSIONAL', () => {
  const state = createAuthenticatedAuthState({
    role: 'CLIENT',
    specialization: null,
  });

  renderWithAuthContext(
    roleRoutes('PROFESSIONAL'),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Forbidden destination')).toBeVisible();
});

it('fallisce chiuso se usato accidentalmente senza stato authenticated', () => {
  const state: AuthState = {
    status: 'unavailable',
    operation: null,
    reason: 'bootstrap-failed',
    account: null,
    profile: null,
    accessProfile: null,
  };

  renderWithAuthContext(
    <Routes>
      <Route element={<RequireRole role="CLIENT" />}>
        <Route path="/private" element={<p>Contenuto autorizzato</p>} />
      </Route>
      <Route path="/forbidden" element={<p>Forbidden destination</p>} />
    </Routes>,
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.queryByText('Contenuto autorizzato')).not.toBeInTheDocument();
  expect(screen.queryByText('Forbidden destination')).not.toBeInTheDocument();
});
