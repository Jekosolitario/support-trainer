import { describe, expect, it } from 'vitest';

import {
  decodeAvailabilitySlot,
  decodeClientAvailabilityWindow,
  decodeWeeklyAvailabilityRule,
  decodeWeeklyAvailabilityRuleImpact,
} from './availabilityTypes';

const validRule = {
  id: 12,
  dayOfWeek: 'MONDAY',
  startTime: '09:00',
  endTime: '13:00',
  allowedDurations: [45, 60, 120],
  locationLabel: 'Palestra X',
  capacityPerSlot: 3,
  active: true,
  validFrom: '2026-08-12',
  createdAt: '2026-08-11T08:00:00Z',
  updatedAt: '2026-08-11T08:00:00Z',
};

describe('availability response decoding', () => {
  it('accetta una regola settimanale completa', () => {
    expect(decodeWeeklyAvailabilityRule(validRule)).toEqual(validRule);
  });

  it('rifiuta orari e capacità non validi', () => {
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        startTime: '9:00',
      }),
    ).toThrow(/startTime/);
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        capacityPerSlot: 0,
      }),
    ).toThrow(/capacityPerSlot/);
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        startTime: '13:00',
        endTime: '12:00',
      }),
    ).toThrow(/time range/);
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        startTime: '09:00',
        endTime: '09:30',
        allowedDurations: [60],
      }),
    ).toThrow(/allowedDurations/);
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        allowedDurations: [15, 17],
      }),
    ).toThrow(/allowedDurations/);
    expect(() =>
      decodeWeeklyAvailabilityRule({
        ...validRule,
        allowedDurations: [60, 60],
      }),
    ).toThrow(/allowedDurations/);
  });

  it('decodifica un impatto anche quando non ci sono booking', () => {
    expect(
      decodeWeeklyAvailabilityRuleImpact({
        impactDetected: false,
        impactedBookingCount: 0,
        changeReasonRequired: false,
      }),
    ).toEqual({
      impactDetected: false,
      impactedBookingCount: 0,
      changeReasonRequired: false,
    });
  });

  it('decodifica capacità e blocco di una singola occorrenza', () => {
    expect(
      decodeAvailabilitySlot({
        id: 31,
        startDateTime: '2026-08-12T09:00:00+02:00',
        endDateTime: '2026-08-12T10:00:00+02:00',
        locationLabel: 'Palestra X',
        capacity: 3,
        maximumOccupancy: 1,
        minimumRemainingCapacity: 2,
        allowedDurations: [45, 60],
        startIntervalMinutes: 15,
        blocked: false,
        active: true,
        bookable: true,
      }),
    ).toEqual(
      expect.objectContaining({
        id: 31,
        capacity: 3,
        minimumRemainingCapacity: 2,
        blocked: false,
      }),
    );
  });

  it('rifiuta impatto e capacità residua incoerenti', () => {
    expect(() =>
      decodeWeeklyAvailabilityRuleImpact({
        impactDetected: false,
        impactedBookingCount: 1,
        changeReasonRequired: false,
      }),
    ).toThrow(/inconsistent/);

    expect(() =>
      decodeAvailabilitySlot({
        id: 31,
        startDateTime: '2026-08-12T09:00:00+02:00',
        endDateTime: '2026-08-12T10:00:00+02:00',
        locationLabel: null,
        capacity: 3,
        maximumOccupancy: 2,
        minimumRemainingCapacity: 2,
        allowedDurations: [60],
        startIntervalMinutes: 15,
        blocked: false,
        active: true,
        bookable: true,
      }),
    ).toThrow(/capacity/);
  });

  it('rifiuta un offset valido ma non coerente con Europe/Rome', () => {
    expect(() =>
      decodeAvailabilitySlot({
        id: 31,
        startDateTime: '2026-08-12T09:00:00+00:00',
        endDateTime: '2026-08-12T10:00:00+00:00',
        locationLabel: null,
        capacity: 3,
        maximumOccupancy: 0,
        minimumRemainingCapacity: 3,
        allowedDurations: [60],
        startIntervalMinutes: 15,
        blocked: false,
        active: true,
        bookable: true,
      }),
    ).toThrow(/Europe\/Rome/);
  });

  it('accetta una finestra manuale legacy non prenotabile senza durate supportate', () => {
    const legacyWindow = {
      id: 32,
      startDateTime: '2026-08-13T09:00:00+02:00',
      endDateTime: '2026-08-13T13:00:00+02:00',
      locationLabel: null,
      capacity: 1,
      maximumOccupancy: 0,
      minimumRemainingCapacity: 1,
      allowedDurations: [],
      startIntervalMinutes: 15,
      blocked: false,
      active: true,
      bookable: false,
    };

    expect(decodeAvailabilitySlot(legacyWindow).allowedDurations).toEqual([]);
    expect(() =>
      decodeAvailabilitySlot({ ...legacyWindow, bookable: true }),
    ).toThrow(/bookable state/);
  });

  it('decodifica soltanto combinazioni Client prenotabili e minimizzate', () => {
    const response = {
      occurrenceId: 44,
      windowStart: '2026-08-20T09:00:00+02:00',
      windowEnd: '2026-08-20T11:00:00+02:00',
      allowedDurations: [45, 60],
      startIntervalMinutes: 15,
      location: 'Studio',
      capacity: 2,
      bookableOptions: [
        {
          startDateTime: '2026-08-20T09:15:00+02:00',
          allowedDurations: [45],
        },
        {
          startDateTime: '2026-08-20T10:00:00+02:00',
          allowedDurations: [45, 60],
        },
      ],
    };

    expect(decodeClientAvailabilityWindow(response)).toEqual(response);
    expect(Object.keys(decodeClientAvailabilityWindow(response))).toEqual([
      'occurrenceId',
      'windowStart',
      'windowEnd',
      'allowedDurations',
      'startIntervalMinutes',
      'location',
      'capacity',
      'bookableOptions',
    ]);
  });

  it('rifiuta opzioni Client vuote, fuori finestra o con durate non consentite', () => {
    const base = {
      occurrenceId: 44,
      windowStart: '2026-08-20T09:00:00+02:00',
      windowEnd: '2026-08-20T11:00:00+02:00',
      allowedDurations: [60],
      startIntervalMinutes: 15,
      location: null,
      capacity: 1,
      bookableOptions: [
        {
          startDateTime: '2026-08-20T10:00:00+02:00',
          allowedDurations: [60],
        },
      ],
    };

    expect(() =>
      decodeClientAvailabilityWindow({ ...base, bookableOptions: [] }),
    ).toThrow(/bookableOptions/);
    expect(() =>
      decodeClientAvailabilityWindow({
        ...base,
        bookableOptions: [
          {
            startDateTime: '2026-08-20T11:00:00+02:00',
            allowedDurations: [60],
          },
        ],
      }),
    ).toThrow(/outside/);
    expect(() =>
      decodeClientAvailabilityWindow({
        ...base,
        bookableOptions: [
          {
            startDateTime: '2026-08-20T09:00:00+02:00',
            allowedDurations: [45],
          },
        ],
      }),
    ).toThrow(/not allowed/);
  });

  it('rifiuta start Client non allineati per minuto, secondo o frazione', () => {
    const base = {
      occurrenceId: 44,
      windowStart: '2026-08-20T09:00:00+02:00',
      windowEnd: '2026-08-20T11:00:00+02:00',
      allowedDurations: [60],
      startIntervalMinutes: 15,
      location: null,
      capacity: 1,
      bookableOptions: [
        {
          startDateTime: '2026-08-20T09:15:00+02:00',
          allowedDurations: [60],
        },
      ],
    };

    for (const startDateTime of [
      '2026-08-20T09:10:00+02:00',
      '2026-08-20T09:15:30+02:00',
      '2026-08-20T09:15:00.001+02:00',
    ]) {
      expect(() =>
        decodeClientAvailabilityWindow({
          ...base,
          bookableOptions: [
            {
              startDateTime,
              allowedDurations: [60],
            },
          ],
        }),
      ).toThrow(/not aligned/);
    }
  });
});
