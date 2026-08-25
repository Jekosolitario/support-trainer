import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { expect, it } from 'vitest';

import type { UserAccessProfile } from '../../app/config/access';
import { RequireAuth } from '../../auth/RequireAuth';
import type { AuthState } from '../../auth/authState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { AuthenticatedRouteLayout } from './AuthenticatedRouteLayout';

interface LayoutCase {
  readonly label: string;
  readonly accessProfile: UserAccessProfile;
  readonly path: string;
  readonly area: string;
  readonly visibleLink: string;
  readonly hiddenLink: string;
}

it.each([
  {
    label: 'CLIENT',
    accessProfile: { role: 'CLIENT', specialization: null },
    path: '/app/client/dashboard',
    area: 'Area cliente',
    visibleLink: 'Professionisti',
    hiddenLink: 'Clienti',
  },
  {
    label: 'PERSONAL_TRAINER',
    accessProfile: {
      role: 'PROFESSIONAL',
      specialization: 'PERSONAL_TRAINER',
    },
    path: '/app/professional/dashboard',
    area: 'Area personal trainer',
    visibleLink: 'Inviti',
    hiddenLink: 'Professionisti',
  },
  {
    label: 'NUTRITIONIST',
    accessProfile: {
      role: 'PROFESSIONAL',
      specialization: 'NUTRITIONIST',
    },
    path: '/app/professional/dashboard',
    area: 'Area nutrizionista',
    visibleLink: 'Inviti',
    hiddenLink: 'Disponibilità',
  },
] satisfies LayoutCase[])(
  'usa il profilo runtime $label dal Context per layout, navigation e Logout',
  async ({ accessProfile, path, area, visibleLink, hiddenLink }) => {
    const user = userEvent.setup();
    const state = createAuthenticatedAuthState(accessProfile);

    renderWithAuthContext(
      <Routes>
        <Route element={<RequireAuth />}>
          <Route element={<AuthenticatedRouteLayout />}>
            <Route path={path} element={<p>Pagina privata</p>} />
          </Route>
        </Route>
      </Routes>,
      createAuthContextValue(state),
      { initialEntries: [path] },
    );

    await user.click(screen.getByRole('button', { name: 'Menu' }));

    const dialog = screen.getByRole('dialog', { name: 'Navigazione' });
    expect(within(dialog).getByText(new RegExp(`${area}$`))).toBeVisible();
    expect(within(dialog).getByRole('button', { name: 'Esci' })).toBeEnabled();
    const navigation = within(dialog).getByRole('navigation', {
      name: 'Navigazione principale',
    });
    expect(
      within(navigation).getByRole('link', { name: visibleLink }),
    ).toBeVisible();
    expect(
      within(navigation).queryByRole('link', { name: hiddenLink }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Pagina privata')).toBeVisible();
  },
);

it('mostra Logout anche con profile.active=false', async () => {
  const user = userEvent.setup();
  const state = createAuthenticatedAuthState(
    { role: 'CLIENT', specialization: null },
    { active: false },
  );

  renderWithAuthContext(
    <Routes>
      <Route element={<RequireAuth />}>
        <Route element={<AuthenticatedRouteLayout />}>
          <Route path="/app/client/dashboard" element={<p>Pagina privata</p>} />
        </Route>
      </Route>
    </Routes>,
    createAuthContextValue(state),
    { initialEntries: ['/app/client/dashboard'] },
  );

  await user.click(screen.getByRole('button', { name: 'Menu' }));

  expect(
    within(screen.getByRole('dialog', { name: 'Navigazione' })).getByRole(
      'button',
      { name: 'Esci' },
    ),
  ).toBeEnabled();
  expect(screen.getByText('Pagina privata')).toBeVisible();
});

it('fallisce chiuso se montato accidentalmente fuori authenticated', () => {
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
      <Route element={<AuthenticatedRouteLayout />}>
        <Route path="/private" element={<p>Pagina privata</p>} />
      </Route>
    </Routes>,
    createAuthContextValue(state),
    { initialEntries: ['/private'] },
  );

  expect(screen.queryByText('Pagina privata')).not.toBeInTheDocument();
  expect(
    screen.queryByRole('navigation', { name: 'Navigazione principale' }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: 'Esci' }),
  ).not.toBeInTheDocument();
});
