import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  createWeeklyAvailabilityRule,
  deactivateWeeklyAvailabilityRule,
  listMyAvailabilitySlots,
  listMyWeeklyAvailabilityRules,
  listProfessionalAvailability,
  previewWeeklyAvailabilityRuleImpact,
  setAvailabilitySlotBlocked,
  updateWeeklyAvailabilityRule,
} from './availabilityApi';
import type {
  AvailabilitySlot,
  CreateWeeklyAvailabilityRuleInput,
  WeeklyAvailabilityRule,
} from './availabilityTypes';
import { advanceEpoch } from './authEpoch';
import * as csrfMutation from './csrfMutation';
import { UnexpectedResponseError } from './types';

const RULE: WeeklyAvailabilityRule = {
  id: 7,
  dayOfWeek: 'MONDAY',
  startTime: '09:00',
  endTime: '13:00',
  allowedDurations: [45, 60, 90],
  locationLabel: 'Palestra X',
  capacityPerSlot: 3,
  active: true,
  validFrom: '2026-08-12',
  createdAt: '2026-08-11T08:00:00Z',
  updatedAt: '2026-08-11T08:00:00Z',
};

const SLOT: AvailabilitySlot = {
  id: 31,
  startDateTime: '2026-08-20T09:00:00+02:00',
  endDateTime: '2026-08-20T10:00:00+02:00',
  locationLabel: 'Palestra X',
  capacity: 3,
  maximumOccupancy: 1,
  minimumRemainingCapacity: 2,
  allowedDurations: [60],
  startIntervalMinutes: 15,
  blocked: false,
  active: true,
  bookable: true,
};

const CREATE_INPUT: CreateWeeklyAvailabilityRuleInput = {
  dayOfWeek: 'MONDAY',
  startTime: '09:00',
  endTime: '13:00',
  allowedDurations: [45, 60, 90],
  locationLabel: 'Palestra X',
  capacityPerSlot: 3,
  validFrom: '2026-08-12',
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('availabilityApi', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    advanceEpoch();
  });

  it('legge regole e slot con GET, credentials e AbortSignal', async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([RULE]))
      .mockResolvedValueOnce(jsonResponse([SLOT]));
    globalThis.fetch = fetchMock;

    await expect(
      listMyWeeklyAvailabilityRules({ signal: controller.signal }),
    ).resolves.toEqual([RULE]);
    await expect(
      listMyAvailabilitySlots({ signal: controller.signal }),
    ).resolves.toEqual([SLOT]);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/availability/weekly-rules/my',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        signal: controller.signal,
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/availability/my',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        signal: controller.signal,
      }),
    );
  });

  it('legge le combinazioni Client calcolate dal backend', async () => {
    const controller = new AbortController();
    const response = {
      occurrenceId: 31,
      windowStart: '2026-08-20T09:00:00+02:00',
      windowEnd: '2026-08-20T11:00:00+02:00',
      allowedDurations: [60],
      startIntervalMinutes: 15,
      location: 'Palestra X',
      capacity: 1,
      bookableOptions: [
        {
          startDateTime: '2026-08-20T10:00:00+02:00',
          allowedDurations: [60],
        },
      ],
    };
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse([response]));
    globalThis.fetch = fetchMock;

    await expect(
      listProfessionalAvailability(7, { signal: controller.signal }),
    ).resolves.toEqual([response]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/professionals/7/availability',
      expect.objectContaining({ signal: controller.signal }),
    );
  });

  it('crea una regola con JSON e protocollo CSRF', async () => {
    const mutationSpy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValueOnce(RULE);

    await expect(createWeeklyAvailabilityRule(CREATE_INPUT)).resolves.toEqual(
      RULE,
    );

    expect(mutationSpy).toHaveBeenCalledWith(
      '/availability/weekly-rules',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(CREATE_INPUT),
        invalidateOn401: true,
        invalidateCsrfOnCommit: false,
      }),
    );
  });

  it('aggiorna immediatamente una regola includendo changeReason', async () => {
    const mutationSpy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValueOnce(RULE);
    const updateWithoutValidFrom = {
      dayOfWeek: CREATE_INPUT.dayOfWeek,
      startTime: CREATE_INPUT.startTime,
      endTime: CREATE_INPUT.endTime,
      allowedDurations: CREATE_INPUT.allowedDurations,
      locationLabel: CREATE_INPUT.locationLabel,
      capacityPerSlot: CREATE_INPUT.capacityPerSlot,
      changeReason: 'Cambio sede',
    };

    await updateWeeklyAvailabilityRule(7, updateWithoutValidFrom);

    expect(mutationSpy).toHaveBeenCalledWith(
      '/availability/weekly-rules/7',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(updateWithoutValidFrom),
      }),
    );
  });

  it("anticipa l'impatto immediato con signal", async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse({
        impactDetected: true,
        impactedBookingCount: 2,
        changeReasonRequired: true,
      }),
    );
    globalThis.fetch = fetchMock;

    await expect(
      previewWeeklyAvailabilityRuleImpact(7, {
        signal: controller.signal,
      }),
    ).resolves.toEqual({
      impactDetected: true,
      impactedBookingCount: 2,
      changeReasonRequired: true,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/availability/weekly-rules/7/impact',
      expect.objectContaining({ signal: controller.signal }),
    );
  });

  it('disattiva la regola con effetto immediato e motivazione', async () => {
    const mutationSpy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValueOnce(undefined);

    await deactivateWeeklyAvailabilityRule(7, 'Ferie');

    expect(mutationSpy).toHaveBeenCalledWith(
      '/availability/weekly-rules/7/deactivate',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({
          changeReason: 'Ferie',
        }),
      }),
    );
  });

  it('blocca e sblocca una singola occorrenza', async () => {
    const mutationSpy = vi
      .spyOn(csrfMutation, 'performCsrfMutation')
      .mockResolvedValue(SLOT);

    await setAvailabilitySlotBlocked(31, true, 'Chiusura palestra');
    await setAvailabilitySlotBlocked(31, false);

    expect(mutationSpy).toHaveBeenNthCalledWith(
      1,
      '/availability/31/block',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ changeReason: 'Chiusura palestra' }),
      }),
    );
    expect(mutationSpy).toHaveBeenNthCalledWith(
      2,
      '/availability/31/unblock',
      expect.objectContaining({ method: 'PATCH' }),
    );
  });

  it('fallisce closed su response mutation non conforme', async () => {
    vi.spyOn(csrfMutation, 'performCsrfMutation').mockResolvedValueOnce({
      ...RULE,
      capacityPerSlot: 0,
    });

    await expect(
      createWeeklyAvailabilityRule(CREATE_INPUT),
    ).rejects.toBeInstanceOf(UnexpectedResponseError);
  });

  it('rifiuta identificativi non positivi prima della rete', async () => {
    const mutationSpy = vi.spyOn(csrfMutation, 'performCsrfMutation');

    await expect(setAvailabilitySlotBlocked(0, true)).rejects.toBeInstanceOf(
      RangeError,
    );
    expect(() => previewWeeklyAvailabilityRuleImpact(-1)).toThrow(RangeError);
    expect(mutationSpy).not.toHaveBeenCalled();
  });
});
