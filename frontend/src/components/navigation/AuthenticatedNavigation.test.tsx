import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import {
  CLIENT_ACCESS_PROFILE,
  NUTRITIONIST_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
  type UserAccessProfile,
} from '../../app/config/access';
import { AuthenticatedNavigation } from './AuthenticatedNavigation';

function renderNavigation(profile: UserAccessProfile) {
  render(
    <MemoryRouter
      initialEntries={[
        profile.role === 'CLIENT'
          ? '/app/client/dashboard'
          : '/app/professional/dashboard',
      ]}
    >
      <AuthenticatedNavigation profile={profile} />
    </MemoryRouter>,
  );

  return screen.getByRole('navigation', { name: 'Navigazione principale' });
}

describe('AuthenticatedNavigation', () => {
  it('rende il menu cliente e marca la voce corrente', () => {
    const navigation = renderNavigation(CLIENT_ACCESS_PROFILE);

    expect(within(navigation).getAllByRole('link')).toHaveLength(4);
    expect(
      within(navigation).getByRole('link', { name: 'Professionisti' }),
    ).toBeVisible();
    expect(
      within(navigation).getByRole('link', { name: 'Dashboard' }),
    ).toHaveAttribute('aria-current', 'page');
  });

  it('rende il menu personal trainer', () => {
    const navigation = renderNavigation(PERSONAL_TRAINER_ACCESS_PROFILE);

    expect(within(navigation).getAllByRole('link')).toHaveLength(5);
    expect(
      within(navigation).getByRole('link', { name: 'Disponibilità' }),
    ).toBeVisible();
    expect(
      within(navigation).getByRole('link', { name: 'Prenotazioni' }),
    ).toBeVisible();
  });

  it('rende il menu nutrizionista senza disponibilità o prenotazioni', () => {
    const navigation = renderNavigation(NUTRITIONIST_ACCESS_PROFILE);

    expect(within(navigation).getAllByRole('link')).toHaveLength(4);
    expect(
      within(navigation).getByRole('link', { name: 'Inviti' }),
    ).toBeVisible();
    expect(
      within(navigation).queryByRole('link', { name: 'Disponibilità' }),
    ).not.toBeInTheDocument();
    expect(
      within(navigation).queryByRole('link', { name: 'Prenotazioni' }),
    ).not.toBeInTheDocument();
  });
});
