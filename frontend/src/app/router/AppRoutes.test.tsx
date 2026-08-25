import { useState } from 'react';
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserAccessProfile } from '../config/access';
import * as authOnboardingApi from '../../api/authOnboardingApi';
import * as clientsApi from '../../api/clientsApi';
import * as invitesApi from '../../api/invitesApi';
import * as professionalsApi from '../../api/professionalsApi';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
} from '../../auth/authState';
import { renderApp } from '../../test/renderApp';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
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

function renderUnauthenticatedApp(path: string) {
  return renderWithAuthContext(
    <AppRoutes isDevelopment={false} />,
    createAuthContextValue(createUnauthenticatedAuthState()),
    { initialEntries: [path] },
  );
}

function expectPageHeading(name: string): void {
  expect(screen.getByRole('heading', { level: 1, name })).toBeVisible();
}

function expectForbidden(): void {
  expectPageHeading('Non puoi accedere a questa pagina');
}

async function openMobileNavigation(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Menu' }));

  const dialog = screen.getByRole('dialog', { name: 'Navigazione' });
  return {
    dialog,
    navigation: within(dialog).getByRole('navigation', {
      name: 'Navigazione principale',
    }),
  };
}

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.spyOn(invitesApi, 'listMyInvites').mockResolvedValue([]);
    vi.spyOn(clientsApi, 'listMyClients').mockResolvedValue([]);
    vi.spyOn(clientsApi, 'getClientById').mockResolvedValue({
      id: 7,
      firstName: 'Ada',
      lastName: 'Lovelace',
      profileImageUrl: null,
      primaryGoal: 'Migliorare la mobilità',
      operationalStatus: 'ATTIVO',
      birthDate: '1995-08-10',
      heightCm: 168.5,
      gender: 'FEMALE',
    });
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockResolvedValue([]);
    vi.spyOn(professionalsApi, 'getProfessionalById').mockResolvedValue({
      id: 11,
      firstName: 'Grace',
      lastName: 'Hopper',
      profileImageUrl: null,
      specialization: 'NUTRITIONIST',
      operationalStatus: 'DISPONIBILE',
      active: true,
      phoneNumber: null,
      bio: null,
      workplaceName: null,
      city: null,
      instagramUrl: null,
      websiteUrl: null,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each([
    ['/', 'Il tuo lavoro, più semplice da organizzare.'],
    ['/login', 'Login'],
    ['/register/professional', 'Registrazione professionista'],
    ['/invite/validate', 'Validazione invito'],
    ['/verify-email', 'Verifica dell’indirizzo email'],
  ])('mantiene pubblica %s', (path, heading) => {
    if (path === '/login' || path === '/invite/validate') {
      renderUnauthenticatedApp(path);
    } else {
      renderApp(path);
    }

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

  it('lascia ai guard il from CLIENT dopo login PROFESSIONAL', () => {
    const state = createAuthenticatedAuthState({
      role: 'PROFESSIONAL',
      specialization: 'NUTRITIONIST',
    });

    renderWithAuthContext(
      <AppRoutes isDevelopment={false} />,
      createAuthContextValue(state),
      {
        initialEntries: [
          {
            pathname: '/login',
            state: {
              from: {
                pathname: '/app/client/bookings',
                search: '?filter=pending',
                hash: '#request-11',
              },
            },
          },
        ],
      },
    );

    expectForbidden();
    expect(
      screen.queryByRole('heading', { level: 1, name: 'Prenotazioni' }),
    ).not.toBeInTheDocument();
  });

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

  it.each(['/app/professional/clients', '/app/professional/clients/7'])(
    'manda un CLIENT a forbidden sulla route professional %s',
    (path) => {
      renderAuthenticatedApp(path, {
        role: 'CLIENT',
        specialization: null,
      });

      expectForbidden();
      expect(clientsApi.listMyClients).not.toHaveBeenCalled();
      expect(clientsApi.getClientById).not.toHaveBeenCalled();
    },
  );

  describe.each(professionalProfiles)(
    'route CLIENT Professionals negate a $label',
    ({ accessProfile }) => {
      it.each(['/app/client/professionals', '/app/client/professionals/11'])(
        'nega %s senza chiamare API',
        (path) => {
          renderAuthenticatedApp(path, accessProfile);

          expectForbidden();
          expect(professionalsApi.listMyProfessionals).not.toHaveBeenCalled();
          expect(professionalsApi.getProfessionalById).not.toHaveBeenCalled();
        },
      );
    },
  );

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

  it('usa il profilo NUT runtime anche per layout, nav e dashboard', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/app/professional/dashboard', {
      role: 'PROFESSIONAL',
      specialization: 'NUTRITIONIST',
    });

    const { dialog, navigation } = await openMobileNavigation(user);
    expect(within(dialog).getByText(/Area nutrizionista$/)).toBeVisible();
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

  it('include Inviti nella primary navigation del personal trainer', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/app/professional/dashboard', {
      role: 'PROFESSIONAL',
      specialization: 'PERSONAL_TRAINER',
    });

    const { navigation } = await openMobileNavigation(user);
    expect(within(navigation).getAllByRole('link')).toHaveLength(6);
    expect(
      within(navigation).getByRole('link', { name: 'Inviti' }),
    ).toHaveAttribute('href', '/app/professional/invites');
  });

  it('non espone più Accesso secondario nella pagina Clienti', () => {
    renderAuthenticatedApp('/app/professional/clients', {
      role: 'PROFESSIONAL',
      specialization: 'PERSONAL_TRAINER',
    });

    expect(screen.queryByText('Accesso secondario')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Vai all’area inviti' }),
    ).not.toBeInTheDocument();
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

    const { dialog, navigation } = await openMobileNavigation(user);
    expect(within(dialog).getByText(/Area nutrizionista$/)).toBeVisible();
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

describe('AppRoutes CLIENT onboarding foundation gate', () => {
  const initializingState: AuthState = {
    status: 'initializing',
    operation: 'bootstrap',
    reason: null,
    account: null,
    profile: null,
    accessProfile: null,
  };

  const unavailableState: AuthState = {
    status: 'unavailable',
    operation: null,
    reason: 'bootstrap-failed',
    account: null,
    profile: null,
    accessProfile: null,
  };

  function ControllableAppAuth({
    initialState,
    path,
  }: {
    readonly initialState: AuthState;
    readonly path: string;
  }) {
    const [state, setState] = useState<AuthState>(initialState);
    const value: AuthContextValue = {
      ...createAuthContextValue(state),
      state,
    };

    return (
      <MemoryRouter initialEntries={[path]}>
        <AuthContext.Provider value={value}>
          <AppRoutes isDevelopment={false} />
          <button
            type="button"
            onClick={() => {
              setState(
                createAuthenticatedAuthState({
                  role: 'CLIENT',
                  specialization: null,
                }),
              );
            }}
          >
            become-authenticated-client
          </button>
        </AuthContext.Provider>
      </MemoryRouter>
    );
  }

  function renderAppWithAuth(path: string, state: AuthState) {
    return renderWithAuthContext(
      <AppRoutes isDevelopment={false} />,
      createAuthContextValue(state),
      { initialEntries: [path] },
    );
  }

  it('unauthenticated mostra la pagina reale ValidateInvite su /invite/validate', () => {
    renderAppWithAuth('/invite/validate', createUnauthenticatedAuthState());

    expectPageHeading('Validazione invito');
    expect(screen.getByLabelText('Codice invito')).toBeVisible();
    expect(
      screen.getByRole('textbox', { name: 'Codice invito' }),
    ).toBeVisible();
    expect(
      screen.getByText(
        'Usa il codice fornito dal professionista. Massimo 100 caratteri.',
      ),
    ).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Verifica codice' }),
    ).toBeVisible();
    expect(
      screen.queryByText(/La futura procedura verificherà il codice/),
    ).not.toBeInTheDocument();
  });

  it('direct access unauthenticated a /register/client fail-closed su validate', () => {
    renderAppWithAuth('/register/client', createUnauthenticatedAuthState());

    expectPageHeading('Validazione invito');
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
  });

  it('route production validate → register mostra il form CLIENT reale con provider condiviso', async () => {
    const user = userEvent.setup();
    vi.spyOn(authOnboardingApi, 'validateInviteCode').mockResolvedValue({
      valid: true,
      code: 'INV-ROUTER0001',
      professionalId: 7,
      expiresAt: '2026-12-31T23:59:59Z',
    });
    renderAppWithAuth('/invite/validate', createUnauthenticatedAuthState());

    await user.type(screen.getByLabelText('Codice invito'), 'INV-ROUTER0001');
    await user.click(screen.getByRole('button', { name: 'Verifica codice' }));
    await user.click(
      await screen.findByRole('button', {
        name: 'Continua con la registrazione',
      }),
    );

    expectPageHeading('Registrazione cliente');
    expect(screen.getByLabelText('Nome')).toBeVisible();
    expect(screen.getByLabelText('Data di nascita')).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Crea account cliente' }),
    ).toBeVisible();
  });

  it.each(['/invite/validate', '/register/client'] as const)(
    'initializing non mostra la pagina onboarding su %s',
    (path) => {
      const childHeading =
        path === '/invite/validate'
          ? 'Validazione invito'
          : 'Registrazione cliente';

      renderAppWithAuth(path, initializingState);

      expect(screen.getByRole('status')).toHaveTextContent(
        'Verifica della sessione in corso.',
      );
      expect(
        screen.queryByRole('heading', { level: 1, name: childHeading }),
      ).not.toBeInTheDocument();
    },
  );

  it('authenticated CLIENT redirecta da /invite/validate al dashboard cliente', () => {
    renderAppWithAuth(
      '/invite/validate',
      createAuthenticatedAuthState({
        role: 'CLIENT',
        specialization: null,
      }),
    );

    expectPageHeading('Dashboard');
    expect(
      screen.queryByRole('heading', { name: 'Validazione invito' }),
    ).not.toBeInTheDocument();
  });

  it('authenticated PROFESSIONAL redirecta da /register/client al dashboard professionista', () => {
    renderAppWithAuth(
      '/register/client',
      createAuthenticatedAuthState({
        role: 'PROFESSIONAL',
        specialization: 'NUTRITIONIST',
      }),
    );

    expectPageHeading('Dashboard');
    expect(
      screen.queryByRole('heading', { name: 'Registrazione cliente' }),
    ).not.toBeInTheDocument();
  });

  it('unavailable mostra AuthUnavailableBoundary sulle route onboarding', () => {
    renderAppWithAuth('/invite/validate', unavailableState);

    expectPageHeading('Sessione non verificabile');
    expect(
      screen.queryByRole('heading', { name: 'Validazione invito' }),
    ).not.toBeInTheDocument();
  });

  it('transizione initializing to authenticated su AppRoutes reale non mostra ValidateInvite', () => {
    render(
      <ControllableAppAuth
        initialState={initializingState}
        path="/invite/validate"
      />,
    );

    expect(screen.getByRole('status')).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Validazione invito' }),
    ).not.toBeInTheDocument();

    act(() => {
      screen
        .getByRole('button', { name: 'become-authenticated-client' })
        .click();
    });

    expectPageHeading('Dashboard');
    expect(
      screen.queryByRole('heading', { name: 'Validazione invito' }),
    ).not.toBeInTheDocument();
  });
});
