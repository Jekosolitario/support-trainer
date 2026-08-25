import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import styles from './Button.module.css';
import { Button } from './Button';

describe('Button', () => {
  it('rende un button nativo primary con type button predefinito', () => {
    render(<Button>Salva</Button>);

    const button = screen.getByRole('button', { name: 'Salva' });

    expect(button).toHaveAttribute('type', 'button');
    expect(button).toHaveClass(styles.primary);
  });

  it('applica la variant e inoltra gli attributi HTML', () => {
    render(
      <Button
        aria-describedby="delete-help"
        data-testid="delete-button"
        name="delete"
        type="submit"
        variant="danger"
      >
        Elimina
      </Button>,
    );

    const button = screen.getByTestId('delete-button');

    expect(button).toHaveClass(styles.danger);
    expect(button).toHaveAttribute('type', 'submit');
    expect(button).toHaveAttribute('name', 'delete');
    expect(button).toHaveAttribute('aria-describedby', 'delete-help');
  });

  it('inoltra il click quando abilitato', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    render(<Button onClick={onClick}>Continua</Button>);

    await user.click(screen.getByRole('button', { name: 'Continua' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('preserva il comportamento nativo disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    render(
      <Button disabled onClick={onClick} variant="secondary">
        Continua
      </Button>,
    );

    const button = screen.getByRole('button', { name: 'Continua' });
    expect(button).toBeDisabled();

    await user.click(button);

    expect(onClick).not.toHaveBeenCalled();
  });
});
