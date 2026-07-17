import { screen } from '@testing-library/react';

import { renderApp } from '../../test/renderApp';

describe('PublicLayout', () => {
  it('rende branding, main e navigazione pubblica', () => {
    renderApp('/login');

    expect(screen.getByLabelText('Support Trainer, home')).toBeVisible();
    expect(screen.getByRole('main')).toBeVisible();
    expect(
      screen.getByRole('navigation', { name: 'Navigazione pubblica' }),
    ).toBeVisible();
  });
});
