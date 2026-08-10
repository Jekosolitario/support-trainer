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

import * as professionalsApi from '../../api/professionalsApi';
import type {
  ProfessionalDetail,
  ProfessionalSummary,
} from '../../api/professionalsTypes';
import {
  HttpApiError,
  NetworkError,
  type ErrorResponse,
} from '../../api/types';
import { ClientProfessionalDetailPage } from './ClientProfessionalDetailPage';
import { ClientProfessionalsPage } from './ClientProfessionalsPage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const PROFESSIONAL_SUMMARY: ProfessionalSummary = {
  id: 10,
  firstName: 'Grace',
  lastName: 'Hopper',
  profileImageUrl: null,
  specialization: 'NUTRITIONIST',
  operationalStatus: 'FERIE',
  active: true,
};

function professionalDetail(
  overrides: Partial<ProfessionalDetail> = {},
): ProfessionalDetail {
  return {
    ...PROFESSIONAL_SUMMARY,
    phoneNumber: '+39 06 1234567',
    bio: 'Nutrizionista sportiva con esperienza in percorsi personalizzati.',
    workplaceName: 'Centro Benessere Roma',
    city: 'Roma',
    instagramUrl: 'https://instagram.example/grace',
    websiteUrl: 'http://grace.example/profile',
    ...overrides,
  };
}

function httpError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-10T12:00:00Z',
    status,
    code,
    message: 'Messaggio backend riservato',
    path: '/api/v1/professionals/10',
  };
  return new HttpApiError(status, body, new Response(null, { status }));
}

function renderList(options: { readonly strict?: boolean } = {}) {
  const router = createMemoryRouter(
    [
      {
        path: '/app/client/professionals',
        element: <ClientProfessionalsPage />,
      },
      {
        path: '/app/client/professionals/:professionalId',
        element: <p>Destinazione dettaglio professionista</p>,
      },
    ],
    { initialEntries: ['/app/client/professionals'] },
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
        path: '/app/client/professionals/:professionalId',
        element: <ClientProfessionalDetailPage />,
      },
      {
        path: '/app/client/professionals',
        element: <p>Elenco professionisti</p>,
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

describe('ClientProfessionalsPage', () => {
  it('annuncia il loading e mostra summary, label e fallback avatar', async () => {
    const pending = deferred<ProfessionalSummary[]>();
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockReturnValueOnce(
      pending.promise,
    );

    renderList();

    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento professionisti…',
    );

    await act(async () => {
      pending.resolve([
        PROFESSIONAL_SUMMARY,
        {
          id: 20,
          firstName: 'Ada',
          lastName: 'Lovelace',
          profileImageUrl: 'https://example.test/ada.png',
          specialization: 'PERSONAL_TRAINER',
          operationalStatus: 'DISPONIBILE',
          active: false,
        },
      ]);
    });

    expect(
      await screen.findByRole('link', {
        name: 'Apri il profilo professionista Grace Hopper',
      }),
    ).toBeVisible();
    expect(screen.getByText('Nutrizionista')).toBeVisible();
    expect(screen.getByText('In ferie')).toBeVisible();
    expect(screen.getByText('Personal trainer')).toBeVisible();
    expect(screen.getByText('Disponibile')).toBeVisible();
    expect(
      screen.getByRole('img', { name: 'Avatar di Grace Hopper' }),
    ).toHaveTextContent('GH');

    const remoteAvatar = screen.getByRole('img', {
      name: 'Foto profilo di Ada Lovelace',
    });
    fireEvent.error(remoteAvatar);
    expect(
      screen.getByRole('img', { name: 'Avatar di Ada Lovelace' }),
    ).toHaveTextContent('AL');
  });

  it('mostra lo stato vuoto senza inventare CTA', async () => {
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockResolvedValueOnce([]);

    renderList();

    expect(
      await screen.findByText('Nessun professionista collegato al momento.'),
    ).toBeVisible();
    expect(
      screen.queryByText(/cerca|aggiungi|inserisci codice/i),
    ).not.toBeInTheDocument();
  });

  it('mostra un errore neutro e consente il retry', async () => {
    const listSpy = vi
      .spyOn(professionalsApi, 'listMyProfessionals')
      .mockRejectedValueOnce(new NetworkError(new Error('raw offline')))
      .mockResolvedValueOnce([PROFESSIONAL_SUMMARY]);
    const user = userEvent.setup();

    renderList();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(
      'Non è stato possibile caricare i professionisti. Riprova.',
    );
    expect(alert).not.toHaveTextContent('raw offline');

    await user.click(within(alert).getByRole('button', { name: 'Riprova' }));

    expect(
      await screen.findByRole('link', {
        name: 'Apri il profilo professionista Grace Hopper',
      }),
    ).toBeVisible();
    expect(listSpy).toHaveBeenCalledTimes(2);
  });

  it('impedisce due retry nello stesso tick', async () => {
    const retry = deferred<ProfessionalSummary[]>();
    const listSpy = vi
      .spyOn(professionalsApi, 'listMyProfessionals')
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

  it('mostra solo i campi summary utili e nasconde active e campi detail', async () => {
    const responseWithUnexpectedDetailFields = [
      {
        ...PROFESSIONAL_SUMMARY,
        phoneNumber: 'dato detail da non mostrare',
        bio: 'bio detail da non mostrare',
        websiteUrl: 'https://private.example',
      },
    ];
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockResolvedValueOnce(
      responseWithUnexpectedDetailFields,
    );

    renderList();

    expect(await screen.findByText('Grace Hopper')).toBeVisible();
    expect(screen.queryByText('true')).not.toBeInTheDocument();
    expect(
      screen.queryByText('dato detail da non mostrare'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText('bio detail da non mostrare'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/private\.example/)).not.toBeInTheDocument();
  });

  it('naviga al dettaglio corretto tramite un link accessibile', async () => {
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockResolvedValueOnce([
      PROFESSIONAL_SUMMARY,
    ]);
    const user = userEvent.setup();
    const { router } = renderList();

    await user.click(
      await screen.findByRole('link', {
        name: 'Apri il profilo professionista Grace Hopper',
      }),
    );

    expect(router.state.location.pathname).toBe('/app/client/professionals/10');
    expect(
      screen.getByText('Destinazione dettaglio professionista'),
    ).toBeVisible();
  });

  it('non presenta AbortError come errore UI', async () => {
    vi.spyOn(professionalsApi, 'listMyProfessionals').mockRejectedValueOnce(
      new DOMException('aborted', 'AbortError'),
    );

    renderList();

    await waitFor(() => {
      expect(professionalsApi.listMyProfessionals).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento professionisti…',
    );
  });

  it('aborta il caricamento e non committa dopo unmount', async () => {
    const pending = deferred<ProfessionalSummary[]>();
    const listSpy = vi
      .spyOn(professionalsApi, 'listMyProfessionals')
      .mockReturnValueOnce(pending.promise);
    const { unmount } = renderList();

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledTimes(1);
    });
    const signal = listSpy.mock.calls[0]?.[0]?.signal;

    unmount();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve([PROFESSIONAL_SUMMARY]);
    });
  });

  it('in StrictMode esegue un solo bootstrap controllato', async () => {
    const listSpy = vi
      .spyOn(professionalsApi, 'listMyProfessionals')
      .mockResolvedValueOnce([]);

    renderList({ strict: true });

    expect(
      await screen.findByText('Nessun professionista collegato al momento.'),
    ).toBeVisible();
    expect(listSpy).toHaveBeenCalledTimes(1);
  });
});

describe('ClientProfessionalDetailPage', () => {
  it('mostra loading e tutti i campi approvati con link esterni sicuri', async () => {
    const pending = deferred<ProfessionalDetail>();
    vi.spyOn(professionalsApi, 'getProfessionalById').mockReturnValueOnce(
      pending.promise,
    );

    renderDetail('/app/client/professionals/10');

    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento profilo professionista…',
    );

    await act(async () => {
      pending.resolve(professionalDetail());
    });

    const profile = await screen.findByRole('region', { name: 'Grace Hopper' });
    expect(within(profile).getByText('Nutrizionista')).toBeVisible();
    expect(within(profile).getByText('In ferie')).toBeVisible();
    expect(within(profile).getByText(/Nutrizionista sportiva/)).toBeVisible();
    expect(within(profile).getByText('Centro Benessere Roma')).toBeVisible();
    expect(within(profile).getByText('Roma')).toBeVisible();
    expect(within(profile).getByText('+39 06 1234567')).toBeVisible();
    expect(within(profile).queryByText('true')).not.toBeInTheDocument();

    const instagram = within(profile).getByRole('link', {
      name: 'Instagram di Grace Hopper',
    });
    expect(instagram).toHaveAttribute(
      'href',
      'https://instagram.example/grace',
    );
    expect(instagram).toHaveAttribute('target', '_blank');
    expect(instagram).toHaveAttribute('rel', 'noopener noreferrer');

    const website = within(profile).getByRole('link', {
      name: 'Sito web di Grace Hopper',
    });
    expect(website).toHaveAttribute('href', 'http://grace.example/profile');
    expect(website).toHaveAttribute('target', '_blank');
    expect(website).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('omette nullabili assenti senza sezioni o placeholder vuoti', async () => {
    vi.spyOn(professionalsApi, 'getProfessionalById').mockResolvedValueOnce(
      professionalDetail({
        phoneNumber: null,
        bio: null,
        workplaceName: null,
        city: null,
        instagramUrl: null,
        websiteUrl: null,
      }),
    );

    renderDetail('/app/client/professionals/10');

    await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' });
    expect(screen.queryByText('Biografia')).not.toBeInTheDocument();
    expect(screen.queryByText('Luogo di lavoro')).not.toBeInTheDocument();
    expect(screen.queryByText('Città')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Contatti' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('null')).not.toBeInTheDocument();
  });

  it.each(['javascript:alert(1)', 'data:text/html,unsafe', 'non è un URL'])(
    'non rende cliccabile un URL esterno non consentito: %s',
    async (websiteUrl) => {
      vi.spyOn(professionalsApi, 'getProfessionalById').mockResolvedValueOnce(
        professionalDetail({
          phoneNumber: null,
          instagramUrl: null,
          websiteUrl,
        }),
      );

      renderDetail('/app/client/professionals/10');

      await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' });
      expect(
        screen.queryByRole('link', { name: 'Sito web di Grace Hopper' }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText(websiteUrl)).not.toBeInTheDocument();
    },
  );

  it.each([
    ['testo', 'abc'],
    ['whitespace', '%20'],
    ['zero', '0'],
    ['negativo', '-1'],
    ['frazionario', '1.5'],
    ['esponenziale', '1e3'],
    ['leading zero', '01'],
    ['plus', '+1'],
    ['hex', '0x10'],
    ['partial', '12abc'],
    ['unsafe', '9007199254740992'],
  ])('neutralizza professionalId %s senza API', async (_label, rawId) => {
    const detailSpy = vi.spyOn(professionalsApi, 'getProfessionalById');

    renderDetail(`/app/client/professionals/${rawId}`);

    expect(
      screen.getByText('Profilo professionista non disponibile.'),
    ).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Torna ai professionisti' }),
    ).toHaveAttribute('href', '/app/client/professionals');
    await act(async () => Promise.resolve());
    expect(detailSpy).not.toHaveBeenCalled();
  });

  it('neutralizza il parametro assente senza chiamare API', async () => {
    const detailSpy = vi.spyOn(professionalsApi, 'getProfessionalById');

    render(
      <MemoryRouter>
        <ClientProfessionalDetailPage />
      </MemoryRouter>,
    );

    expect(
      screen.getByText('Profilo professionista non disponibile.'),
    ).toBeVisible();
    await act(async () => Promise.resolve());
    expect(detailSpy).not.toHaveBeenCalled();
  });

  it('rende un 404 indistinguibile da un parametro invalido', async () => {
    vi.spyOn(professionalsApi, 'getProfessionalById').mockRejectedValueOnce(
      httpError(404, 'PROFESSIONAL_NOT_FOUND'),
    );

    renderDetail('/app/client/professionals/10');

    expect(
      await screen.findByText('Profilo professionista non disponibile.'),
    ).toBeVisible();
    expect(
      screen.queryByText('PROFESSIONAL_NOT_FOUND'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText('Messaggio backend riservato'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Riprova' }),
    ).not.toBeInTheDocument();
  });

  it('mantiene un 403 come errore generico retryable', async () => {
    vi.spyOn(professionalsApi, 'getProfessionalById').mockRejectedValueOnce(
      httpError(403, 'ROLE_NOT_ALLOWED'),
    );

    renderDetail('/app/client/professionals/10');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(
      'Non è stato possibile caricare il profilo professionista. Riprova.',
    );
    expect(alert).not.toHaveTextContent('ROLE_NOT_ALLOWED');
    expect(
      screen.queryByText('Profilo professionista non disponibile.'),
    ).not.toBeInTheDocument();
  });

  it('esegue retry dopo errore generico e mostra il successo', async () => {
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockRejectedValueOnce(new NetworkError(new Error('raw offline')))
      .mockResolvedValueOnce(professionalDetail());
    const user = userEvent.setup();

    renderDetail('/app/client/professionals/10');

    await user.click(await screen.findByRole('button', { name: 'Riprova' }));

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(2);
  });

  it('impedisce due retry detail nello stesso tick', async () => {
    const retry = deferred<ProfessionalDetail>();
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockRejectedValueOnce(new NetworkError(new Error('offline')))
      .mockReturnValueOnce(retry.promise);

    renderDetail('/app/client/professionals/10');

    const retryButton = await screen.findByRole('button', { name: 'Riprova' });
    act(() => {
      fireEvent.click(retryButton);
      fireEvent.click(retryButton);
    });

    expect(detailSpy).toHaveBeenCalledTimes(2);

    await act(async () => {
      retry.resolve(professionalDetail());
    });
  });

  it('ignora A tardiva dopo il cambio rapido verso B', async () => {
    const responseA = deferred<ProfessionalDetail>();
    const responseB = deferred<ProfessionalDetail>();
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockReturnValueOnce(responseA.promise)
      .mockReturnValueOnce(responseB.promise);
    const { router } = renderDetail('/app/client/professionals/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    await act(async () => {
      await router.navigate('/app/client/professionals/20');
    });
    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(2);
    });

    await act(async () => {
      responseB.resolve(
        professionalDetail({ id: 20, firstName: 'Ada', lastName: 'Lovelace' }),
      );
    });
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();

    await act(async () => {
      responseA.resolve(professionalDetail());
    });
    expect(
      screen.getByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).not.toBeInTheDocument();
    expect(detailSpy.mock.calls[0]?.[1]?.signal?.aborted).toBe(true);
  });

  it('una response antecedente non sovrascrive il retry corrente', async () => {
    const staleA = deferred<ProfessionalDetail>();
    const retryB = deferred<ProfessionalDetail>();
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockReturnValueOnce(staleA.promise)
      .mockRejectedValueOnce(new NetworkError(new Error('B offline')))
      .mockReturnValueOnce(retryB.promise);
    const user = userEvent.setup();
    const { router } = renderDetail('/app/client/professionals/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    await act(async () => {
      await router.navigate('/app/client/professionals/20');
    });
    await user.click(await screen.findByRole('button', { name: 'Riprova' }));

    await act(async () => {
      retryB.resolve(
        professionalDetail({ id: 20, firstName: 'Ada', lastName: 'Lovelace' }),
      );
    });
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();

    await act(async () => {
      staleA.resolve(professionalDetail());
    });
    expect(
      screen.getByRole('heading', { level: 2, name: 'Ada Lovelace' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(3);
  });

  it('non presenta AbortError come errore UI', async () => {
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockRejectedValueOnce(new DOMException('aborted', 'AbortError'));

    renderDetail('/app/client/professionals/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Caricamento profilo professionista…',
    );
  });

  it('aborta il dettaglio e non committa dopo unmount', async () => {
    const pending = deferred<ProfessionalDetail>();
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockReturnValueOnce(pending.promise);
    const { unmount } = renderDetail('/app/client/professionals/10');

    await waitFor(() => {
      expect(detailSpy).toHaveBeenCalledTimes(1);
    });
    const signal = detailSpy.mock.calls[0]?.[1]?.signal;

    unmount();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve(professionalDetail());
    });
  });

  it('in StrictMode esegue un solo bootstrap controllato', async () => {
    const detailSpy = vi
      .spyOn(professionalsApi, 'getProfessionalById')
      .mockResolvedValueOnce(professionalDetail());

    renderDetail('/app/client/professionals/10', { strict: true });

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Grace Hopper' }),
    ).toBeVisible();
    expect(detailSpy).toHaveBeenCalledTimes(1);
  });
});
