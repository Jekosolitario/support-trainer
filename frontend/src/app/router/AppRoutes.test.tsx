import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { UserAccessProfile } from '../config/access';
import { renderApp } from '../../test/renderApp';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { AppRoutes } from './AppRoutes';

interface RouteExpectation {
  readonly path: string;
  readonly heading: string;
}

const clientRoutes: RouteExpectation[] = [
  { path: '/app/client/dashboard', heading: 'Dashboard' },
  { path: '/app/client/profile', heading: 'Profilo' },
  { path: '/app/client/professionals', heading: 'Professionisti' },
  {
    path: '/app/client/professionals/7',
    heading: 'Dettaglio professionista',
  },
  {
    path: '/app/client/professionals/7/availability',
    heading: 'Disponibilità professionista',
  },
  { path: '/app/client/bookings', heading: 'Prenotazioni' },
  {
    path: '/app/client/bookings/11',
    heading: 'Dettaglio prenotazione',
  },
];

const professionalCommonRoutes: RouteExpectation[] = [
  { path: '/app/professional/dashboard', heading: 'Dashboard' },
  { path: '/app/professional/profile', heading: 'Profilo' },
  { path: '/app/professional/clients', heading: 'Clienti' },
  {
    path: '/app/professional/clients/7',
    heading: 'Dettaglio cliente',
  },
  { path: '/app/professional/invites', heading: 'Inviti' },
];

const personalTrainerRoutes: RouteExpectation[] = [
  {
    path: '/app/professional/availability',
    heading: 'Disponibilità',
  },
  { path: '/app/professional/bookings', heading: 'Prenotazioni' },
  {
    path: '/app/professional/bookings/11',
    heading: 'Dettaglio prenotazione',
  },
];

const professionalProfiles: Array<{
  readonly label: string;
  readonly accessProfile: UserAccessProfile;
}> = [
  {
    label: 'personal trainer',
    accessProfile: {
      role: 'PROFESSIONAL',
      specialization: 'PERSONAL_TRAINER',
    },
  },
  {
    label: 'nutrizionista',
    accessProfile: {
      role: 'PROFESSIONAL',
      specialization: 'NUTRITIONIST',
    },
  },
];

function renderAuthenticatedApp(
  path: string,
  accessProfile: UserAccessProfile,
) {
  return renderWithAuthContext(
    <AppRoutes isDevelopment={false} />,
    createAuthContextValue(createAuthenticatedAuthState(accessProfile)),
    { initialEntries: [path] },
  );
}

function expectPageHeading(name: string): void {
  expect(screen.getByRole('heading', { level: 1, name })).toBeVisible();
}

function expectForbidden(): void {
  expectPageHeading('Non puoi accedere a questa pagina');
}

describe('AppRoutes', () => {
  it.each([
    ['/', 'Il tuo lavoro, più semplice da organizzare.'],
    ['/login', 'Login'],
    ['/register/professional', 'Registrazione professionista'],
    ['/invite/validate', 'Validazione invito'],
    ['/register/client', 'Registrazione cliente'],
    ['/verify-email', 'Verifica dell’indirizzo email'],
  ])('mantiene pubblica %s', (path, heading) => {
    renderApp(path);

    expectPageHeading(heading);
  });

  it('rende una pagina 404 per un percorso sconosciuto', () => {
    renderApp('/percorso-inesistente');

    expectPageHeading('Pagina non trovata');
  });

  it('distingue la pagina forbidden dalla 404', () => {
    renderApp('/forbidden');

    expectForbidden();
    expect(screen.queryByText('Pagina non trovata')).not.toBeInTheDocument();
  });

  it.each(clientRoutes)(
    'consente a CLIENT la route $path',
    ({ path, heading }) => {
      renderAuthenticatedApp(path, {
        role: 'CLIENT',
        specialization: null,
      });

      expectPageHeading(heading);
    },
  );

  it.each(professionalProfiles)(
    'manda $label a forbidden sulla dashboard CLIENT',
    ({ accessProfile }) => {
      renderAuthenticatedApp('/app/client/dashboard', accessProfile);

      expectForbidden();
      expect(
        screen.queryByRole('heading', { level: 1, name: 'Dashboard' }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText('Area cliente')).not.toBeInTheDocument();
    },
  );

  describe.each(professionalProfiles)(
    'route professional comuni per $label',
    ({ accessProfile }) => {
      it.each(professionalCommonRoutes)(
        'consente $path',
        ({ path, heading }) => {
          renderAuthenticatedApp(path, accessProfile);

          expectPageHeading(heading);
        },
      );
    },
  );

  it('manda un CLIENT a forbidden su una route professional comune', () => {
    renderAuthenticatedApp('/app/professional/clients', {
      role: 'CLIENT',
      specialization: null,
    });

    expectForbidden();
  });

  it.each(personalTrainerRoutes)(
    'consente a PERSONAL_TRAINER la route PT-only $path',
    ({ path, heading }) => {
      renderAuthenticatedApp(path, {
        role: 'PROFESSIONAL',
        specialization: 'PERSONAL_TRAINER',
      });

      expectPageHeading(heading);
    },
  );

  it.each(personalTrainerRoutes)(
    'manda NUTRITIONIST a forbidden sulla route PT-only $path',
    ({ path }) => {
      renderAuthenticatedApp(path, {
        role: 'PROFESSIONAL',
        specialization: 'NUTRITIONIST',
      });

      expectForbidden();
    },
  );

  it.each(personalTrainerRoutes)(
    'manda CLIENT a forbidden sulla route PT-only $path',
    ({ path }) => {
      renderAuthenticatedApp(path, {
        role: 'CLIENT',
        specialization: null,
      });

      expectForbidden();
    },
  );

  it('usa il profilo NUT runtime anche per layout, nav e dashboard', () => {
    renderAuthenticatedApp('/app/professional/dashboard', {
      role: 'PROFESSIONAL',
      specialization: 'NUTRITIONIST',
    });

    expect(screen.getByText('Area nutrizionista')).toBeVisible();
    const navigation = screen.getByRole('navigation', {
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
  });

  it('registra la preview dei ruoli in sviluppo', () => {
    renderApp('/dev/role-preview', true);

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Anteprima tecnica dei ruoli',
      }),
    ).toBeVisible();
  });

  it('preserva il profilo simulato NUT nella preview sviluppo', async () => {
    const user = userEvent.setup();
    renderApp('/dev/role-preview', true);

    await user.click(screen.getByRole('radio', { name: 'Nutrizionista' }));

    expect(screen.getByText('Area nutrizionista')).toBeVisible();
    const navigation = screen.getByRole('navigation', {
      name: 'Navigazione principale',
    });
    expect(
      within(navigation).getByRole('link', { name: 'Inviti' }),
    ).toBeVisible();
    expect(
      within(navigation).queryByRole('link', { name: 'Disponibilità' }),
    ).not.toBeInTheDocument();
  });

  it('non registra la preview dei ruoli in produzione', () => {
    renderApp('/dev/role-preview', false);

    expectPageHeading('Pagina non trovata');
  });
});
