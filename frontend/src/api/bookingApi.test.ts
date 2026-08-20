import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  cancelBooking,
  confirmBooking,
  createBooking,
  getBookingDetail,
  listClientBookings,
  listProfessionalBookings,
  rejectBooking,
} from './bookingApi';
import { advanceEpoch } from './authEpoch';
import * as csrfMutation from './csrfMutation';
import { UnexpectedResponseError } from './types';

const DETAIL = {
  id: 41,
  status: 'PENDING',
  client: { id: 2, displayName: 'Luigi', profileImageUrl: null },
  professional: {
    id: 7,
    displayName: 'Mario',
    profileImageUrl: null,
    specialization: 'PERSONAL_TRAINER',
  },
  scheduledStart: '2026-08-20T10:00:00+02:00',
  scheduledEnd: '2026-08-20T11:00:00+02:00',
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
      scheduledStart: '2026-08-20T10:00:00+02:00',
      scheduledEnd: '2026-08-20T11:00:00+02:00',
      durationMinutes: 60,
      locationLabel: null,
    },
  ],
};

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('bookingApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    advanceEpoch();
  });

  it('usa endpoint GET, decoder e signal per liste e detail', async () => {
    const signal = new AbortController().signal;
    const summary = {
      id: 41,
      status: 'PENDING',
      counterparty: DETAIL.professional,
      scheduledStart: DETAIL.scheduledStart,
      scheduledEnd: DETAIL.scheduledEnd,
      durationMinutes: 60,
      note: null,
      createdAt: DETAIL.createdAt,
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([summary]))
      .mockResolvedValueOnce(jsonResponse([summary]))
      .mockResolvedValueOnce(jsonResponse(DETAIL));
    globalThis.fetch = fetchMock;

    await listClientBookings({ signal });
    await listProfessionalBookings({ signal });
    await getBookingDetail(41, { signal });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/bookings/client',
      expect.objectContaining({ method: 'GET', signal }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/bookings/professional',
      expect.objectContaining({ method: 'GET', signal }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/bookings/41',
      expect.objectContaining({ signal }),
    );
  });

  it('espone payload e metodi mutation tramite protocollo CSRF', async () => {
    const mutation = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValue(DETAIL);
    const createInput = {
      availabilitySlotId: 31,
      startDateTime: DETAIL.scheduledStart,
      durationMinutes: 60,
      note: null,
    };

    await createBooking(createInput);
    await confirmBooking(41);
    await rejectBooking(41, 'Agenda completa');
    await cancelBooking(41, null);
    await cancelBooking(41);

    expect(mutation).toHaveBeenNthCalledWith(
      1,
      '/bookings',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(createInput),
      }),
    );
    expect(mutation).toHaveBeenNthCalledWith(
      2,
      '/bookings/41/confirm',
      expect.objectContaining({ method: 'PATCH', invalidateOn401: true }),
    );
    expect(mutation).toHaveBeenNthCalledWith(
      3,
      '/bookings/41/reject',
      expect.objectContaining({
        body: JSON.stringify({ reason: 'Agenda completa' }),
      }),
    );
    expect(mutation).toHaveBeenNthCalledWith(
      4,
      '/bookings/41/cancel',
      expect.objectContaining({ body: JSON.stringify({ reason: null }) }),
    );
    expect(mutation.mock.calls[4]?.[1]).not.toHaveProperty('body');
  });

  it('fallisce closed su mutation malformata e valida gli id prima della rete', async () => {
    vi.spyOn(csrfMutation, 'performCsrfMutation').mockResolvedValue({
      ...DETAIL,
      status: 'COMPLETED',
    });
    await expect(confirmBooking(41)).rejects.toBeInstanceOf(
      UnexpectedResponseError,
    );
    expect(() => getBookingDetail(0)).toThrow(RangeError);
  });
});
