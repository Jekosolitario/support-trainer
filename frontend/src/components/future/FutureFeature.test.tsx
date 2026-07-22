import { render, screen, within } from '@testing-library/react';

import { FutureFeature } from './FutureFeature';

describe('FutureFeature', () => {
  it('mantiene non interattiva la variante predefinita', () => {
    render(
      <FutureFeature
        title="Workout"
        description="Funzionalità prevista per uno step futuro."
      />,
    );

    const feature = screen.getByRole('article', { name: 'Workout: In arrivo' });

    expect(within(feature).getByText('In arrivo')).toBeVisible();
    expect(within(feature).queryByRole('link')).not.toBeInTheDocument();
    expect(within(feature).queryByRole('button')).not.toBeInTheDocument();
  });

  it('mantiene non interattiva la variante home', () => {
    render(
      <FutureFeature
        title="Nutrizione"
        description="Funzionalità prevista per uno step futuro."
        variant="home"
      />,
    );

    const feature = screen.getByRole('article', {
      name: 'Nutrizione: In arrivo',
    });

    expect(within(feature).getByText('In arrivo')).toBeVisible();
    expect(within(feature).queryByRole('link')).not.toBeInTheDocument();
    expect(within(feature).queryByRole('button')).not.toBeInTheDocument();
  });
});
