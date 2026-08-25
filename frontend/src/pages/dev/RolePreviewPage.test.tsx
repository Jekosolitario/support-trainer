import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { renderApp } from '../../test/renderApp';

describe('RolePreviewPage', () => {
  it('aggiorna localmente layout, menu e moduli del nutrizionista', async () => {
    const user = userEvent.setup();
    renderApp('/dev/role-preview', true);

    await user.click(screen.getByRole('radio', { name: 'Nutrizionista' }));
    await user.click(screen.getByRole('button', { name: 'Menu' }));

    const dialog = screen.getByRole('dialog', { name: 'Navigazione' });
    expect(within(dialog).getByText(/Area nutrizionista$/)).toBeVisible();
    const navigation = within(dialog).getByRole('navigation', {
      name: 'Navigazione principale',
    });
    expect(
      within(navigation).getByRole('link', { name: 'Inviti' }),
    ).toBeVisible();
    expect(
      within(navigation).queryByRole('link', { name: 'Disponibilità' }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('article', { name: 'Nutrition: In arrivo' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Esci' }),
    ).not.toBeInTheDocument();
  });
});
