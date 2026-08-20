import {
  requireArray,
  requireBusinessOffsetDateTime,
  requireEnum,
  requireIsoInstant,
  requireJsonObject,
  requireNullableIsoInstant,
  requireNullableString,
  requirePositiveSafeInteger,
  requireString,
  type JsonObject,
} from './apiResponseDecoders';

export const BOOKING_STATUS_VALUES = [
  'PENDING',
  'CONFIRMED',
  'REJECTED',
  'CANCELLED',
] as const;
export const BOOKING_CANCELLATION_ACTOR_VALUES = [
  'CLIENT',
  'PROFESSIONAL',
] as const;
export const BOOKING_SPECIALIZATION_VALUES = [
  'PERSONAL_TRAINER',
  'NUTRITIONIST',
] as const;

export type BookingStatus = (typeof BOOKING_STATUS_VALUES)[number];
export type BookingCancellationActor =
  (typeof BOOKING_CANCELLATION_ACTOR_VALUES)[number];
export type BookingSpecialization =
  (typeof BOOKING_SPECIALIZATION_VALUES)[number];

export interface BookingParticipant {
  readonly id: number;
  readonly displayName: string;
  readonly profileImageUrl: string | null;
  readonly specialization: BookingSpecialization | null;
}

export interface BookingItem {
  readonly id: number;
  readonly availabilitySlotId: number;
  readonly scheduledStart: string;
  readonly scheduledEnd: string;
  readonly durationMinutes: number;
  readonly locationLabel: string | null;
}

export interface BookingSummary {
  readonly id: number;
  readonly status: BookingStatus;
  readonly counterparty: BookingParticipant;
  readonly scheduledStart: string;
  readonly scheduledEnd: string;
  readonly durationMinutes: number;
  readonly note: string | null;
  readonly createdAt: string;
}

export interface BookingDetail {
  readonly id: number;
  readonly status: BookingStatus;
  readonly client: BookingParticipant;
  readonly professional: BookingParticipant;
  readonly scheduledStart: string;
  readonly scheduledEnd: string;
  readonly durationMinutes: number;
  readonly note: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly confirmedAt: string | null;
  readonly rejectedAt: string | null;
  readonly cancelledAt: string | null;
  readonly rejectionReason: string | null;
  readonly cancellationReason: string | null;
  readonly cancelledBy: BookingCancellationActor | null;
  readonly items: readonly BookingItem[];
}

export interface CreateBookingInput {
  readonly availabilitySlotId: number;
  readonly startDateTime: string;
  readonly durationMinutes: number;
  readonly note: string | null;
}

function decodeParticipant(value: unknown): BookingParticipant {
  const record = requireJsonObject(value, 'Booking participant');
  const specialization =
    record.specialization === undefined || record.specialization === null
      ? null
      : requireEnum(record, 'specialization', BOOKING_SPECIALIZATION_VALUES);
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    displayName: requireString(record, 'displayName'),
    profileImageUrl: requireNullableString(record, 'profileImageUrl'),
    specialization,
  };
}

function decodeSchedule(
  record: JsonObject,
  requireDurationMatchesSpan: boolean,
): {
  scheduledStart: string;
  scheduledEnd: string;
  durationMinutes: number;
} {
  const scheduledStart = requireBusinessOffsetDateTime(
    record,
    'scheduledStart',
  );
  const scheduledEnd = requireBusinessOffsetDateTime(record, 'scheduledEnd');
  const durationMinutes = requirePositiveSafeInteger(record, 'durationMinutes');
  if (
    Date.parse(scheduledStart) >= Date.parse(scheduledEnd) ||
    (requireDurationMatchesSpan &&
      (Date.parse(scheduledEnd) - Date.parse(scheduledStart)) / 60_000 !==
        durationMinutes)
  ) {
    throw new Error('Booking schedule is inconsistent');
  }
  return { scheduledStart, scheduledEnd, durationMinutes };
}

function decodeItem(value: unknown): BookingItem {
  const record = requireJsonObject(value, 'Booking item');
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    availabilitySlotId: requirePositiveSafeInteger(
      record,
      'availabilitySlotId',
    ),
    ...decodeSchedule(record, true),
    locationLabel: requireNullableString(record, 'locationLabel'),
  };
}

export function decodeBookingSummary(value: unknown): BookingSummary {
  const record = requireJsonObject(value, 'Booking summary');
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    status: requireEnum(record, 'status', BOOKING_STATUS_VALUES),
    counterparty: decodeParticipant(record.counterparty),
    ...decodeSchedule(record, false),
    note: requireNullableString(record, 'note'),
    createdAt: requireIsoInstant(record, 'createdAt'),
  };
}

export function decodeBookingSummaryList(value: unknown): BookingSummary[] {
  return requireArray(value, 'Booking summary list').map(decodeBookingSummary);
}

export function decodeBookingDetail(value: unknown): BookingDetail {
  const record = requireJsonObject(value, 'Booking detail');
  const items = requireArray(record.items, 'Booking items').map(decodeItem);
  if (items.length === 0) throw new Error('Booking items must not be empty');
  const schedule = decodeSchedule(record, false);
  if (
    Math.min(...items.map((item) => Date.parse(item.scheduledStart))) !==
      Date.parse(schedule.scheduledStart) ||
    Math.max(...items.map((item) => Date.parse(item.scheduledEnd))) !==
      Date.parse(schedule.scheduledEnd) ||
    items.reduce((total, item) => total + item.durationMinutes, 0) !==
      schedule.durationMinutes
  ) {
    throw new Error('Booking detail aggregate schedule is inconsistent');
  }
  const cancelledBy =
    record.cancelledBy === null
      ? null
      : requireEnum(record, 'cancelledBy', BOOKING_CANCELLATION_ACTOR_VALUES);
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    status: requireEnum(record, 'status', BOOKING_STATUS_VALUES),
    client: decodeParticipant(record.client),
    professional: decodeParticipant(record.professional),
    ...schedule,
    note: requireNullableString(record, 'note'),
    createdAt: requireIsoInstant(record, 'createdAt'),
    updatedAt: requireIsoInstant(record, 'updatedAt'),
    confirmedAt: requireNullableIsoInstant(record, 'confirmedAt'),
    rejectedAt: requireNullableIsoInstant(record, 'rejectedAt'),
    cancelledAt: requireNullableIsoInstant(record, 'cancelledAt'),
    rejectionReason: requireNullableString(record, 'rejectionReason'),
    cancellationReason: requireNullableString(record, 'cancellationReason'),
    cancelledBy,
    items,
  };
}
