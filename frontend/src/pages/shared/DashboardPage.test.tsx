import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import {
  CLIENT_ACCESS_PROFILE,
  NUTRITIONIST_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
  type UserAccessProfile,
} from '../../app/config/access';
import pageTemplateStyles from '../../components/page/PageTemplate.module.css';
import { DashboardPage } from './DashboardPage';

function renderDashboard(profile: UserAccessProfile) {
  return render(
    <MemoryRouter>
      <DashboardPage profile={profile} />
    </MemoryRouter>,
  );
}

function getOperationalSection() {
  return screen.getByRole('region', { name: 'Funzioni operative previste' });
}

function getFutureSection() {
  return screen.getByRole('region', { name: 'Moduli futuri' });
}

function getFutureNames(section: HTMLElement) {
  return within(section)
    .getAllByRole('article')
    .map((article) => article.getAttribute('aria-label'));
}

describe('DashboardPage', () => {
  it('usa PageTemplate authenticated e preserva copy e heading', () => {
    renderDashboard(CLIENT_ACCESS_PROFILE);

    const heading = screen.getByRole('heading', {
      level: 1,
      name: 'Dashboard',
    });
    const page = heading.closest('article');
    const header = heading.closest('header');

    expect(page).toHaveClass(pageTemplateStyles.authenticated);
    expect(header).not.toBeNull();
    expect(
      within(header as HTMLElement).getByText('Area applicativa'),
    ).toBeVisible();
    expect(
      within(header as HTMLElement).getByText(
        'Riepilogo strutturale delle aree previste. Non sono visualizzati dati provenienti dal backend.',
      ),
    ).toBeVisible();
  });

  it('renderizza i link operativi e i moduli futuri CLIENT', () => {
    renderDashboard(CLIENT_ACCESS_PROFILE);

    const operational = getOperationalSection();
    expect(
      within(operational)
        .getAllByRole('link')
        .map((link) => [link.textContent, link.getAttribute('href')]),
    ).toEqual([
      ['Professionisti collegati', '/app/client/professionals'],
      ['Prenotazioni', '/app/client/bookings'],
      ['Profilo', '/app/client/profile'],
    ]);

    const future = getFutureSection();
    expect(getFutureNames(future)).toEqual([
      'Workout: In arrivo',
      'Nutrition: In arrivo',
      'Progressi e misurazioni: In arrivo',
    ]);
  });

  it('renderizza i link operativi e i moduli futuri PERSONAL_TRAINER', () => {
    renderDashboard(PERSONAL_TRAINER_ACCESS_PROFILE);

    const operational = getOperationalSection();
    expect(
      within(operational)
        .getAllByRole('link')
        .map((link) => [link.textContent, link.getAttribute('href')]),
    ).toEqual([
      ['Clienti collegati', '/app/professional/clients'],
      ['Disponibilità', '/app/professional/availability'],
      ['Prenotazioni', '/app/professional/bookings'],
      ['Inviti', '/app/professional/invites'],
      ['Profilo', '/app/professional/profile'],
    ]);

    expect(getFutureNames(getFutureSection())).toEqual(['Workout: In arrivo']);
  });

  it('renderizza i link NUTRITIONIST senza Availability né Booking', () => {
    renderDashboard(NUTRITIONIST_ACCESS_PROFILE);

    const operational = getOperationalSection();
    expect(
      within(operational)
        .getAllByRole('link')
        .map((link) => [link.textContent, link.getAttribute('href')]),
    ).toEqual([
      ['Clienti collegati', '/app/professional/clients'],
      ['Inviti', '/app/professional/invites'],
      ['Profilo', '/app/professional/profile'],
    ]);
    expect(
      within(operational).queryByRole('link', { name: 'Disponibilità' }),
    ).not.toBeInTheDocument();
    expect(
      within(operational).queryByRole('link', { name: 'Prenotazioni' }),
    ).not.toBeInTheDocument();

    expect(getFutureNames(getFutureSection())).toEqual([
      'Nutrition: In arrivo',
    ]);
  });

  it('mantiene l’interazione sui Link e non rende le Card cliccabili', () => {
    renderDashboard(CLIENT_ACCESS_PROFILE);

    const operational = getOperationalSection();
    const firstLink = within(operational).getByRole('link', {
      name: 'Professionisti collegati',
    });
    const firstCard = firstLink.parentElement;

    expect(firstCard).not.toBeNull();
    expect(firstCard).not.toHaveAttribute('role');
    expect(firstCard).not.toHaveAttribute('tabindex');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });
});
