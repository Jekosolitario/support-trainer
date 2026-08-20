import type {
  BookingCancellationActor,
  BookingStatus,
  BookingSummary,
} from '../../../api/bookingTypes';

export type BookingTemporalState = 'UPCOMING' | 'IN_PROGRESS' | 'PAST';
export type BookingViewer = 'CLIENT' | 'PROFESSIONAL';

export function bookingTemporalState(
  scheduledStart: string,
  scheduledEnd: string,
  now: Date = new Date(),
): BookingTemporalState {
  const nowValue = now.getTime();
  if (nowValue < Date.parse(scheduledStart)) return 'UPCOMING';
  if (nowValue < Date.parse(scheduledEnd)) return 'IN_PROGRESS';
  return 'PAST';
}

export function bookingStatusLabel(
  status: BookingStatus,
  temporalState: BookingTemporalState,
): string {
  if (status === 'PENDING' && temporalState === 'PAST') {
    return 'Richiesta scaduta';
  }
  if (status === 'CONFIRMED' && temporalState === 'PAST') {
    return 'Appuntamento passato';
  }
  return {
    PENDING: 'In attesa',
    CONFIRMED: 'Confermata',
    REJECTED: 'Rifiutata',
    CANCELLED: 'Annullata',
  }[status];
}

export function cancellationActorLabel(
  actor: BookingCancellationActor | null,
  viewer: BookingViewer,
): string {
  if (actor === null) return 'Annullata';
  if (viewer === 'CLIENT') {
    return actor === 'CLIENT'
      ? 'Annullata da te'
      : 'Annullata dal Personal Trainer';
  }
  return actor === 'CLIENT' ? 'Annullata dal cliente' : 'Annullata da te';
}

export function groupClientBookings(bookings: readonly BookingSummary[]): {
  upcoming: BookingSummary[];
  history: BookingSummary[];
} {
  const upcoming: BookingSummary[] = [];
  const history: BookingSummary[] = [];
  for (const booking of bookings) {
    const temporal = bookingTemporalState(
      booking.scheduledStart,
      booking.scheduledEnd,
    );
    if (
      (booking.status === 'PENDING' || booking.status === 'CONFIRMED') &&
      temporal !== 'PAST'
    ) {
      upcoming.push(booking);
    } else {
      history.push(booking);
    }
  }
  return { upcoming, history };
}

export function groupProfessionalBookings(
  bookings: readonly BookingSummary[],
): {
  pending: BookingSummary[];
  confirmed: BookingSummary[];
  history: BookingSummary[];
} {
  const pending: BookingSummary[] = [];
  const confirmed: BookingSummary[] = [];
  const history: BookingSummary[] = [];
  for (const booking of bookings) {
    const isPast =
      bookingTemporalState(booking.scheduledStart, booking.scheduledEnd) ===
      'PAST';
    if (booking.status === 'PENDING' && !isPast) pending.push(booking);
    else if (booking.status === 'CONFIRMED' && !isPast) confirmed.push(booking);
    else history.push(booking);
  }
  return { pending, confirmed, history };
}

export function formatBookingDateTime(value: string): string {
  return new Intl.DateTimeFormat('it-IT', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Rome',
  }).format(new Date(value));
}

export function formatBookingDay(value: string): string {
  return new Intl.DateTimeFormat('it-IT', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'Europe/Rome',
  }).format(new Date(value));
}

export function formatBookingTime(value: string): string {
  return new Intl.DateTimeFormat('it-IT', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Europe/Rome',
  }).format(new Date(value));
}

export function parseBookingId(rawValue: string | undefined): number | null {
  if (rawValue === undefined || !/^[1-9]\d*$/.test(rawValue)) return null;
  const value = Number(rawValue);
  return Number.isSafeInteger(value) ? value : null;
}

export function isAbortError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'AbortError'
  );
}
