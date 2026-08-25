import { render, screen } from '@testing-library/react';

import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('rende title, eyebrow e description', () => {
    render(
      <PageHeader
        eyebrow="Area applicativa"
        title="Dashboard"
        description="Riepilogo delle aree disponibili."
      />,
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Dashboard' }),
    ).toBeVisible();
    expect(screen.getByText('Area applicativa')).toBeVisible();
    expect(screen.getByText('Riepilogo delle aree disponibili.')).toBeVisible();
  });

  it('non rende i campi opzionali quando assenti', () => {
    render(<PageHeader title="Profilo" />);

    const header = screen
      .getByRole('heading', { level: 1, name: 'Profilo' })
      .closest('header');

    expect(header).not.toBeNull();
    expect(header?.querySelectorAll('p')).toHaveLength(0);
  });
});
