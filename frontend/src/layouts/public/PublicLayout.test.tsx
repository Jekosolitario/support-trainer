import { screen, within } from '@testing-library/react';

import { renderApp } from '../../test/renderApp';

describe('PublicLayout', () => {
  it('rende skip link, branding, main e navigazione pubblica', () => {
    renderApp('/login');

    const skipLink = screen.getByRole('link', { name: 'Vai al contenuto' });

    expect(skipLink).toHaveAttribute('href', '#main-content');
    expect(screen.getAllByRole('link')[0]).toBe(skipLink);
    expect(screen.getByLabelText('Support Trainer, home')).toBeVisible();
    expect(screen.getAllByRole('main')).toHaveLength(1);
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
    expect(
      screen.getByRole('navigation', { name: 'Navigazione pubblica' }),
    ).toBeVisible();
    expect(
      screen.getByRole('heading', { level: 1, name: 'Login' }),
    ).toBeVisible();
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });

  it('rende il footer della home dopo il main e preserva l’Outlet', () => {
    renderApp('/');

    const main = screen.getByRole('main');
    const footer = screen.getByRole('contentinfo');

    expect(screen.getAllByRole('main')).toHaveLength(1);
    expect(within(main).getByRole('heading', { level: 1 })).toBeVisible();
    expect(main).not.toContainElement(footer);
    expect(main.nextElementSibling).toBe(footer);
  });

  it('mantiene il footer quando la home contiene un hash', () => {
    renderApp('/#faq');

    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });
});
