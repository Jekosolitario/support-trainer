import { render, screen } from '@testing-library/react';

import styles from './Card.module.css';
import { Card } from './Card';

describe('Card', () => {
  it('rende i children in una superficie presentational statica', () => {
    render(<Card data-testid="card">Contenuto</Card>);

    const card = screen.getByTestId('card');
    expect(card).toHaveTextContent('Contenuto');
    expect(card).toHaveClass(styles.static);
    expect(card).not.toHaveAttribute('role');
    expect(card).not.toHaveAttribute('tabindex');
  });

  it.each([
    ['interactive', styles.interactive],
    ['highlighted', styles.highlighted],
  ] as const)('applica la variant %s', (variant, variantClass) => {
    render(
      <Card data-testid="card" variant={variant}>
        Contenuto
      </Card>,
    );

    expect(screen.getByTestId('card')).toHaveClass(variantClass);
  });
});
