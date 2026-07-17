import { render, screen, within } from '@testing-library/react';

import { FutureFeature } from './FutureFeature';

describe('FutureFeature', () => {
  it('comunica lo stato In arrivo senza elementi navigabili o azioni', () => {
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
});
