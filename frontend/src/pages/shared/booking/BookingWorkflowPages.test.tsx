import { StrictMode } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as availabilityApi from '../../../api/availabilityApi';
import type { ClientAvailabilityWindow } from '../../../api/availabilityTypes';
import * as bookingApi from '../../../api/bookingApi';
import type { BookingDetail, BookingSummary } from '../../../api/bookingTypes';
import { HttpApiError, type ErrorResponse } from '../../../api/types';
import pageTemplateStyles from '../../../components/page/PageTemplate.module.css';
import { ClientBookingDetailPage } from '../../client/ClientBookingDetailPage';
import { ClientBookingsPage } from '../../client/ClientBookingsPage';
import { ClientProfessionalAvailabilityPage } from '../../client/ClientProfessionalAvailabilityPage';
import { ProfessionalBookingDetailPage } from '../../professional/ProfessionalBookingDetailPage';
import { ProfessionalBookingsPage } from '../../professional/ProfessionalBookingsPage';

const FUTURE_START = '2099-01-20T10:00:00+01:00';
const FUTURE_END = '2099-01-20T11:00:00+01:00';

function detail(overrides: Partial<BookingDetail> = {}): BookingDetail {
  return {
    id: 41,
    status: 'PENDING',
    client: {
      id: 2,
      displayName: 'Luigi Bianchi',
      profileImageUrl: null,
      specialization: null,
    },
    professional: {
      id: 7,
      displayName: 'Mario Rossi',
      profileImageUrl: null,
      specialization: 'PERSONAL_TRAINER',
    },
    scheduledStart: FUTURE_START,
    scheduledEnd: FUTURE_END,
    durationMinutes: 60,
    note: null,
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-10T08:00:00Z',
    confirmedAt: null,
    rejectedAt: null,
    cancelledAt: null,
    rejectionReason: null,
    cancellationReason: null,
    cancelledBy: null,
    items: [
      {
        id: 1,
        availabilitySlotId: 31,
        scheduledStart: FUTURE_START,
        scheduledEnd: FUTURE_END,
        durationMinutes: 60,
        locationLabel: 'Studio',
      },
    ],
    ...overrides,
  };
}

function summary(overrides: Partial<BookingSummary> = {}): BookingSummary {
  return {
    id: 41,
    status: 'PENDING',
    counterparty: detail().professional,
    scheduledStart: FUTURE_START,
    scheduledEnd: FUTURE_END,
    durationMinutes: 60,
    note: null,
    createdAt: '2026-08-10T08:00:00Z',
    ...overrides,
  };
}

function httpError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-10T08:00:00Z',
    status,
    code,
    message: 'Errore',
    path: '/api/v1/bookings/41',
  };
  return new HttpApiError(status, body, new Response(null, { status }));
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function availability(
  occurrenceId: number,
  startDateTime: string,
  location: string,
): ClientAvailabilityWindow {
  const day = startDateTime.slice(0, 10);
  return {
    occurrenceId,
    windowStart: `${day}T09:00:00+02:00`,
    windowEnd: `${day}T13:00:00+02:00`,
    allowedDurations: [60],
    startIntervalMinutes: 15,
    location,
    capacity: 1,
    bookableOptions: [{ startDateTime, allowedDurations: [60] }],
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Client availability Booking flow', () => {
  it('usa occurrence/bookableOptions, impedisce doppio submit e naviga replace al detail', async () => {
    vi.spyOn(availabilityApi, 'listProfessionalAvailability').mockResolvedValue(
      [
        {
          occurrenceId: 31,
          windowStart: '2026-08-20T09:00:00+02:00',
          windowEnd: '2026-08-20T12:00:00+02:00',
          allowedDurations: [60],
          startIntervalMinutes: 15,
          location: 'Studio',
          capacity: 99,
          bookableOptions: [
            {
              startDateTime: '2026-08-20T10:00:00+02:00',
              allowedDurations: [60],
            },
          ],
        },
      ],
    );
    const pending = deferred<BookingDetail>();
    const createSpy = vi
      .spyOn(bookingApi, 'createBooking')
      .mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
        {
          path: '/app/client/bookings/:bookingRequestId',
          element: <p>Detail autorevole</p>,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /20 agosto/ }));
    await user.click(screen.getByRole('button', { name: /10:00.*Studio/ }));
    await user.click(screen.getByRole('button', { name: '60 minuti' }));
    await user.type(screen.getByRole('textbox'), '  Nota utile  ');
    const submit = screen.getByRole('button', { name: 'Invia richiesta' });
    await user.click(submit);
    await user.click(submit);

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(createSpy).toHaveBeenCalledWith(
      {
        availabilitySlotId: 31,
        startDateTime: '2026-08-20T10:00:00+02:00',
        durationMinutes: 60,
        note: 'Nota utile',
      },
      { signal: expect.any(AbortSignal) },
    );
    await act(async () => pending.resolve(detail()));
    expect(await screen.findByText('Detail autorevole')).toBeVisible();
    expect(router.state.location.pathname).toBe('/app/client/bookings/41');
    expect(router.state.historyAction).toBe('REPLACE');
  });

  it('mostra empty, retry e neutral 404 senza inferenze', async () => {
    const listSpy = vi
      .spyOn(availabilityApi, 'listProfessionalAvailability')
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(httpError(404, 'PROFESSIONAL_NOT_FOUND'));
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();
    expect(
      await screen.findByRole('button', { name: 'Aggiorna' }),
    ).toBeVisible();
    expect(
      screen
        .getByRole('heading', {
          level: 1,
          name: 'Disponibilità professionista',
        })
        .closest('article'),
    ).toHaveClass(pageTemplateStyles.authenticated);
    await user.click(screen.getByRole('button', { name: 'Aggiorna' }));
    expect(
      await screen.findByText('Professionista non disponibile'),
    ).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Torna ai professionisti' }),
    ).toHaveAttribute('href', '/app/client/professionals');
    expect(listSpy).toHaveBeenCalledTimes(2);
  });

  it('invalida completamente la selezione quando cambia professionalId', async () => {
    vi.spyOn(
      availabilityApi,
      'listProfessionalAvailability',
    ).mockImplementation((professionalId) =>
      Promise.resolve([
        professionalId === 7
          ? availability(31, '2026-08-20T10:00:00+02:00', 'Studio A')
          : availability(32, '2026-08-21T11:00:00+02:00', 'Studio B'),
      ]),
    );
    const createSpy = vi.spyOn(bookingApi, 'createBooking');
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /20 agosto/ }));
    await user.click(screen.getByRole('button', { name: /10:00.*Studio A/ }));
    await user.click(screen.getByRole('button', { name: '60 minuti' }));
    expect(
      screen.getByRole('button', { name: 'Invia richiesta' }),
    ).toBeVisible();

    await act(async () => {
      await router.navigate('/app/client/professionals/8/availability');
    });

    expect(
      await screen.findByRole('button', { name: /21 agosto/ }),
    ).toBeVisible();
    expect(screen.queryByText(/Studio A/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Invia richiesta' }),
    ).not.toBeInTheDocument();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('ignora la create PT A che risolve dopo il passaggio al PT B', async () => {
    vi.spyOn(
      availabilityApi,
      'listProfessionalAvailability',
    ).mockImplementation((professionalId) =>
      Promise.resolve([
        professionalId === 7
          ? availability(31, '2026-08-20T10:00:00+02:00', 'Studio A')
          : availability(32, '2026-08-21T11:00:00+02:00', 'Studio B'),
      ]),
    );
    const pending = deferred<BookingDetail>();
    const createSpy = vi
      .spyOn(bookingApi, 'createBooking')
      .mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
        {
          path: '/app/client/bookings/:bookingRequestId',
          element: <p>Detail A non pertinente</p>,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /20 agosto/ }));
    await user.click(screen.getByRole('button', { name: /10:00.*Studio A/ }));
    await user.click(screen.getByRole('button', { name: '60 minuti' }));
    await user.click(screen.getByRole('button', { name: 'Invia richiesta' }));
    const signal = createSpy.mock.calls[0]?.[1]?.signal;

    await act(async () => {
      await router.navigate('/app/client/professionals/8/availability');
    });
    expect(
      await screen.findByRole('button', { name: /21 agosto/ }),
    ).toBeVisible();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve(detail());
      await pending.promise;
    });

    expect(router.state.location.pathname).toBe(
      '/app/client/professionals/8/availability',
    );
    expect(
      screen.queryByText('Detail A non pertinente'),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('aborta e ignora una create che risolve dopo unmount', async () => {
    vi.spyOn(availabilityApi, 'listProfessionalAvailability').mockResolvedValue(
      [availability(31, '2026-08-20T10:00:00+02:00', 'Studio')],
    );
    const pending = deferred<BookingDetail>();
    const createSpy = vi
      .spyOn(bookingApi, 'createBooking')
      .mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    const view = render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /20 agosto/ }));
    await user.click(screen.getByRole('button', { name: /10:00.*Studio/ }));
    await user.click(screen.getByRole('button', { name: '60 minuti' }));
    await user.click(screen.getByRole('button', { name: 'Invia richiesta' }));
    const signal = createSpy.mock.calls[0]?.[1]?.signal;

    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      pending.resolve(detail());
      await pending.promise;
    });

    expect(router.state.location.pathname).toBe(
      '/app/client/professionals/7/availability',
    );
  });

  it.each([
    [409, 'AVAILABILITY_SLOT_CAPACITY_EXHAUSTED'],
    [404, 'AVAILABILITY_SLOT_NOT_FOUND'],
  ])(
    'su create stale %s ricarica Availability, invalida la selezione e non ripete il POST',
    async (status, code) => {
      const listSpy = vi
        .spyOn(availabilityApi, 'listProfessionalAvailability')
        .mockResolvedValue([
          availability(31, '2026-08-20T10:00:00+02:00', 'Studio'),
        ]);
      const createSpy = vi
        .spyOn(bookingApi, 'createBooking')
        .mockRejectedValue(httpError(status, code));
      const router = createMemoryRouter(
        [
          {
            path: '/app/client/professionals/:professionalId/availability',
            element: <ClientProfessionalAvailabilityPage />,
          },
        ],
        { initialEntries: ['/app/client/professionals/7/availability'] },
      );
      render(<RouterProvider router={router} />);
      const user = userEvent.setup();

      await user.click(
        await screen.findByRole('button', { name: /20 agosto/ }),
      );
      await user.click(screen.getByRole('button', { name: /10:00.*Studio/ }));
      await user.click(screen.getByRole('button', { name: '60 minuti' }));
      await user.click(screen.getByRole('button', { name: 'Invia richiesta' }));

      expect(
        await screen.findByText(/disponibilità selezionata non è più attuale/i),
      ).toBeVisible();
      await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
      expect(createSpy).toHaveBeenCalledTimes(1);
      expect(
        screen.queryByRole('button', { name: 'Invia richiesta' }),
      ).not.toBeInTheDocument();
    },
  );

  it('ignora un 409 create stale durante route change senza refetch del PT A', async () => {
    const listSpy = vi
      .spyOn(availabilityApi, 'listProfessionalAvailability')
      .mockImplementation((professionalId) =>
        Promise.resolve([
          professionalId === 7
            ? availability(31, '2026-08-20T10:00:00+02:00', 'Studio A')
            : availability(32, '2026-08-21T11:00:00+02:00', 'Studio B'),
        ]),
      );
    const pending = deferred<BookingDetail>();
    vi.spyOn(bookingApi, 'createBooking').mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /20 agosto/ }));
    await user.click(screen.getByRole('button', { name: /10:00.*Studio A/ }));
    await user.click(screen.getByRole('button', { name: '60 minuti' }));
    await user.click(screen.getByRole('button', { name: 'Invia richiesta' }));
    await act(async () => {
      await router.navigate('/app/client/professionals/8/availability');
    });
    expect(
      await screen.findByRole('button', { name: /21 agosto/ }),
    ).toBeVisible();

    await act(async () => {
      pending.reject(httpError(409, 'AVAILABILITY_SLOT_CAPACITY_EXHAUSTED'));
      await pending.promise.catch(() => undefined);
    });

    expect(listSpy).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('espone programmaticamente lo stato selezionato di giorno, orario e durata', async () => {
    vi.spyOn(availabilityApi, 'listProfessionalAvailability').mockResolvedValue(
      [
        {
          occurrenceId: 31,
          windowStart: '2026-08-20T09:00:00+02:00',
          windowEnd: '2026-08-20T12:00:00+02:00',
          allowedDurations: [45, 60],
          startIntervalMinutes: 15,
          location: 'Studio A',
          capacity: 99,
          bookableOptions: [
            {
              startDateTime: '2026-08-20T10:00:00+02:00',
              allowedDurations: [45, 60],
            },
            {
              startDateTime: '2026-08-20T11:00:00+02:00',
              allowedDurations: [60],
            },
          ],
        },
        {
          occurrenceId: 32,
          windowStart: '2026-08-21T09:00:00+02:00',
          windowEnd: '2026-08-21T12:00:00+02:00',
          allowedDurations: [60],
          startIntervalMinutes: 15,
          location: 'Studio B',
          capacity: 99,
          bookableOptions: [
            {
              startDateTime: '2026-08-21T11:00:00+02:00',
              allowedDurations: [60],
            },
          ],
        },
      ],
    );
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/professionals/:professionalId/availability',
          element: <ClientProfessionalAvailabilityPage />,
        },
      ],
      { initialEntries: ['/app/client/professionals/7/availability'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    const day20 = await screen.findByRole('button', { name: /20 agosto/ });
    const day21 = screen.getByRole('button', { name: /21 agosto/ });
    expect(day20).toHaveAttribute('aria-pressed', 'false');
    expect(day21).toHaveAttribute('aria-pressed', 'false');

    day20.focus();
    await user.keyboard('{Enter}');
    expect(day20).toHaveAttribute('aria-pressed', 'true');
    expect(day21).toHaveAttribute('aria-pressed', 'false');

    const time10 = screen.getByRole('button', { name: /10:00.*Studio A/ });
    const time11 = screen.getByRole('button', { name: /11:00.*Studio A/ });
    expect(time10).toHaveAttribute('aria-pressed', 'false');
    time10.focus();
    await user.keyboard('{Enter}');
    expect(time10).toHaveAttribute('aria-pressed', 'true');
    expect(time11).toHaveAttribute('aria-pressed', 'false');

    const duration45 = screen.getByRole('button', { name: '45 minuti' });
    const duration60 = screen.getByRole('button', { name: '60 minuti' });
    duration45.focus();
    await user.keyboard('{Enter}');
    expect(duration45).toHaveAttribute('aria-pressed', 'true');
    expect(duration60).toHaveAttribute('aria-pressed', 'false');
    await user.click(duration60);
    expect(duration60).toHaveAttribute('aria-pressed', 'true');
    expect(duration45).toHaveAttribute('aria-pressed', 'false');

    await user.click(day21);
    expect(day21).toHaveAttribute('aria-pressed', 'true');
    expect(day20).toHaveAttribute('aria-pressed', 'false');
    expect(
      screen.queryByRole('button', { name: '45 minuti' }),
    ).not.toBeInTheDocument();
  });
});

describe('Booking list and detail actions', () => {
  it('raggruppa prossime/storico e presenta il pending passato come scaduto', async () => {
    vi.spyOn(bookingApi, 'listClientBookings').mockResolvedValue([
      summary({ id: 1 }),
      summary({
        id: 2,
        scheduledStart: '2000-01-20T10:00:00+01:00',
        scheduledEnd: '2000-01-20T11:00:00+01:00',
      }),
      summary({ id: 3, status: 'REJECTED' }),
    ]);
    const router = createMemoryRouter(
      [{ path: '/app/client/bookings', element: <ClientBookingsPage /> }],
      { initialEntries: ['/app/client/bookings'] },
    );
    render(<RouterProvider router={router} />);

    expect(
      await screen.findByRole('heading', { name: 'Prossime' }),
    ).toBeVisible();
    expect(
      screen
        .getByRole('heading', { level: 1, name: 'Prenotazioni' })
        .closest('article'),
    ).toHaveClass(pageTemplateStyles.authenticated);
    const upcomingLink = screen.getByRole('link', { name: /In attesa/ });
    expect(upcomingLink).toHaveAttribute('href', '/app/client/bookings/1');
    expect(upcomingLink.closest('li')).not.toBeNull();
    expect(screen.getByRole('heading', { name: 'Prossime' })).toHaveAttribute(
      'id',
      'client-bookings-upcoming-title',
    );
    expect(screen.getByRole('region', { name: 'Prossime' })).toHaveAttribute(
      'aria-labelledby',
      'client-bookings-upcoming-title',
    );
    expect(screen.getByRole('heading', { name: 'Storico' })).toBeVisible();
    expect(screen.getByText('Richiesta scaduta')).toBeVisible();
  });

  it('raggruppa da gestire, confermate e storico per il professionista', async () => {
    vi.spyOn(bookingApi, 'listProfessionalBookings').mockResolvedValue([
      summary({ id: 1, status: 'PENDING' }),
      summary({ id: 2, status: 'CONFIRMED' }),
      summary({ id: 3, status: 'REJECTED' }),
    ]);
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings',
          element: <ProfessionalBookingsPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings'] },
    );
    render(<RouterProvider router={router} />);

    expect(
      await screen.findByRole('heading', { name: 'Da gestire' }),
    ).toBeVisible();
    expect(
      screen
        .getByRole('heading', { level: 1, name: 'Prenotazioni' })
        .closest('article'),
    ).toHaveClass(pageTemplateStyles.authenticated);
    const pendingLink = screen.getByRole('link', { name: /In attesa/ });
    expect(pendingLink).toHaveAttribute('href', '/app/professional/bookings/1');
    expect(pendingLink.closest('li')).not.toBeNull();
    expect(screen.getByRole('heading', { name: 'Confermate' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Storico' })).toBeVisible();
    expect(screen.getByText('Rifiutata')).toBeVisible();
  });

  it('applica requiredness Client CONFIRMED e usa la response server autorevole', async () => {
    vi.spyOn(bookingApi, 'getBookingDetail').mockResolvedValue(
      detail({ status: 'CONFIRMED', confirmedAt: '2026-08-11T08:00:00Z' }),
    );
    const cancelSpy = vi.spyOn(bookingApi, 'cancelBooking').mockResolvedValue(
      detail({
        status: 'CANCELLED',
        confirmedAt: '2026-08-11T08:00:00Z',
        cancelledAt: '2026-08-12T08:00:00Z',
        cancellationReason: 'Imprevisto',
        cancelledBy: 'CLIENT',
      }),
    );
    const router = createMemoryRouter(
      [
        {
          path: '/app/client/bookings/:bookingRequestId',
          element: <ClientBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/client/bookings/41'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole('button', { name: 'Annulla appuntamento' }),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('obbligatoria');
    expect(cancelSpy).not.toHaveBeenCalled();
    await user.type(screen.getByRole('textbox'), ' Imprevisto ');
    await user.click(
      screen.getByRole('button', { name: 'Annulla appuntamento' }),
    );
    expect(
      await screen.findByRole('heading', { name: 'Annullata da te' }),
    ).toBeVisible();
    expect(screen.getByText('Imprevisto')).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Torna alle prenotazioni' }),
    ).toHaveAttribute('href', '/app/client/bookings');
    expect(
      screen
        .getByRole('heading', {
          level: 1,
          name: 'Dettaglio prenotazione',
        })
        .closest('article'),
    ).toHaveClass(pageTemplateStyles.authenticated);
  });

  it('riconcilia un 409 PT con GET detail senza ripetere la mutation', async () => {
    const getSpy = vi
      .spyOn(bookingApi, 'getBookingDetail')
      .mockResolvedValueOnce(detail())
      .mockResolvedValueOnce(
        detail({ status: 'CONFIRMED', confirmedAt: '2026-08-11T08:00:00Z' }),
      );
    const confirmSpy = vi
      .spyOn(bookingApi, 'confirmBooking')
      .mockRejectedValue(httpError(409, 'BOOKING_REQUEST_INVALID_TRANSITION'));
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Conferma' }));

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('Confermata')).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Torna alle prenotazioni' }),
    ).toHaveAttribute('href', '/app/professional/bookings');
  });

  it('mantiene un lock reale in StrictMode e disabilita tutte le azioni durante la mutation', async () => {
    vi.spyOn(bookingApi, 'getBookingDetail').mockResolvedValue(detail());
    const pending = deferred<BookingDetail>();
    const confirmSpy = vi
      .spyOn(bookingApi, 'confirmBooking')
      .mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );
    const user = userEvent.setup();

    const confirm = await screen.findByRole('button', { name: 'Conferma' });
    await user.click(confirm);
    await user.click(confirm);

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Conferma…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Rifiuta' })).toBeDisabled();
    expect(screen.getByRole('textbox')).toBeDisabled();

    await act(async () => {
      pending.resolve(
        detail({ status: 'CONFIRMED', confirmedAt: '2026-08-20T10:00:00Z' }),
      );
      await pending.promise;
    });
    expect(await screen.findByText('Confermata')).toBeVisible();
  });

  it('non permette alla mutation Booking A di sostituire Booking B', async () => {
    const bookingB = detail({
      id: 42,
      client: { ...detail().client, id: 3, displayName: 'Cliente B' },
    });
    vi.spyOn(bookingApi, 'getBookingDetail').mockImplementation((bookingId) =>
      Promise.resolve(bookingId === 41 ? detail() : bookingB),
    );
    const pendingA = deferred<BookingDetail>();
    const confirmSpy = vi
      .spyOn(bookingApi, 'confirmBooking')
      .mockImplementation((bookingId) =>
        bookingId === 41
          ? pendingA.promise
          : Promise.resolve(
              detail({
                ...bookingB,
                status: 'CONFIRMED',
                confirmedAt: '2026-08-20T10:00:00Z',
              }),
            ),
      );
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Conferma' }));
    const signalA = confirmSpy.mock.calls[0]?.[1]?.signal;
    await act(async () => {
      await router.navigate('/app/professional/bookings/42');
    });
    expect(await screen.findByText('Cliente B')).toBeVisible();
    expect(signalA?.aborted).toBe(true);

    await act(async () => {
      pendingA.resolve(
        detail({ status: 'CONFIRMED', confirmedAt: '2026-08-20T10:00:00Z' }),
      );
      await pendingA.promise;
    });

    expect(screen.getByText('Cliente B')).toBeVisible();
    expect(screen.getByText('In attesa')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Conferma' }));
    expect(confirmSpy.mock.calls.map(([bookingId]) => bookingId)).toEqual([
      41, 42,
    ]);
    expect(await screen.findByText('Confermata')).toBeVisible();
  });

  it('aborta e ignora la mutation detail che risolve dopo unmount', async () => {
    vi.spyOn(bookingApi, 'getBookingDetail').mockResolvedValue(detail());
    const pending = deferred<BookingDetail>();
    const confirmSpy = vi
      .spyOn(bookingApi, 'confirmBooking')
      .mockReturnValue(pending.promise);
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    const view = render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Conferma' }));
    const signal = confirmSpy.mock.calls[0]?.[1]?.signal;
    view.unmount();
    expect(signal?.aborted).toBe(true);

    await act(async () => {
      pending.resolve(
        detail({ status: 'CONFIRMED', confirmedAt: '2026-08-20T10:00:00Z' }),
      );
      await pending.promise;
    });
    expect(confirmSpy).toHaveBeenCalledTimes(1);
  });

  it('rende neutral unavailable quando la reconciliation 409 riceve GET 404', async () => {
    vi.spyOn(bookingApi, 'getBookingDetail')
      .mockResolvedValueOnce(detail())
      .mockRejectedValueOnce(httpError(404, 'BOOKING_REQUEST_NOT_FOUND'));
    vi.spyOn(bookingApi, 'confirmBooking').mockRejectedValue(
      httpError(409, 'BOOKING_REQUEST_INVALID_TRANSITION'),
    );
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Conferma' }));

    expect(
      await screen.findByText(/Prenotazione non disponibile/),
    ).toBeVisible();
  });

  it('non lascia che la reconciliation 409 di A sovrascriva la route B', async () => {
    const reconciliationA = deferred<BookingDetail>();
    let bookingAReads = 0;
    const bookingB = detail({
      id: 42,
      client: { ...detail().client, id: 3, displayName: 'Cliente B' },
    });
    const getSpy = vi
      .spyOn(bookingApi, 'getBookingDetail')
      .mockImplementation((bookingId) => {
        if (bookingId === 42) return Promise.resolve(bookingB);
        bookingAReads += 1;
        return bookingAReads === 1
          ? Promise.resolve(detail())
          : reconciliationA.promise;
      });
    vi.spyOn(bookingApi, 'confirmBooking').mockRejectedValue(
      httpError(409, 'BOOKING_REQUEST_INVALID_TRANSITION'),
    );
    const router = createMemoryRouter(
      [
        {
          path: '/app/professional/bookings/:bookingRequestId',
          element: <ProfessionalBookingDetailPage />,
        },
      ],
      { initialEntries: ['/app/professional/bookings/41'] },
    );
    render(<RouterProvider router={router} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Conferma' }));
    await waitFor(() => expect(bookingAReads).toBe(2));
    await act(async () => {
      await router.navigate('/app/professional/bookings/42');
    });
    expect(await screen.findByText('Cliente B')).toBeVisible();

    await act(async () => {
      reconciliationA.reject(httpError(404, 'BOOKING_REQUEST_NOT_FOUND'));
      await reconciliationA.promise.catch(() => undefined);
    });

    expect(screen.getByText('Cliente B')).toBeVisible();
    expect(
      screen.queryByText(/Prenotazione non disponibile/),
    ).not.toBeInTheDocument();
    expect(getSpy.mock.calls.map(([bookingId]) => bookingId)).toEqual([
      41, 41, 42,
    ]);
  });
});
