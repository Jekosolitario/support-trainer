import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, vi } from 'vitest';

import {
  footerAccessLinks,
  footerContent,
  footerExploreLinks,
} from '../../pages/public/homeContent';
import { HomeFooter } from './HomeFooter';

function renderFooter() {
  return render(
    <MemoryRouter>
      <HomeFooter />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe('HomeFooter', () => {
  it('rende il branding e divide le sei destinazioni tra accesso ed esplorazione', () => {
    renderFooter();

    expect(
      screen.getAllByRole('link', { name: 'Support Trainer, home' }),
    ).toHaveLength(1);

    const accessNavigation = screen.getByRole('navigation', {
      name: 'Accesso',
    });
    const homeNavigation = screen.getByRole('navigation', {
      name: 'Esplora la home',
    });
    const accessLinks = within(accessNavigation).getAllByRole('link');
    const homeLinks = within(homeNavigation).getAllByRole('link');

    expect(accessLinks).toHaveLength(3);
    expect(homeLinks).toHaveLength(3);

    footerAccessLinks.forEach((item) => {
      expect(
        within(accessNavigation).getByRole('link', { name: item.label }),
      ).toHaveAttribute('href', item.to);
    });
    footerExploreLinks.forEach((item) => {
      expect(
        within(homeNavigation).getByRole('link', { name: item.label }),
      ).toHaveAttribute('href', item.to);
    });
  });

  it('mantiene supporto futuro e informazioni legali non interattivi', () => {
    renderFooter();

    const nonInteractiveItems = [
      ...footerContent.futureSupport,
      footerContent.legal.title,
      ...footerContent.legal.items,
      'MVP disponibile',
      'Moduli in sviluppo',
      'Informazioni legali in preparazione',
    ];

    nonInteractiveItems.forEach((item) => {
      const element = screen.getByText(item);

      expect(element.closest('a, button')).toBeNull();
    });
  });

  it("calcola dinamicamente l'anno corrente", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2042-06-15T12:00:00Z'));

    renderFooter();

    expect(screen.getByText('© 2042 Support Trainer')).toBeVisible();
  });
});
