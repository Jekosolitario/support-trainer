import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';

import {
  CLIENT_ACCESS_PROFILE,
  NUTRITIONIST_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
  type UserAccessProfile,
} from '../../app/config/access';
import { AuthenticatedNavigation } from './AuthenticatedNavigation';

function renderNavigation(
  profile: UserAccessProfile,
  options: { readonly path?: string; readonly onNavigate?: () => void } = {},
) {
  render(
    <MemoryRouter
      initialEntries={[
        options.path ??
          (profile.role === 'CLIENT'
            ? '/app/client/dashboard'
            : '/app/professional/dashboard'),
      ]}
    >
      <AuthenticatedNavigation
        profile={profile}
        onNavigate={options.onNavigate}
      />
    </MemoryRouter>,
  );

  return screen.getByRole('navigation', { name: 'Navigazione principale' });
}

describe('AuthenticatedNavigation', () => {
  it('rende il menu CLIENT nell’ordine definitivo e marca la voce corrente', () => {
    const navigation = renderNavigation(CLIENT_ACCESS_PROFILE);

    expect(
      within(navigation)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Dashboard', 'Professionisti', 'Prenotazioni', 'Profilo']);
    expect(
      within(navigation).getByRole('link', { name: 'Dashboard' }),
    ).toHaveAttribute('aria-current', 'page');
  });

  it('rende il menu PERSONAL_TRAINER nell’ordine definitivo', () => {
    const navigation = renderNavigation(PERSONAL_TRAINER_ACCESS_PROFILE);

    expect(
      within(navigation)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual([
      'Dashboard',
      'Clienti',
      'Disponibilità',
      'Prenotazioni',
      'Inviti',
      'Profilo',
    ]);
  });

  it('rende il menu NUTRITIONIST nell’ordine sicuro senza route PT-only', () => {
    const navigation = renderNavigation(NUTRITIONIST_ACCESS_PROFILE);

    expect(
      within(navigation)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Dashboard', 'Clienti', 'Inviti', 'Profilo']);
    expect(
      within(navigation).queryByRole('link', { name: 'Disponibilità' }),
    ).not.toBeInTheDocument();
    expect(
      within(navigation).queryByRole('link', { name: 'Prenotazioni' }),
    ).not.toBeInTheDocument();
  });

  it('mantiene attiva la sezione anche su una route di dettaglio', () => {
    const navigation = renderNavigation(CLIENT_ACCESS_PROFILE, {
      path: '/app/client/professionals/7',
    });

    expect(
      within(navigation).getByRole('link', { name: 'Professionisti' }),
    ).toHaveAttribute('aria-current', 'page');
    expect(
      within(navigation).getByRole('link', { name: 'Dashboard' }),
    ).not.toHaveAttribute('aria-current');
  });

  it('notifica la selezione di una voce quando onNavigate è presente', async () => {
    const user = userEvent.setup();
    const onNavigate = vi.fn();
    const navigation = renderNavigation(CLIENT_ACCESS_PROFILE, {
      onNavigate,
    });

    await user.click(
      within(navigation).getByRole('link', { name: 'Professionisti' }),
    );

    expect(onNavigate).toHaveBeenCalledTimes(1);
  });
});
