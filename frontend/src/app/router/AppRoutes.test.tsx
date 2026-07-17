import { screen } from '@testing-library/react';

import { renderApp } from '../../test/renderApp';

describe('AppRoutes', () => {
  it('inizializza il router e rende una rotta pubblica', () => {
    renderApp('/login');

    expect(
      screen.getByRole('heading', { level: 1, name: 'Login' }),
    ).toBeVisible();
  });

  it('rende una pagina 404 per un percorso sconosciuto', () => {
    renderApp('/percorso-inesistente');

    expect(
      screen.getByRole('heading', { level: 1, name: 'Pagina non trovata' }),
    ).toBeVisible();
  });

  it('distingue la pagina forbidden dalla 404', () => {
    renderApp('/forbidden');

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Non puoi accedere a questa pagina',
      }),
    ).toBeVisible();
    expect(screen.queryByText('Pagina non trovata')).not.toBeInTheDocument();
  });

  it('registra la preview dei ruoli in sviluppo', () => {
    renderApp('/dev/role-preview', true);

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Anteprima tecnica dei ruoli',
      }),
    ).toBeVisible();
  });

  it('non registra la preview dei ruoli in produzione', () => {
    renderApp('/dev/role-preview', false);

    expect(
      screen.getByRole('heading', { level: 1, name: 'Pagina non trovata' }),
    ).toBeVisible();
  });
});
