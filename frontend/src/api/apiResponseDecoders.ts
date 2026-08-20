import { request } from './httpClient';
import { UnexpectedResponseError } from './types';

export type JsonObject = Record<string, unknown>;

export function requireJsonObject(value: unknown, label: string): JsonObject {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be a JSON object`);
  }

  return Object.fromEntries(Object.entries(value));
}

export function requireString(record: JsonObject, key: string): string {
  const value = record[key];
  if (typeof value !== 'string') {
    throw new Error(`${key} must be a string`);
  }

  return value;
}

export function requireNullableString(
  record: JsonObject,
  key: string,
): string | null {
  const value = record[key];
  if (value !== null && typeof value !== 'string') {
    throw new Error(`${key} must be a string or null`);
  }

  return value;
}

export function requireBoolean(record: JsonObject, key: string): boolean {
  const value = record[key];
  if (typeof value !== 'boolean') {
    throw new Error(`${key} must be a boolean`);
  }

  return value;
}

export function isPositiveSafeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

export function requirePositiveSafeInteger(
  record: JsonObject,
  key: string,
): number {
  const value = record[key];
  if (!isPositiveSafeInteger(value)) {
    throw new Error(`${key} must be a positive safe integer`);
  }

  return value;
}

export function assertPositiveSafeIntegerInput(
  value: unknown,
  label: string,
): asserts value is number {
  if (!isPositiveSafeInteger(value)) {
    throw new RangeError(`${label} must be a positive safe integer`);
  }
}

export function requireFiniteNumber(record: JsonObject, key: string): number {
  const value = record[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`${key} must be a finite number`);
  }

  return value;
}

function isAllowedValue<T extends string>(
  value: string,
  allowedValues: readonly T[],
): value is T {
  return allowedValues.some((allowedValue) => allowedValue === value);
}

export function requireEnum<T extends string>(
  record: JsonObject,
  key: string,
  allowedValues: readonly T[],
): T {
  const value = record[key];
  if (typeof value !== 'string' || !isAllowedValue(value, allowedValues)) {
    throw new Error(`${key} is not an allowed value`);
  }

  return value;
}

function isLeapYear(year: number): boolean {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    return isLeapYear(year) ? 29 : 28;
  }

  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

export function requireIsoLocalDate(record: JsonObject, key: string): string {
  const value = record[key];
  if (typeof value !== 'string') {
    throw new Error(`${key} must be an ISO local date`);
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match === null) {
    throw new Error(`${key} must be an ISO local date`);
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);

  if (
    year < 1 ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > daysInMonth(year, month)
  ) {
    throw new Error(`${key} must be a valid ISO local date`);
  }

  return value;
}

export function requireIsoInstant(record: JsonObject, key: string): string {
  const value = requireString(record, key);
  if (
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) ||
    Number.isNaN(Date.parse(value))
  ) {
    throw new Error(`${key} must be an ISO instant`);
  }
  return value;
}

export function requireNullableIsoInstant(
  record: JsonObject,
  key: string,
): string | null {
  if (record[key] === null) return null;
  return requireIsoInstant(record, key);
}

export function requireBusinessOffsetDateTime(
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

export function requireArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new Error(`${label} must be an array`);
  }

  return value;
}

export async function requestDecoded<T>(
  path: string,
  signal: AbortSignal | undefined,
  decoder: (value: unknown) => T,
  responseLabel: string,
): Promise<T> {
  const payload = await request<unknown>(path, {
    method: 'GET',
    invalidateOn401: true,
    signal,
  });

  try {
    return decoder(payload);
  } catch (cause) {
    throw new UnexpectedResponseError(
      200,
      new Response(null, { status: 200 }),
      cause,
      `${responseLabel} failed runtime decoding`,
    );
  }
}
