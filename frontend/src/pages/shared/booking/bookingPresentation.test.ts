import { describe, expect, it, vi } from 'vitest';

import {
  bookingTemporalState,
  cancellationActorLabel,
  groupClientBookings,
  groupProfessionalBookings,
} from './bookingPresentation';
import type { BookingSummary } from '../../../api/bookingTypes';

const START = '2026-08-20T10:00:00+02:00';
const END = '2026-08-20T11:00:00+02:00';

describe('booking temporal presentation', () => {
  it.each([
    ['2026-08-20T07:59:59Z', 'UPCOMING'],
    ['2026-08-20T08:00:00Z', 'IN_PROGRESS'],
    ['2026-08-20T08:30:00Z', 'IN_PROGRESS'],
    ['2026-08-20T09:00:00Z', 'PAST'],
    ['2026-08-20T09:00:01Z', 'PAST'],
  ] as const)('deriva i confini %s come %s', (now, expected) => {
    expect(bookingTemporalState(START, END, new Date(now))).toBe(expected);
  });

  it('produce copy actor differenziata e fallback legacy', () => {
    expect(cancellationActorLabel('CLIENT', 'CLIENT')).toBe('Annullata da te');
    expect(cancellationActorLabel('PROFESSIONAL', 'CLIENT')).toBe(
      'Annullata dal Personal Trainer',
    );
    expect(cancellationActorLabel('CLIENT', 'PROFESSIONAL')).toBe(
      'Annullata dal cliente',
    );
    expect(cancellationActorLabel(null, 'PROFESSIONAL')).toBe('Annullata');
  });

  it('raggruppa senza riordinare le response server', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T07:00:00Z'));
    const booking = (id: number, status: BookingSummary['status'], end = END) =>
      ({
        id,
        status,
        counterparty: {
          id: 7,
          displayName: 'Mario',
          profileImageUrl: null,
          specialization: 'PERSONAL_TRAINER',
        },
        scheduledStart: START,
        scheduledEnd: end,
        durationMinutes: 60,
        note: null,
        createdAt: '2026-08-10T08:00:00Z',
      }) satisfies BookingSummary;
    const values = [
      booking(1, 'PENDING'),
      booking(2, 'CONFIRMED'),
      booking(3, 'REJECTED'),
      booking(4, 'CANCELLED'),
    ];
    expect(groupClientBookings(values).upcoming.map(({ id }) => id)).toEqual([
      1, 2,
    ]);
    const professional = groupProfessionalBookings(values);
    expect(professional.pending.map(({ id }) => id)).toEqual([1]);
    expect(professional.confirmed.map(({ id }) => id)).toEqual([2]);
    expect(professional.history.map(({ id }) => id)).toEqual([3, 4]);
    vi.useRealTimers();
  });
});
