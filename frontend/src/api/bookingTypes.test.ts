import { describe, expect, it } from 'vitest';

import { decodeBookingDetail, decodeBookingSummary } from './bookingTypes';

const DETAIL = {
  id: 41,
  status: 'CONFIRMED',
  client: {
    id: 2,
    displayName: 'Luigi Bianchi',
    profileImageUrl: null,
  },
  professional: {
    id: 7,
    displayName: 'Mario Rossi',
    profileImageUrl: null,
    specialization: 'PERSONAL_TRAINER',
  },
  scheduledStart: '2026-08-20T10:00:00+02:00',
  scheduledEnd: '2026-08-20T11:00:00+02:00',
  durationMinutes: 60,
  note: null,
  createdAt: '2026-08-10T08:00:00Z',
  updatedAt: '2026-08-11T08:00:00Z',
  confirmedAt: '2026-08-11T08:00:00Z',
  rejectedAt: null,
  cancelledAt: null,
  rejectionReason: null,
  cancellationReason: null,
  cancelledBy: null,
  items: [
    {
      id: 51,
      availabilitySlotId: 31,
      scheduledStart: '2026-08-20T10:00:00+02:00',
      scheduledEnd: '2026-08-20T11:00:00+02:00',
      durationMinutes: 60,
      locationLabel: 'Studio',
    },
  ],
} as const;

describe('bookingTypes', () => {
  it.each(['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'] as const)(
    'decodifica lo status %s con metadata legacy nullable',
    (status) => {
      expect(decodeBookingDetail({ ...DETAIL, status })).toMatchObject({
        status,
        rejectionReason: null,
        cancellationReason: null,
        cancelledBy: null,
      });
    },
  );

  it.each(['CLIENT', 'PROFESSIONAL'] as const)(
    'decodifica actor %s',
    (cancelledBy) => {
      expect(decodeBookingDetail({ ...DETAIL, cancelledBy }).cancelledBy).toBe(
        cancelledBy,
      );
    },
  );

  it('decodifica un detail single-item coerente', () => {
    expect(decodeBookingDetail(DETAIL)).toMatchObject({
      scheduledStart: DETAIL.scheduledStart,
      scheduledEnd: DETAIL.scheduledEnd,
      durationMinutes: 60,
    });
  });

  it('decodifica un detail multi-item contiguo', () => {
    expect(
      decodeBookingDetail({
        ...DETAIL,
        scheduledEnd: '2026-08-20T12:00:00+02:00',
        durationMinutes: 120,
        items: [
          DETAIL.items[0],
          {
            ...DETAIL.items[0],
            id: 52,
            availabilitySlotId: 32,
            scheduledStart: '2026-08-20T11:00:00+02:00',
            scheduledEnd: '2026-08-20T12:00:00+02:00',
          },
        ],
      }),
    ).toMatchObject({ durationMinutes: 120 });
  });

  it('decodifica un detail multi-item non contiguo usando MIN, MAX e SUM', () => {
    expect(
      decodeBookingDetail({
        ...DETAIL,
        scheduledEnd: '2026-08-20T13:00:00+02:00',
        durationMinutes: 120,
        items: [
          DETAIL.items[0],
          {
            ...DETAIL.items[0],
            id: 52,
            availabilitySlotId: 32,
            scheduledStart: '2026-08-20T12:00:00+02:00',
            scheduledEnd: '2026-08-20T13:00:00+02:00',
          },
        ],
      }),
    ).toMatchObject({
      scheduledStart: '2026-08-20T10:00:00+02:00',
      scheduledEnd: '2026-08-20T13:00:00+02:00',
      durationMinutes: 120,
    });
  });

  it('rifiuta un aggregato detail incoerente con MIN, MAX o SUM degli item', () => {
    const secondItem = {
      ...DETAIL.items[0],
      id: 52,
      availabilitySlotId: 32,
      scheduledStart: '2026-08-20T12:00:00+02:00',
      scheduledEnd: '2026-08-20T13:00:00+02:00',
    };
    const multiItem = {
      ...DETAIL,
      scheduledEnd: '2026-08-20T13:00:00+02:00',
      durationMinutes: 120,
      items: [DETAIL.items[0], secondItem],
    };

    expect(() =>
      decodeBookingDetail({
        ...multiItem,
        scheduledStart: '2026-08-20T09:00:00+02:00',
      }),
    ).toThrow();
    expect(() =>
      decodeBookingDetail({
        ...multiItem,
        scheduledEnd: '2026-08-20T14:00:00+02:00',
      }),
    ).toThrow();
    expect(() =>
      decodeBookingDetail({ ...multiItem, durationMinutes: 121 }),
    ).toThrow();
  });

  it('rifiuta enum, timestamp e schedule malformati', () => {
    expect(() =>
      decodeBookingDetail({ ...DETAIL, status: 'EXPIRED' }),
    ).toThrow();
    expect(() =>
      decodeBookingDetail({ ...DETAIL, cancelledBy: 'ADMIN' }),
    ).toThrow();
    expect(() =>
      decodeBookingDetail({ ...DETAIL, createdAt: '2026-08-10' }),
    ).toThrow();
    expect(() =>
      decodeBookingDetail({ ...DETAIL, scheduledEnd: DETAIL.scheduledStart }),
    ).toThrow();
  });

  it('decodifica summary senza imporre metadata del detail', () => {
    expect(
      decodeBookingSummary({
        id: 41,
        status: 'PENDING',
        counterparty: DETAIL.professional,
        scheduledStart: DETAIL.scheduledStart,
        scheduledEnd: DETAIL.scheduledEnd,
        durationMinutes: 60,
        note: null,
        createdAt: DETAIL.createdAt,
      }),
    ).toMatchObject({ id: 41, status: 'PENDING' });

    expect(
      decodeBookingSummary({
        id: 42,
        status: 'CONFIRMED',
        counterparty: DETAIL.professional,
        scheduledStart: '2026-08-20T10:00:00+02:00',
        scheduledEnd: '2026-08-20T13:00:00+02:00',
        durationMinutes: 120,
        note: null,
        createdAt: DETAIL.createdAt,
      }),
    ).toMatchObject({ id: 42, durationMinutes: 120 });
  });
});
