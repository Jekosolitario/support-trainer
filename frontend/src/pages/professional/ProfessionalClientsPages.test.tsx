import { StrictMode } from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  createMemoryRouter,
  MemoryRouter,
  RouterProvider,
} from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as clientsApi from '../../api/clientsApi';
import type { ClientDetail, ClientSummary } from '../../api/clientsTypes';
import {
  HttpApiError,
  NetworkError,
  type ErrorResponse,
} from '../../api/types';
import { ProfessionalClientDetailPage } from './ProfessionalClientDetailPage';
import { ProfessionalClientsPage } from './ProfessionalClientsPage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const CLIENT_SUMMARY: ClientSummary = {
  id: 10,
  firstName: 'Ada',
  lastName: 'Lovelace',
  profileImageUrl: null,
};

function clientDetail(overrides: Partial<ClientDetail> = {}): ClientDetail {
  return {
    ...CLIENT_SUMMARY,
    primaryGoal: 'Migliorare la mobilità',
    operationalStatus: 'PAUSA',
    birthDate: '1995-08-10',
    heightCm: 180,
    gender: 'NOT_SPECIFIED',
    ...overrides,
  };
}

function httpError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-10T12:00:00Z',
    status,
    code,
    message: 'Messaggio backend riservato',
    path: '/api/v1/clients/10',
  };
  return new HttpApiError(status, body, new Response(null, { status }));
}

function renderList(options: { readonly strict?: boolean } = {}) {
  const router = createMemoryRouter(
    [
      {
        path: '/app/professional/clients',
        element: <ProfessionalClientsPage />,
      },
      {
        path: '/app/professional/clients/:clientId',
        element: <p>Destinazione dettaglio</p>,
      },
    ],
    { initialEntries: ['/app/professional/clients'] },
  );
  const tree = <RouterProvider router={router} />;
  const view = render(options.strict ? <StrictMode>{tree}</StrictMode> : tree);
  return { ...view, router };
}

function renderDetail(
  path: string,
  options: { readonly strict?: boolean } = {},
) {
  const router = createMemoryRouter(
    [
      {
        path: '/app/professional/clients/:clientId',
        element: <ProfessionalClientDetailPage />,
      },
      {
        path: '/app/professional/clients',
        element: <p>Elenco clienti</p>,
      },
    ],
    { initialEntries: [path] },
  );
  const tree = <RouterProvider router={router} />;
  const view = render(options.strict ? <StrictMode>{tree}</StrictMode> : tree);
  return { ...view, router };
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('ProfessionalClientsPage', () => {
  it('annuncia il loading iniziale e poi mostra tutti i clienti', async () => {
    const pending = deferred<ClientSummary[]>();
    vi.spyOn(clientsApi, 'listMyClients').mockReturnValueOnce(pending.promise);

    renderList();

    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento clienti…',
    );

    await act(async () => {
      pending.resolve([
        CLIENT_SUMMARY,
        {
          id: 20,
          firstName: 'Grace',
          lastName: 'Hopper',
          profileImageUrl: 'https://example.test/grace.png',
        },
      ]);
    });

    expect(
      await screen.findByRole('link', {
        name: 'Apri il profilo cliente Ada Lovelace',
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('link', {
        name: 'Apri il profilo cliente Grace Hopper',
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('img', { name: 'Avatar di Ada Lovelace' }),
    ).toBeVisible();
    const remoteAvatar = screen.getByRole('img', {
      name: 'Foto profilo di Grace Hopper',
    });
    expect(remoteAvatar).toHaveAttribute(
      'src',
      'https://example.test/grace.png',
    );

    fireEvent.error(remoteAvatar);
    expect(
      screen.getByRole('img', { name: 'Avatar di Grace Hopper' }),
    ).toHaveTextContent('GH');
  });

  it('mostra lo stato vuoto senza inventare CTA', async () => {
    vi.spyOn(clientsApi, 'listMyClients').mockResolvedValueOnce([]);

    renderList();

    expect(
      await screen.findByText('Nessun cliente collegato al momento.'),
    ).toBeVisible();
    expect(
      screen.queryByText(/aggiungi|cerca cliente/i),
    ).not.toBeInTheDocument();
  });

  it('mostra errore neutro e retry manuale fino al successo', async () => {
    const listSpy = vi
      .spyOn(clientsApi, 'listMyClients')
      .mockRejectedValueOnce(new NetworkError(new Error('offline raw detail')))
      .mockResolvedValueOnce([CLIENT_SUMMARY]);
    const user = userEvent.setup();

    renderList();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(
      'Non è stato possibile caricare i clienti. Riprova.',
    );
    expect(alert).not.toHaveTextContent('offline raw detail');

    await user.click(within(alert).getByRole('button', { name: 'Riprova' }));

    expect(
      await screen.findByRole('link', {
        name: 'Apri il profilo cliente Ada Lovelace',
      }),
    ).toBeVisible();
    expect(listSpy).toHaveBeenCalledTimes(2);
  });

  it('impedisce due retry nello stesso tick', async () => {
    const retry = deferred<ClientSummary[]>();
    const listSpy = vi
      .spyOn(clientsApi, 'listMyClients')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockReturnValueOnce(retry.promise);

    renderList();

    const retryButton = await screen.findByRole('button', { name: 'Riprova' });
    act(() => {
      fireEvent.click(retryButton);
      fireEvent.click(retryButton);
    });

    expect(listSpy).toHaveBeenCalledTimes(2);

    await act(async () => {
      retry.resolve([]);
    });
  });

  it('usa soltanto i campi summary nella card', async () => {
    const responseWithUnexpectedDetailFields = [
      {
        ...CLIENT_SUMMARY,
        primaryGoal: 'Dato detail da non mostrare',
        operationalStatus: 'ATTIVO',
      },
    ];
    vi.spyOn(clientsApi, 'listMyClients').mockResolvedValueOnce(
      responseWithUnexpectedDetailFields,
    );

    renderList();

    expect(await screen.findByText('Ada Lovelace')).toBeVisible();
    expect(
      screen.queryByText('Dato detail da non mostrare'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('ATTIVO')).not.toBeInTheDocument();
  });

  it('naviga con un link accessibile verso il dettaglio corretto', async () => {
    vi.spyOn(clientsApi, 'listMyClients').mockResolvedValueOnce([
      CLIENT_SUMMARY,
    ]);
    const user = userEvent.setup();
    const { router } = renderList();

    await user.click(
      await screen.findByRole('link', {
        name: 'Apri il profilo cliente Ada Lovelace',
      }),
    );

    expect(router.state.location.pathname).toBe('/app/professional/clients/10');
    expect(screen.getByText('Destinazione dettaglio')).toBeVisible();
  });

  it('non presenta AbortError come errore UI', async () => {
    vi.spyOn(clientsApi, 'listMyClients').mockRejectedValueOnce(
      new DOMException('aborted', 'AbortError'),
    );

    renderList();

    await waitFor(() => {
      expect(clientsApi.listMyClients).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento clienti…',
    );
  });

  it('aborta il caricamento e non committa dopo unmount', async () => {
    const pending = deferred<ClientSummary[]>();
    const listSpy = vi
      .spyOn(clientsApi, 'listMyClients')
      .mockReturnValueOnce(pending.promise);
    const { unmount } = renderList();

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledTimes(1);
    });
    const signal = listSpy.mock.calls[0]?.[0]?.signal;

    unmount();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve([CLIENT_SUMMARY]);
    });
  });

  it('in StrictMode effettua un solo caricamento iniziale controllato', async () => {
    const listSpy = vi
      .spyOn(clientsApi, 'listMyClients')
      .mockResolvedValueOnce([]);

    renderList({ strict: true });

    expect(
      await screen.findByText('Nessun cliente collegato al momento.'),
    ).toBeVisible();
    expect(listSpy).toHaveBeenCalledTimes(1);
  });
});

describe('ProfessionalClientDetailPage', () => {
  it('mostra loading e tutti i dati approvati con label leggibili', async () => {
    const pending = deferred<ClientDetail>();
    vi.spyOn(clientsApi, 'getClientById').mockReturnValueOnce(pending.promise);

    renderDetail('/app/professional/clients/10');

    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento profilo cliente…',
    );

    await act(async () => {
      pending.resolve(clientDetail());
    });

    const profile = await screen.findByRole('region', {
      name: 'Ada Lovelace',
    });
    expect(within(profile).getByText('Migliorare la mobilità')).toBeVisible();
    expect(within(profile).getByText('10/08/1995')).toBeVisible();
    expect(within(profile).getByText('180 cm')).toBeVisible();
    expect(within(profile).getByText('Non specificato')).toBeVisible();
    expect(within(profile).getByText('In pausa')).toBeVisible();
    expect(
      within(profile).getByRole('img', { name: 'Avatar di Ada Lovelace' }),
    ).toBeVisible();
    expect(within(profile).getByText('Genere dichiarato')).toBeVisible();
    expect(
      screen.queryByText(/medical|emailVerified|account/i),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['non numerico', 'abc'],
    ['zero', '0'],
    ['negativo', '-1'],
    ['frazionario', '1.5'],
    ['esponenziale', '1e2'],
    ['leading zero', '01'],
    ['unsafe', '9007199254740992'],
  ])('neutralizza clientId %s senza chiamare API', async (_label, rawId) => {
    const detailSpy = vi.spyOn(clientsApi, 'getClientById');

    renderDetail(`/app/professional/clients/${rawId}`);

    expect(screen.getByText('Profilo cliente non disponibile.')).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Torna ai clienti' }),
    ).toHaveAttribute('href', '/app/professional/clients');
    await act(async () => Promise.resolve());
    expect(detailSpy).not.toHaveBeenCalled();
  });

  it('neutralizza il parametro assente senza chiamare API', async () => {
    const detailSpy = vi.spyOn(clientsApi, 'getClientById');

    render(
      <MemoryRouter>
        <ProfessionalClientDetailPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('Profilo cliente non disponibile.')).toBeVisible();
    await act(async () => Promise.resolve());
    expect(detailSpy).not.toHaveBeenCalled();
  });

  it('rende un 404 indistinguibile da un parametro invalido', async () => {
    vi.spyOn(clientsApi, 'getClientById').mockRejectedValueOnce(
      httpError(404, 'CLIENT_NOT_FOUND'),
    );

    renderDetail('/app/professional/clients/10');

    expect(
      await screen.findByText('Profilo cliente non disponibile.'),
    ).toBeVisible();
    expect(screen.queryByText('CLIENT_NOT_FOUND')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Messaggio backend riservato'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Riprova' }),
    ).not.toBeInTheDocument();
  });

  it('non trasforma 403 in 404 e offre retry generico', async () => {
    vi.spyOn(clientsApi, 'getClientById').mockRejectedValueOnce(
      httpError(403, 'ROLE_NOT_ALLOWED'),
    );

    renderDetail('/app/professional/clients/10');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(
      'Non è stato possibile caricare il profilo cliente. Riprova.',
    );
    expect(alert).not.toHaveTextContent('ROLE_NOT_ALLOWED');
    expect(
      screen.queryByText('Profilo cliente non disponibile.'),
    ).not.toBeInTheDocument();
  });

  it('esegue retry dopo errore generico e mostra il successo', async () => {
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockRejectedValueOnce(new NetworkError(new Error('offline raw')))
      .mockResolvedValueOnce(clientDetail());
    const user = userEvent.setup();

    renderDetail('/app/professional/clients/10');

    await user.click(await screen.findByRole('button', { name: 'Riprova' }));

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(2);
    expect(detailSpy.mock.calls[0]?.[0]).toBe(10);
    expect(detailSpy.mock.calls[1]?.[0]).toBe(10);
  });

  it('ignora la response A tardiva dopo il cambio rapido verso B', async () => {
    const responseA = deferred<ClientDetail>();
    const responseB = deferred<ClientDetail>();
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockReturnValueOnce(responseA.promise)
      .mockReturnValueOnce(responseB.promise);
    const { router } = renderDetail('/app/professional/clients/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    await act(async () => {
      await router.navigate('/app/professional/clients/20');
    });
    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(2);
    });

    await act(async () => {
      responseB.resolve(
        clientDetail({ id: 20, firstName: 'Grace', lastName: 'Hopper' }),
      );
    });
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();

    await act(async () => {
      responseA.resolve(clientDetail());
    });
    expect(
      screen.getByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).not.toBeInTheDocument();
    expect(detailSpy.mock.calls[0]?.[1]?.signal?.aborted).toBe(true);
  });

  it('una response antecedente non sovrascrive il successo del retry corrente', async () => {
    const staleA = deferred<ClientDetail>();
    const retryB = deferred<ClientDetail>();
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockReturnValueOnce(staleA.promise)
      .mockRejectedValueOnce(new NetworkError(new Error('B offline')))
      .mockReturnValueOnce(retryB.promise);
    const user = userEvent.setup();
    const { router } = renderDetail('/app/professional/clients/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    await act(async () => {
      await router.navigate('/app/professional/clients/20');
    });
    await user.click(await screen.findByRole('button', { name: 'Riprova' }));

    await act(async () => {
      retryB.resolve(
        clientDetail({ id: 20, firstName: 'Grace', lastName: 'Hopper' }),
      );
    });
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();

    await act(async () => {
      staleA.resolve(clientDetail());
    });
    expect(
      screen.getByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(3);
  });

  it('non presenta AbortError come errore UI', async () => {
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockRejectedValueOnce(new DOMException('aborted', 'AbortError'));

    renderDetail('/app/professional/clients/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento profilo cliente…',
    );
  });

  it('aborta il dettaglio e non committa dopo unmount', async () => {
    const pending = deferred<ClientDetail>();
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockReturnValueOnce(pending.promise);
    const { unmount } = renderDetail('/app/professional/clients/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    const signal = detailSpy.mock.calls[0]?.[1]?.signal;

    unmount();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve(clientDetail());
    });
  });

  it('in StrictMode effettua un solo caricamento iniziale controllato', async () => {
    const detailSpy = vi
      .spyOn(clientsApi, 'getClientById')
      .mockResolvedValueOnce(clientDetail());

    renderDetail('/app/professional/clients/10', { strict: true });

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(1);
  });
});
