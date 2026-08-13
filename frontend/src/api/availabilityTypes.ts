import {
  requireArray,
  requireBoolean,
  requireEnum,
  requireIsoLocalDate,
  requireJsonObject,
  requireNullableString,
  requirePositiveSafeInteger,
  requireString,
  type JsonObject,
} from './apiResponseDecoders';

export const DAY_OF_WEEK_VALUES = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

export const START_INTERVAL_MINUTES = 15;
export const MIN_DURATION_MINUTES = 15;
export const MAX_DURATION_MINUTES = 180;
export const DURATION_OPTIONS = Array.from(
  {
    length:
      (MAX_DURATION_MINUTES - MIN_DURATION_MINUTES) / START_INTERVAL_MINUTES +
      1,
  },
  (_, index) => MIN_DURATION_MINUTES + index * START_INTERVAL_MINUTES,
);

export type DayOfWeek = (typeof DAY_OF_WEEK_VALUES)[number];

export interface WeeklyAvailabilityRule {
  readonly id: number;
  readonly dayOfWeek: DayOfWeek;
  readonly startTime: string;
  readonly endTime: string;
  readonly allowedDurations: readonly number[];
  readonly locationLabel: string | null;
  readonly capacityPerSlot: number;
  readonly active: boolean;
  readonly validFrom: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface WeeklyAvailabilityRuleImpact {
  readonly impactDetected: boolean;
  readonly impactedBookingCount: number;
  readonly changeReasonRequired: boolean;
}

export interface AvailabilitySlot {
  readonly id: number;
  readonly startDateTime: string;
  readonly endDateTime: string;
  readonly locationLabel: string | null;
  readonly capacity: number;
  readonly maximumOccupancy: number;
  readonly minimumRemainingCapacity: number;
  readonly allowedDurations: readonly number[];
  readonly startIntervalMinutes: number;
  readonly blocked: boolean;
  readonly active: boolean;
  readonly bookable: boolean;
}

export interface ClientBookableOption {
  readonly startDateTime: string;
  readonly allowedDurations: readonly number[];
}

export interface ClientAvailabilityWindow {
  readonly occurrenceId: number;
  readonly windowStart: string;
  readonly windowEnd: string;
  readonly allowedDurations: readonly number[];
  readonly startIntervalMinutes: number;
  readonly location: string | null;
  readonly capacity: number;
  readonly bookableOptions: readonly ClientBookableOption[];
}

export interface CreateWeeklyAvailabilityRuleInput {
  readonly dayOfWeek: DayOfWeek;
  readonly startTime: string;
  readonly endTime: string;
  readonly allowedDurations: readonly number[];
  readonly locationLabel: string | null;
  readonly capacityPerSlot: number;
  readonly validFrom: string;
}

export interface UpdateWeeklyAvailabilityRuleInput extends Omit<
  CreateWeeklyAvailabilityRuleInput,
  'validFrom'
> {
  readonly changeReason: string | null;
}

function requireIsoLocalTime(record: JsonObject, key: string): string {
  const value = requireString(record, key);
  if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) {
    throw new Error(`${key} must be an HH:mm local time`);
  }
  return value;
}

function localTimeMinutes(value: string): number {
  const [hours, minutes] = value.split(':').map(Number);
  return hours * 60 + minutes;
}

function requireNonNegativeSafeInteger(
  record: JsonObject,
  key: string,
): number {
  const value = record[key];
  if (!Number.isSafeInteger(value) || (value as number) < 0) {
    throw new Error(`${key} must be a non-negative safe integer`);
  }
  return value as number;
}

function requireBusinessOffsetDateTime(
  record: JsonObject,
  key: string,
): string {
  const value = requireString(record, key);
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?([+-]\d{2}:\d{2})$/.exec(
      value,
    );
  const instant = new Date(value);
  if (match === null || Number.isNaN(instant.getTime())) {
    throw new Error(`${key} must be an ISO offset date-time`);
  }
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Rome',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
    .formatToParts(instant)
    .reduce<Record<string, string>>((result, part) => {
      result[part.type] = part.value;
      return result;
    }, {});
  if (
    parts.year !== match[1] ||
    parts.month !== match[2] ||
    parts.day !== match[3] ||
    parts.hour !== match[4] ||
    parts.minute !== match[5] ||
    parts.second !== match[6]
  ) {
    throw new Error(`${key} must use the Europe/Rome business offset`);
  }
  return value;
}

function isAlignedBookableStart(value: string): boolean {
  const match = /T\d{2}:(\d{2}):(\d{2})(?:\.(\d+))?[+-]\d{2}:\d{2}$/.exec(
    value,
  );
  if (match === null) return false;

  const [, minute, second, fraction] = match;
  return (
    Number(minute) % START_INTERVAL_MINUTES === 0 &&
    second === '00' &&
    (fraction === undefined || !/[1-9]/.test(fraction))
  );
}

function requireAllowedDurations(
  record: JsonObject,
  key: string,
  windowMinutes: number,
  allowEmpty = false,
): number[] {
  const values = requireArray(record[key], key).map((value) => {
    if (!Number.isSafeInteger(value)) throw new Error(`${key} is invalid`);
    return value as number;
  });
  if (
    (!allowEmpty && values.length < 1) ||
    values.length > DURATION_OPTIONS.length ||
    values.some(
      (duration) =>
        duration < MIN_DURATION_MINUTES ||
        duration > MAX_DURATION_MINUTES ||
        duration % START_INTERVAL_MINUTES !== 0 ||
        duration > windowMinutes,
    ) ||
    values.some((duration, index) => index > 0 && values[index - 1] >= duration)
  ) {
    throw new Error(`${key} must be unique, sorted and fit the window`);
  }
  return values;
}

export function decodeWeeklyAvailabilityRule(
  value: unknown,
): WeeklyAvailabilityRule {
  const record = requireJsonObject(value, 'Weekly availability rule');
  const startTime = requireIsoLocalTime(record, 'startTime');
  const endTime = requireIsoLocalTime(record, 'endTime');
  const start = localTimeMinutes(startTime);
  const end = localTimeMinutes(endTime);
  if (
    start >= end ||
    start % START_INTERVAL_MINUTES !== 0 ||
    end % START_INTERVAL_MINUTES !== 0
  ) {
    throw new Error('Weekly availability rule time range is invalid');
  }

  return {
    id: requirePositiveSafeInteger(record, 'id'),
    dayOfWeek: requireEnum(record, 'dayOfWeek', DAY_OF_WEEK_VALUES),
    startTime,
    endTime,
    allowedDurations: requireAllowedDurations(
      record,
      'allowedDurations',
      end - start,
    ),
    locationLabel: requireNullableString(record, 'locationLabel'),
    capacityPerSlot: requirePositiveSafeInteger(record, 'capacityPerSlot'),
    active: requireBoolean(record, 'active'),
    validFrom: requireIsoLocalDate(record, 'validFrom'),
    createdAt: requireString(record, 'createdAt'),
    updatedAt: requireString(record, 'updatedAt'),
  };
}

export function decodeWeeklyAvailabilityRuleList(
  value: unknown,
): WeeklyAvailabilityRule[] {
  return requireArray(value, 'Weekly availability rule list').map(
    decodeWeeklyAvailabilityRule,
  );
}

export function decodeWeeklyAvailabilityRuleImpact(
  value: unknown,
): WeeklyAvailabilityRuleImpact {
  const record = requireJsonObject(value, 'Weekly availability impact');
  const impactDetected = requireBoolean(record, 'impactDetected');
  const impactedBookingCount = requireNonNegativeSafeInteger(
    record,
    'impactedBookingCount',
  );
  const changeReasonRequired = requireBoolean(record, 'changeReasonRequired');
  if (
    impactDetected !== impactedBookingCount > 0 ||
    changeReasonRequired !== impactDetected
  ) {
    throw new Error('Weekly availability impact is inconsistent');
  }
  return { impactDetected, impactedBookingCount, changeReasonRequired };
}

export function decodeAvailabilitySlot(value: unknown): AvailabilitySlot {
  const record = requireJsonObject(value, 'Availability slot');
  const startDateTime = requireBusinessOffsetDateTime(record, 'startDateTime');
  const endDateTime = requireBusinessOffsetDateTime(record, 'endDateTime');
  const capacity = requirePositiveSafeInteger(record, 'capacity');
  const maximumOccupancy = requireNonNegativeSafeInteger(
    record,
    'maximumOccupancy',
  );
  const minimumRemainingCapacity = requireNonNegativeSafeInteger(
    record,
    'minimumRemainingCapacity',
  );
  const startIntervalMinutes = requirePositiveSafeInteger(
    record,
    'startIntervalMinutes',
  );
  const blocked = requireBoolean(record, 'blocked');
  const active = requireBoolean(record, 'active');
  const bookable = requireBoolean(record, 'bookable');
  const windowMinutes =
    (Date.parse(endDateTime) - Date.parse(startDateTime)) / 60_000;
  if (windowMinutes <= 0) {
    throw new Error('Availability slot time range is invalid');
  }
  if (
    maximumOccupancy > capacity ||
    minimumRemainingCapacity !== capacity - maximumOccupancy
  ) {
    throw new Error('Availability slot capacity is inconsistent');
  }
  if (startIntervalMinutes !== START_INTERVAL_MINUTES) {
    throw new Error('Availability start interval is unsupported');
  }
  const allowedDurations = requireAllowedDurations(
    record,
    'allowedDurations',
    windowMinutes,
    true,
  );
  if (
    bookable &&
    (!active ||
      blocked ||
      minimumRemainingCapacity === 0 ||
      allowedDurations.length === 0)
  ) {
    throw new Error('Availability slot bookable state is inconsistent');
  }

  return {
    id: requirePositiveSafeInteger(record, 'id'),
    startDateTime,
    endDateTime,
    locationLabel: requireNullableString(record, 'locationLabel'),
    capacity,
    maximumOccupancy,
    minimumRemainingCapacity,
    allowedDurations,
    startIntervalMinutes,
    blocked,
    active,
    bookable,
  };
}

export function decodeAvailabilitySlotList(value: unknown): AvailabilitySlot[] {
  return requireArray(value, 'Availability slot list').map(
    decodeAvailabilitySlot,
  );
}

export function decodeClientAvailabilityWindow(
  value: unknown,
): ClientAvailabilityWindow {
  const record = requireJsonObject(value, 'Client availability window');
  const windowStart = requireBusinessOffsetDateTime(record, 'windowStart');
  const windowEnd = requireBusinessOffsetDateTime(record, 'windowEnd');
  const windowMinutes =
    (Date.parse(windowEnd) - Date.parse(windowStart)) / 60_000;
  if (windowMinutes <= 0) {
    throw new Error('Client availability window time range is invalid');
  }
  const allowedDurations = requireAllowedDurations(
    record,
    'allowedDurations',
    windowMinutes,
  );
  const startIntervalMinutes = requirePositiveSafeInteger(
    record,
    'startIntervalMinutes',
  );
  if (startIntervalMinutes !== START_INTERVAL_MINUTES) {
    throw new Error('Client availability start interval is unsupported');
  }

  const bookableOptions = requireArray(
    record.bookableOptions,
    'bookableOptions',
  ).map((option, index) => {
    const optionRecord = requireJsonObject(
      option,
      `bookableOptions[${String(index)}]`,
    );
    const startDateTime = requireBusinessOffsetDateTime(
      optionRecord,
      'startDateTime',
    );
    const startInstant = Date.parse(startDateTime);
    if (
      startInstant < Date.parse(windowStart) ||
      startInstant >= Date.parse(windowEnd) ||
      !isAlignedBookableStart(startDateTime)
    ) {
      throw new Error(
        'Bookable option start is outside the window or not aligned',
      );
    }
    const remainingMinutes = (Date.parse(windowEnd) - startInstant) / 60_000;
    const optionDurations = requireAllowedDurations(
      optionRecord,
      'allowedDurations',
      remainingMinutes,
    );
    if (
      optionDurations.some((duration) => !allowedDurations.includes(duration))
    ) {
      throw new Error('Bookable option duration is not allowed by the window');
    }
    return { startDateTime, allowedDurations: optionDurations };
  });
  if (
    bookableOptions.length === 0 ||
    bookableOptions.some(
      (option, index) =>
        index > 0 &&
        Date.parse(bookableOptions[index - 1].startDateTime) >=
          Date.parse(option.startDateTime),
    )
  ) {
    throw new Error('bookableOptions must be non-empty, unique and sorted');
  }

  return {
    occurrenceId: requirePositiveSafeInteger(record, 'occurrenceId'),
    windowStart,
    windowEnd,
    allowedDurations,
    startIntervalMinutes,
    location: requireNullableString(record, 'location'),
    capacity: requirePositiveSafeInteger(record, 'capacity'),
    bookableOptions,
  };
}

export function decodeClientAvailabilityWindowList(
  value: unknown,
): ClientAvailabilityWindow[] {
  return requireArray(value, 'Client availability window list').map(
    decodeClientAvailabilityWindow,
  );
}
