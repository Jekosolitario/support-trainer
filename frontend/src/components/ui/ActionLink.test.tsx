import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import styles from './ActionLink.module.css';
import { ActionLink } from './ActionLink';

describe('ActionLink', () => {
  it('rende un Link nativo secondary verso la destinazione', () => {
    render(
      <MemoryRouter>
        <ActionLink to="/app/client/professionals">Professionisti</ActionLink>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: 'Professionisti' });

    expect(link).toHaveAttribute('href', '/app/client/professionals');
    expect(link).toHaveClass(styles.secondary);
  });

  it('applica la variant primary e inoltra className e attributi accessibili', () => {
    render(
      <MemoryRouter>
        <ActionLink
          aria-describedby="primary-help"
          className="extra-class"
          to="/app/client/profile"
          variant="primary"
        >
          Profilo
        </ActionLink>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: 'Profilo' });

    expect(link).toHaveClass(styles.primary);
    expect(link).toHaveClass('extra-class');
    expect(link).toHaveAttribute('href', '/app/client/profile');
    expect(link).toHaveAttribute('aria-describedby', 'primary-help');
  });
});
