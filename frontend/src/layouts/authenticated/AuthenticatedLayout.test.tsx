import { StrictMode } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';

import {
  CLIENT_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
} from '../../app/config/access';
import { AuthenticatedLayout } from './AuthenticatedLayout';
import layoutCss from './AuthenticatedLayout.module.css?raw';

describe('AuthenticatedLayout', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('data-st-authenticated');
  });

  it('preserva landmark, skip link, branding, navigation e contenuto main', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/app/client/dashboard']}>
        <AuthenticatedLayout
          profile={CLIENT_ACCESS_PROFILE}
          headerActions={<button type="button">Esci</button>}
        >
          <h1>Pagina privata</h1>
        </AuthenticatedLayout>
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('link', { name: 'Vai al contenuto' }),
    ).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Area riservata', { selector: 'aside' }),
    ).toHaveRole('complementary');
    expect(screen.getAllByLabelText('Support Trainer')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: 'Menu' }));

    const dialog = screen.getByRole('dialog', { name: 'Navigazione' });
    expect(
      within(dialog).getByRole('navigation', {
        name: 'Navigazione principale',
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
    expect(
      within(screen.getByRole('main')).getByRole('heading', {
        name: 'Pagina privata',
      }),
    ).toBeVisible();
    expect(within(dialog).getByRole('button', { name: 'Esci' })).toBeEnabled();
  });

  it('collega il drawer mobile alla stessa navigation role-aware', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/app/professional/dashboard']}>
        <AuthenticatedLayout profile={PERSONAL_TRAINER_ACCESS_PROFILE}>
          <p>Contenuto</p>
        </AuthenticatedLayout>
      </MemoryRouter>,
    );

    const desktopNavigation = within(
      screen.getByLabelText('Area riservata', { selector: 'aside' }),
    ).getByRole('navigation', {
      hidden: true,
    });
    const desktopHrefs = within(desktopNavigation)
      .getAllByRole('link', { hidden: true })
      .map((link) => link.getAttribute('href'));

    await user.click(screen.getByRole('button', { name: 'Menu' }));

    const dialog = screen.getByRole('dialog', { name: 'Navigazione' });
    const drawerNavigation = within(dialog).getByRole('navigation', {
      name: 'Navigazione principale',
    });
    expect(
      within(drawerNavigation)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(desktopHrefs);
  });

  it('mantiene il drawer nel header e sposta backdrop-filter su uno strato non ancestor', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/app/client/dashboard']}>
        <AuthenticatedLayout
          profile={CLIENT_ACCESS_PROFILE}
          headerActions={<button type="button">Esci</button>}
        >
          <p>Contenuto</p>
        </AuthenticatedLayout>
      </MemoryRouter>,
    );

    const header = screen.getByRole('banner');
    await user.click(screen.getByRole('button', { name: 'Menu' }));
    const dialog = screen.getByRole('dialog', { name: 'Navigazione' });

    expect(header.contains(dialog)).toBe(true);
    expect(dialog.parentElement).not.toBe(document.body);

    const headerBlock = layoutCss.match(/\.mobileHeader\s*\{[^}]*\}/)?.[0];
    expect(headerBlock).toBeDefined();
    expect(headerBlock).not.toMatch(/backdrop-filter/);
    expect(layoutCss).toMatch(
      /\.mobileHeader::before\s*\{[^}]*backdrop-filter:[^}]*\}/,
    );
  });

  it('applica data-st-authenticated su html e lo rimuove all’unmount', () => {
    const { unmount } = render(
      <MemoryRouter initialEntries={['/app/client/dashboard']}>
        <AuthenticatedLayout profile={CLIENT_ACCESS_PROFILE}>
          <p>Pagina privata</p>
        </AuthenticatedLayout>
      </MemoryRouter>,
    );

    expect(document.documentElement).toHaveAttribute(
      'data-st-authenticated',
      '',
    );

    unmount();

    expect(document.documentElement).not.toHaveAttribute(
      'data-st-authenticated',
    );
  });

  it('non lascia residui di data-st-authenticated con StrictMode', () => {
    const { rerender, unmount } = render(
      <StrictMode>
        <MemoryRouter initialEntries={['/app/client/dashboard']}>
          <AuthenticatedLayout profile={CLIENT_ACCESS_PROFILE}>
            <p>Pagina privata</p>
          </AuthenticatedLayout>
        </MemoryRouter>
      </StrictMode>,
    );

    expect(document.documentElement).toHaveAttribute(
      'data-st-authenticated',
      '',
    );

    rerender(
      <StrictMode>
        <MemoryRouter initialEntries={['/app/client/dashboard']}>
          <AuthenticatedLayout profile={PERSONAL_TRAINER_ACCESS_PROFILE}>
            <p>Pagina privata</p>
          </AuthenticatedLayout>
        </MemoryRouter>
      </StrictMode>,
    );

    expect(document.documentElement).toHaveAttribute(
      'data-st-authenticated',
      '',
    );

    unmount();

    expect(document.documentElement).not.toHaveAttribute(
      'data-st-authenticated',
    );
  });
});
