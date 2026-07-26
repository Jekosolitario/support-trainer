import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { expect, it } from 'vitest';

import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';
import type { AuthState } from './authState';
import { RequireAuth } from './RequireAuth';
import { RequireRole } from './RequireRole';
import { RequireSpecialization } from './RequireSpecialization';

function personalTrainerRoutes() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route element={<RequireRole role="PROFESSIONAL" />}>
          <Route
            element={
              <RequireSpecialization specialization="PERSONAL_TRAINER" />
            }
          >
            <Route path="/private" element={<p>Contenuto PT</p>} />
          </Route>
        </Route>
      </Route>
      <Route path="/forbidden" element={<p>Forbidden destination</p>} />
    </Routes>
  );
}

it('consente la route a un PERSONAL_TRAINER', () => {
  const state = createAuthenticatedAuthState({
    role: 'PROFESSIONAL',
    specialization: 'PERSONAL_TRAINER',
  });

  renderWithAuthContext(
    personalTrainerRoutes(),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Contenuto PT')).toBeVisible();
});

it('manda un NUTRITIONIST a forbidden', () => {
  const state = createAuthenticatedAuthState({
    role: 'PROFESSIONAL',
    specialization: 'NUTRITIONIST',
  });

  renderWithAuthContext(
    personalTrainerRoutes(),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Forbidden destination')).toBeVisible();
  expect(screen.queryByText('Contenuto PT')).not.toBeInTheDocument();
});

it('ferma un CLIENT prima della specializzazione', () => {
  const state = createAuthenticatedAuthState({
    role: 'CLIENT',
    specialization: null,
  });

  renderWithAuthContext(
    personalTrainerRoutes(),
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.getByText('Forbidden destination')).toBeVisible();
  expect(screen.queryByText('Contenuto PT')).not.toBeInTheDocument();
});

it('fallisce chiuso se usato isolatamente senza stato authenticated', () => {
  const state: AuthState = {
    status: 'unauthenticated',
    operation: null,
    reason: 'no-session',
    account: null,
    profile: null,
    accessProfile: null,
  };

  renderWithAuthContext(
    <Routes>
      <Route
        element={<RequireSpecialization specialization="PERSONAL_TRAINER" />}
      >
        <Route path="/private" element={<p>Contenuto PT</p>} />
      </Route>
      <Route path="/forbidden" element={<p>Forbidden destination</p>} />
    </Routes>,
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.queryByText('Contenuto PT')).not.toBeInTheDocument();
  expect(screen.queryByText('Forbidden destination')).not.toBeInTheDocument();
});
