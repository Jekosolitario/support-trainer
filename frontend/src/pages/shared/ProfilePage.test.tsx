import { useState } from 'react';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { currentEpoch } from '../../api/authEpoch';
import type {
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
  MyProfileResponse,
} from '../../api/authTypes';
import * as meProfileApi from '../../api/meProfileApi';
import { HttpApiError, type ErrorResponse } from '../../api/types';
import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
  type AuthenticatedAuthState,
} from '../../auth/authState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import { ProfilePage } from './ProfilePage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function apiError(
  status: number,
  code: string,
  fieldErrors?: ErrorResponse['fieldErrors'],
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-26T10:00:00Z',
    status,
    code,
    message: 'MESSAGGIO BACKEND NON VISIBILE',
    path: '/api/v1/me/profile',
    ...(fieldErrors === undefined ? {} : { fieldErrors }),
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

function clientState(): AuthenticatedAuthState {
  return createAuthenticatedAuthState({
    role: 'CLIENT',
    specialization: null,
  });
}

function professionalState(): AuthenticatedAuthState {
  return createAuthenticatedAuthState({
    role: 'PROFESSIONAL',
    specialization: 'PERSONAL_TRAINER',
  });
}

function requireClientProfile(
  state: AuthenticatedAuthState,
): MyClientProfileResponse {
  if (state.profile.role !== 'CLIENT') {
    throw new Error('expected client profile');
  }

  return state.profile;
}

function requireProfessionalProfile(
  state: AuthenticatedAuthState,
): MyProfessionalProfileResponse {
  if (state.profile.role !== 'PROFESSIONAL') {
    throw new Error('expected professional profile');
  }

  return state.profile;
}

function withClientProfile(
  state: AuthenticatedAuthState,
  patch: Partial<MyClientProfileResponse>,
): AuthenticatedAuthState {
  return {
    ...state,
    profile: { ...requireClientProfile(state), ...patch },
  };
}

function withProfessionalProfile(
  state: AuthenticatedAuthState,
  patch: Partial<MyProfessionalProfileResponse>,
): AuthenticatedAuthState {
  return {
    ...state,
    profile: { ...requireProfessionalProfile(state), ...patch },
  };
}

function StatefulProfilePage({
  initialState,
  applyProfileSnapshot,
  area = 'cliente',
}: {
  readonly initialState: AuthenticatedAuthState;
  readonly applyProfileSnapshot?: AuthContextValue['applyProfileSnapshot'];
  readonly area?: 'cliente' | 'professionista';
}) {
  const [state, setState] = useState<AuthState>(initialState);
  const value = createAuthContextValue(state, {
    applyProfileSnapshot:
      applyProfileSnapshot ??
      ((profile: MyProfileResponse) => {
        setState((previous): AuthState => {
          if (previous.status !== 'authenticated') {
            return previous;
          }

          return {
            ...previous,
            profile,
            accessProfile:
              profile.role === 'CLIENT'
                ? { role: 'CLIENT', specialization: null }
                : {
                    role: 'PROFESSIONAL',
                    specialization: profile.specialization,
                  },
          };
        });
      }),
  });

  return (
    <AuthContext.Provider value={value}>
      <ProfilePage area={area} />
    </AuthContext.Provider>
  );
}

function renderProfile(
  state: AuthenticatedAuthState,
  overrides: Partial<AuthContextValue> = {},
  area: 'cliente' | 'professionista' = 'cliente',
) {
  return renderWithAuthContext(
    <ProfilePage area={area} />,
    createAuthContextValue(state, overrides),
  );
}

let updateMyProfile: ReturnType<typeof vi.spyOn>;
let updateMyOperationalStatus: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  updateMyProfile = vi.spyOn(meProfileApi, 'updateMyProfile');
  updateMyOperationalStatus = vi.spyOn(
    meProfileApi,
    'updateMyOperationalStatus',
  );
});

afterEach(() => {
  updateMyProfile.mockRestore();
  updateMyOperationalStatus.mockRestore();
  vi.restoreAllMocks();
});

describe('ProfilePage CLIENT rendering', () => {
  it('mostra dati CLIENT, account read-only e status CLIENT senza campi PRO', () => {
    const state = withClientProfile(clientState(), {
      medicalNotes: 'Asma',
      injuryNotes: 'Ginocchio',
      notes: 'Note libere',
    });

    renderProfile(state);

    expect(
      screen.getByRole('heading', { name: 'Profilo', level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Ada')).toBeInTheDocument();
    expect(screen.getByText('Lovelace')).toBeInTheDocument();
    expect(screen.getByText('1996-04-15')).toBeInTheDocument();
    expect(screen.getByText('170')).toBeInTheDocument();
    expect(screen.getByText('Benessere')).toBeInTheDocument();
    expect(screen.getByText('Femmina')).toBeInTheDocument();
    expect(screen.getByText('Asma')).toBeInTheDocument();
    expect(screen.getByText('Ginocchio')).toBeInTheDocument();
    expect(screen.getByText('Note libere')).toBeInTheDocument();

    expect(screen.queryByText('Specializzazione')).not.toBeInTheDocument();
    expect(screen.queryByText('Telefono')).not.toBeInTheDocument();
    expect(screen.queryByText('Bio')).not.toBeInTheDocument();
    expect(screen.queryByText('Instagram')).not.toBeInTheDocument();
    expect(screen.queryByText('Sito web')).not.toBeInTheDocument();

    const account = screen
      .getByRole('heading', { name: 'Account' })
      .closest('section');
    expect(account).not.toBeNull();
    expect(within(account!).getByText('user@example.com')).toBeInTheDocument();
    expect(within(account!).getByText('Cliente')).toBeInTheDocument();
    expect(within(account!).getByText('Attivo')).toBeInTheDocument();
    expect(within(account!).getByText('Verificata')).toBeInTheDocument();
    expect(within(account!).queryByRole('textbox')).not.toBeInTheDocument();

    const statusSelect = screen.getByLabelText('Nuovo stato');
    expect(statusSelect).toHaveDisplayValue('Attivo');
    expect(
      within(statusSelect).getByRole('option', { name: 'Attivo' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).getByRole('option', { name: 'Infortunato' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).getByRole('option', { name: 'In pausa' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).queryByRole('option', { name: 'Disponibile' }),
    ).not.toBeInTheDocument();
  });
});

describe('ProfilePage PROFESSIONAL rendering', () => {
  it('mostra dati PRO, specialization e status PROFESSIONAL senza campi CLIENT', () => {
    const state = withProfessionalProfile(professionalState(), {
      phoneNumber: '+39 333 111222',
      bio: 'Coach',
      workplaceName: 'Gym One',
      city: 'Milano',
      instagramUrl: 'https://instagram.com/grace',
      websiteUrl: 'https://example.com',
    });

    renderProfile(state, {}, 'professionista');

    expect(screen.getByText('Grace')).toBeInTheDocument();
    expect(screen.getByText('Hopper')).toBeInTheDocument();
    expect(screen.getByText('Personal trainer')).toBeInTheDocument();
    expect(screen.getByText('+39 333 111222')).toBeInTheDocument();
    expect(screen.getByText('Coach')).toBeInTheDocument();
    expect(screen.getByText('Gym One')).toBeInTheDocument();
    expect(screen.getByText('Milano')).toBeInTheDocument();
    expect(screen.getByText('https://instagram.com/grace')).toBeInTheDocument();
    expect(screen.getByText('https://example.com')).toBeInTheDocument();

    expect(screen.queryByText('Data di nascita')).not.toBeInTheDocument();
    expect(screen.queryByText('Altezza (cm)')).not.toBeInTheDocument();
    expect(screen.queryByText('Obiettivo principale')).not.toBeInTheDocument();
    expect(screen.queryByText('Genere')).not.toBeInTheDocument();
    expect(screen.queryByText('Note mediche')).not.toBeInTheDocument();

    const statusSelect = screen.getByLabelText('Nuovo stato');
    expect(statusSelect).toHaveDisplayValue('Disponibile');
    expect(
      within(statusSelect).getByRole('option', { name: 'Disponibile' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).getByRole('option', { name: 'Assente' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).getByRole('option', { name: 'In ferie' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).getByRole('option', { name: 'In malattia' }),
    ).toBeInTheDocument();
    expect(
      within(statusSelect).queryByRole('option', { name: 'Attivo' }),
    ).not.toBeInTheDocument();
  });
});

describe('ProfilePage editing', () => {
  it('parte in view, entra in editing con draft corretto e Annulla ripristina senza mutare AuthProvider', async () => {
    const user = userEvent.setup();
    const applyProfileSnapshot = vi.fn();
    const state = clientState();

    renderProfile(state, { applyProfileSnapshot });

    expect(
      screen.getByRole('button', { name: 'Modifica profilo' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Salva modifiche' }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));

    expect(screen.getByLabelText('Nome')).toHaveValue('Ada');
    expect(screen.getByLabelText('Cognome')).toHaveValue('Lovelace');
    expect(screen.getByLabelText('Data di nascita')).toHaveValue('1996-04-15');
    expect(screen.getByLabelText('Altezza (cm)')).toHaveValue('170');

    await user.clear(screen.getByLabelText('Nome'));
    await user.type(screen.getByLabelText('Nome'), 'Ada Updated');
    await user.click(screen.getByRole('button', { name: 'Annulla' }));

    expect(
      screen.getByRole('button', { name: 'Modifica profilo' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Ada')).toBeInTheDocument();
    expect(screen.queryByText('Ada Updated')).not.toBeInTheDocument();
    expect(applyProfileSnapshot).not.toHaveBeenCalled();
    expect(updateMyProfile).not.toHaveBeenCalled();
  });
});

describe('ProfilePage dirty e validation', () => {
  it('disabilita Salva senza modifiche e lo abilita dopo una modifica', async () => {
    const user = userEvent.setup();
    renderProfile(clientState());

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeDisabled();

    await user.type(screen.getByLabelText('Nome'), 'X');
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeEnabled();
  });

  it('CLIENT non-clearable vuoto: dirty ma validation blocca PATCH', async () => {
    const user = userEvent.setup();
    renderProfile(clientState());

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Altezza (cm)'));
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(
      await screen.findByText(
        'L’altezza deve essere maggiore di 0 e avere al massimo 3 cifre intere e 2 decimali.',
      ),
    ).toBeInTheDocument();
    expect(updateMyProfile).not.toHaveBeenCalled();
  });

  it('blocca PATCH su firstName blank, birthDate invalida e URL professional invalido', async () => {
    const user = userEvent.setup();

    renderProfile(clientState());
    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Nome'));
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));
    expect(
      await screen.findByText('Il nome non può essere vuoto.'),
    ).toBeInTheDocument();
    expect(updateMyProfile).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('Nome'), 'Ada');
    await user.clear(screen.getByLabelText('Data di nascita'));
    await user.type(screen.getByLabelText('Data di nascita'), '2999-01-01');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));
    expect(
      await screen.findByText('La data di nascita deve essere nel passato.'),
    ).toBeInTheDocument();
    expect(updateMyProfile).not.toHaveBeenCalled();
  });

  it('blocca PATCH su URL professional invalido', async () => {
    const user = userEvent.setup();
    renderProfile(professionalState(), {}, 'professionista');

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Sito web'), 'ftp://invalid');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(
      await screen.findByText(
        'L’URL del sito web deve iniziare con http:// o https://.',
      ),
    ).toBeInTheDocument();
    expect(updateMyProfile).not.toHaveBeenCalled();
  });
});

describe('ProfilePage success e errori profilo', () => {
  it('esegue epoch → PATCH differenziale → apply → successo e torna in view', async () => {
    const user = userEvent.setup();
    const applyProfileSnapshot = vi.fn();
    const state = clientState();
    const response: MyClientProfileResponse = {
      ...requireClientProfile(state),
      firstName: 'Ada Maria',
    };

    updateMyProfile.mockResolvedValue(response);

    renderProfile(state, { applyProfileSnapshot });

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Nome'));
    await user.type(screen.getByLabelText('Nome'), 'Ada Maria');
    const expectedEpoch = currentEpoch();
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    await waitFor(() => {
      expect(updateMyProfile).toHaveBeenCalledWith({ firstName: 'Ada Maria' });
      expect(applyProfileSnapshot).toHaveBeenCalledWith(
        response,
        expectedEpoch,
      );
    });

    expect(await screen.findByText('Profilo aggiornato')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Modifica profilo' }),
    ).toBeInTheDocument();
  });

  it('mostra field e global error preservando draft e restando in editing', async () => {
    const user = userEvent.setup();
    updateMyProfile.mockRejectedValue(
      apiError(400, 'VALIDATION_ERROR', [
        { field: 'firstName', code: 'Size', message: 'too long' },
      ]),
    );

    renderProfile(clientState());

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Nome'));
    await user.type(screen.getByLabelText('Nome'), 'Nome Nuovo');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(
      await screen.findByText('Il nome non può superare 100 caratteri.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Nome')).toHaveValue('Nome Nuovo');
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Profilo aggiornato')).not.toBeInTheDocument();
  });

  it('mostra errore globale profilo e resta in editing', async () => {
    const user = userEvent.setup();
    updateMyProfile.mockRejectedValue(apiError(400, 'INVALID_REQUEST'));

    renderProfile(clientState());
    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(
      await screen.findByText(
        'I dati inviati non sono validi. Controlla i campi e riprova.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Profilo aggiornato')).not.toBeInTheDocument();
  });

  it('stale soft commit: nessun successo, nessun GET/reconcile', async () => {
    const user = userEvent.setup();
    const reconcileSession = vi.fn();
    const applyProfileSnapshot = vi.fn(() => {
      throw new StaleAuthOperationError(42, 43);
    });
    const state = clientState();
    updateMyProfile.mockResolvedValue({
      ...requireClientProfile(state),
      firstName: 'Ada Maria',
    });

    renderProfile(state, { applyProfileSnapshot, reconcileSession });

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Nome'));
    await user.type(screen.getByLabelText('Nome'), 'Ada Maria');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(
      await screen.findByText('Operazione non completata. Riprova.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Profilo aggiornato')).not.toBeInTheDocument();
    expect(reconcileSession).not.toHaveBeenCalled();
    expect(
      screen.getByRole('button', { name: 'Salva modifiche' }),
    ).toBeInTheDocument();
  });
});

describe('ProfilePage operational status', () => {
  it('CLIENT: invariato disabled, update con epoch/apply e feedback', async () => {
    const user = userEvent.setup();
    const applyProfileSnapshot = vi.fn();
    const state = clientState();
    const response: MyClientProfileResponse = {
      ...requireClientProfile(state),
      operationalStatus: 'PAUSA',
    };
    updateMyOperationalStatus.mockResolvedValue(response);

    renderProfile(state, { applyProfileSnapshot });

    expect(
      screen.getByRole('button', { name: 'Aggiorna stato' }),
    ).toBeDisabled();

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    expect(
      screen.getByRole('button', { name: 'Aggiorna stato' }),
    ).toBeEnabled();

    const expectedEpoch = currentEpoch();
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    await waitFor(() => {
      expect(updateMyOperationalStatus).toHaveBeenCalledWith({
        operationalStatus: 'PAUSA',
      });
      expect(applyProfileSnapshot).toHaveBeenCalledWith(
        response,
        expectedEpoch,
      );
    });

    expect(await screen.findByText('Stato aggiornato')).toBeInTheDocument();
  });

  it('PROFESSIONAL: sole opzioni PRO e update riuscito', async () => {
    const user = userEvent.setup();
    const applyProfileSnapshot = vi.fn();
    const state = professionalState();
    const response: MyProfessionalProfileResponse = {
      ...requireProfessionalProfile(state),
      operationalStatus: 'FERIE',
    };
    updateMyOperationalStatus.mockResolvedValue(response);

    renderProfile(state, { applyProfileSnapshot }, 'professionista');

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'FERIE');
    const expectedEpoch = currentEpoch();
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    await waitFor(() => {
      expect(updateMyOperationalStatus).toHaveBeenCalledWith({
        operationalStatus: 'FERIE',
      });
      expect(applyProfileSnapshot).toHaveBeenCalledWith(
        response,
        expectedEpoch,
      );
    });

    expect(await screen.findByText('Stato aggiornato')).toBeInTheDocument();
  });

  it('errore status non contamina il form profilo', async () => {
    const user = userEvent.setup();
    updateMyOperationalStatus.mockRejectedValue(
      apiError(400, 'INVALID_OPERATIONAL_STATUS'),
    );

    renderProfile(clientState());

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    expect(
      await screen.findByText('Stato operativo non valido.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Nome')).toHaveValue('AdaX');
    expect(screen.queryByText('Profilo aggiornato')).not.toBeInTheDocument();
  });
});

describe('ProfilePage serialization', () => {
  it('durante profileSaving non parte status mutation e doppio submit non duplica', async () => {
    const user = userEvent.setup();
    const pending = deferred<MyClientProfileResponse>();
    updateMyProfile.mockReturnValue(pending.promise);

    const state = clientState();
    renderProfile(state);

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    await waitFor(() => {
      expect(updateMyProfile).toHaveBeenCalledTimes(1);
      expect(
        screen.getByRole('button', { name: 'Salvataggio…' }),
      ).toBeDisabled();
      expect(
        screen.getByRole('button', { name: 'Aggiorna stato' }),
      ).toBeDisabled();
    });

    const form = screen
      .getByRole('button', { name: 'Salvataggio…' })
      .closest('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    expect(updateMyProfile).toHaveBeenCalledTimes(1);
    expect(updateMyOperationalStatus).not.toHaveBeenCalled();

    pending.resolve({ ...requireClientProfile(state), firstName: 'AdaX' });
    await screen.findByText('Profilo aggiornato');
  });

  it('durante statusSaving non parte profile save', async () => {
    const user = userEvent.setup();
    const pending = deferred<MyClientProfileResponse>();
    updateMyOperationalStatus.mockReturnValue(pending.promise);

    const state = clientState();
    renderProfile(state);

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    await waitFor(() => {
      expect(updateMyOperationalStatus).toHaveBeenCalledTimes(1);
      expect(
        screen.getByRole('button', { name: 'Salva modifiche' }),
      ).toBeDisabled();
    });

    const form = screen
      .getByRole('button', { name: 'Salva modifiche' })
      .closest('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    expect(updateMyProfile).not.toHaveBeenCalled();

    pending.resolve({
      ...requireClientProfile(state),
      operationalStatus: 'PAUSA',
    });
    await screen.findByText('Stato aggiornato');
  });
});

describe('ProfilePage stateful soft commit', () => {
  it('dopo apply riuscito la view riflette la response senza GET', async () => {
    const user = userEvent.setup();
    const state = clientState();
    updateMyProfile.mockResolvedValue({
      ...requireClientProfile(state),
      firstName: 'Ada Maria',
    });

    render(<StatefulProfilePage initialState={state} />);

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.clear(screen.getByLabelText('Nome'));
    await user.type(screen.getByLabelText('Nome'), 'Ada Maria');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(await screen.findByText('Profilo aggiornato')).toBeInTheDocument();
    expect(screen.getByText('Ada Maria')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Modifica profilo' }),
    ).toBeInTheDocument();
  });
});

describe('ProfilePage Lotto 4 — M1/M2/T2', () => {
  it('M1: successo profilo rimuove errore status stale', async () => {
    const user = userEvent.setup();
    updateMyOperationalStatus.mockRejectedValue(
      apiError(400, 'INVALID_OPERATIONAL_STATUS'),
    );
    updateMyProfile.mockResolvedValue({
      ...requireClientProfile(clientState()),
      firstName: 'AdaX',
    });

    renderProfile(clientState());

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));
    expect(
      await screen.findByText('Stato operativo non valido.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));

    expect(await screen.findByText('Profilo aggiornato')).toBeInTheDocument();
    expect(
      screen.queryByText('Stato operativo non valido.'),
    ).not.toBeInTheDocument();
  });

  it('M1: successo status rimuove errore globale profilo stale', async () => {
    const user = userEvent.setup();
    updateMyProfile.mockRejectedValue(apiError(400, 'INVALID_REQUEST'));
    updateMyOperationalStatus.mockResolvedValue({
      ...requireClientProfile(clientState()),
      operationalStatus: 'PAUSA',
    });

    renderProfile(clientState());

    await user.click(screen.getByRole('button', { name: 'Modifica profilo' }));
    await user.type(screen.getByLabelText('Nome'), 'X');
    await user.click(screen.getByRole('button', { name: 'Salva modifiche' }));
    expect(
      await screen.findByText(
        'I dati inviati non sono validi. Controlla i campi e riprova.',
      ),
    ).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    expect(await screen.findByText('Stato aggiornato')).toBeInTheDocument();
    expect(
      screen.queryByText(
        'I dati inviati non sono validi. Controlla i campi e riprova.',
      ),
    ).not.toBeInTheDocument();
  });

  it('M2: fallback avatar accessibile quando manca la foto', () => {
    renderProfile(clientState());

    expect(
      screen.getByRole('img', { name: 'Nessuna foto profilo' }),
    ).toBeInTheDocument();
  });

  it('mostra badge testuale dello stato operativo corrente', () => {
    renderProfile(clientState());

    const statusSection = screen
      .getByRole('heading', { name: 'Stato operativo' })
      .closest('section');
    expect(statusSection).not.toBeNull();
    expect(
      within(statusSection!).getByText('Stato corrente'),
    ).toBeInTheDocument();
    expect(
      within(statusSection!).getAllByText('Attivo').length,
    ).toBeGreaterThan(0);
  });

  it('T2: doppio status submit non duplica la mutation', async () => {
    const user = userEvent.setup();
    const pending = deferred<MyClientProfileResponse>();
    updateMyOperationalStatus.mockReturnValue(pending.promise);

    const state = clientState();
    renderProfile(state);

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    const submitButton = screen.getByRole('button', { name: 'Aggiorna stato' });
    fireEvent.click(submitButton);
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(updateMyOperationalStatus).toHaveBeenCalledTimes(1);
      expect(
        screen.getByRole('button', { name: 'Aggiornamento…' }),
      ).toBeDisabled();
    });

    pending.resolve({
      ...requireClientProfile(state),
      operationalStatus: 'PAUSA',
    });
    await screen.findByText('Stato aggiornato');
    expect(
      screen.getByRole('button', { name: 'Aggiorna stato' }),
    ).toBeDisabled();
  });

  it('T2: stale soft commit status senza falso successo né reconcile', async () => {
    const user = userEvent.setup();
    const reconcileSession = vi.fn();
    const applyProfileSnapshot = vi.fn(() => {
      throw new StaleAuthOperationError(7, 8);
    });
    const state = clientState();
    updateMyOperationalStatus.mockResolvedValue({
      ...requireClientProfile(state),
      operationalStatus: 'PAUSA',
    });

    renderProfile(state, { applyProfileSnapshot, reconcileSession });

    await user.selectOptions(screen.getByLabelText('Nuovo stato'), 'PAUSA');
    await user.click(screen.getByRole('button', { name: 'Aggiorna stato' }));

    expect(
      await screen.findByText('Operazione non completata. Riprova.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Stato aggiornato')).not.toBeInTheDocument();
    expect(reconcileSession).not.toHaveBeenCalled();
    expect(
      screen.getByRole('button', { name: 'Aggiorna stato' }),
    ).toBeEnabled();
  });
});
