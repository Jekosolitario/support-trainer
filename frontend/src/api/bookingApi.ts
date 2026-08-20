import {
  assertPositiveSafeIntegerInput,
  requestDecoded,
} from './apiResponseDecoders';
import { performCsrfMutation } from './csrfMutation';
import { UnexpectedResponseError } from './types';
import {
  decodeBookingDetail,
  decodeBookingSummaryList,
  type BookingDetail,
  type BookingSummary,
  type CreateBookingInput,
} from './bookingTypes';

export interface BookingApiOptions {
  readonly signal?: AbortSignal;
}

export async function createBooking(
  input: CreateBookingInput,
  options: BookingApiOptions = {},
): Promise<BookingDetail> {
  return mutation(
    '/bookings',
    jsonMutation('POST', input, options.signal),
    201,
  );
}

export function listClientBookings(
  options: BookingApiOptions = {},
): Promise<BookingSummary[]> {
  return requestDecoded(
    '/bookings/client',
    options.signal,
    decodeBookingSummaryList,
    'Client booking list response',
  );
}

export function listProfessionalBookings(
  options: BookingApiOptions = {},
): Promise<BookingSummary[]> {
  return requestDecoded(
    '/bookings/professional',
    options.signal,
    decodeBookingSummaryList,
    'Professional booking list response',
  );
}

export function getBookingDetail(
  bookingId: number,
  options: BookingApiOptions = {},
): Promise<BookingDetail> {
  assertPositiveSafeIntegerInput(bookingId, 'bookingId');
  return requestDecoded(
    `/bookings/${String(bookingId)}`,
    options.signal,
    decodeBookingDetail,
    'Booking detail response',
  );
}

export function confirmBooking(
  bookingId: number,
  options: BookingApiOptions = {},
): Promise<BookingDetail> {
  assertPositiveSafeIntegerInput(bookingId, 'bookingId');
  return mutation(
    `/bookings/${String(bookingId)}/confirm`,
    mutationOptions('PATCH', options.signal),
    200,
  );
}

export function rejectBooking(
  bookingId: number,
  reason: string,
  options: BookingApiOptions = {},
): Promise<BookingDetail> {
  assertPositiveSafeIntegerInput(bookingId, 'bookingId');
  return mutation(
    `/bookings/${String(bookingId)}/reject`,
    jsonMutation('PATCH', { reason }, options.signal),
    200,
  );
}

export function cancelBooking(
  bookingId: number,
  reason?: string | null,
  options: BookingApiOptions = {},
): Promise<BookingDetail> {
  assertPositiveSafeIntegerInput(bookingId, 'bookingId');
  const requestOptions =
    reason === undefined
      ? mutationOptions('PATCH', options.signal)
      : jsonMutation('PATCH', { reason }, options.signal);
  return mutation(`/bookings/${String(bookingId)}/cancel`, requestOptions, 200);
}

function mutationOptions(method: 'POST' | 'PATCH', signal?: AbortSignal) {
  return {
    method,
    invalidateOn401: true as const,
    invalidateCsrfOnCommit: false as const,
    signal,
  };
}

function jsonMutation(
  method: 'POST' | 'PATCH',
  body: object,
  signal?: AbortSignal,
) {
  return {
    ...mutationOptions(method, signal),
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

async function mutation(
  path: string,
  options: ReturnType<typeof mutationOptions> | ReturnType<typeof jsonMutation>,
  status: number,
): Promise<BookingDetail> {
  const payload = await performCsrfMutation<unknown>(path, options);
  try {
    return decodeBookingDetail(payload);
  } catch (cause) {
    throw new UnexpectedResponseError(
      status,
      new Response(null, { status }),
      cause,
      'Booking mutation response failed runtime decoding',
    );
  }
}
