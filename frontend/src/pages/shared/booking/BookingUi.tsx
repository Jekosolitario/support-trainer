import { Link } from 'react-router-dom';

import type { BookingDetail, BookingSummary } from '../../../api/bookingTypes';
import {
  bookingStatusLabel,
  bookingTemporalState,
  cancellationActorLabel,
  formatBookingDateTime,
  type BookingViewer,
} from './bookingPresentation';
import styles from './BookingWorkflow.module.css';

export function BookingListSection({
  sectionId,
  title,
  bookings,
  detailBasePath,
}: {
  readonly sectionId: string;
  readonly title: string;
  readonly bookings: readonly BookingSummary[];
  readonly detailBasePath: string;
}) {
  if (bookings.length === 0) return null;
  return (
    <section className={styles.section} aria-labelledby={sectionId}>
      <h2 id={sectionId}>{title}</h2>
      <ul className={styles.bookingList}>
        {bookings.map((booking) => {
          const temporal = bookingTemporalState(
            booking.scheduledStart,
            booking.scheduledEnd,
          );
          return (
            <li
              className={`${styles.card} ${temporal === 'PAST' ? styles.past : ''}`}
              key={booking.id}
            >
              <Link to={`${detailBasePath}/${String(booking.id)}`}>
                <span className={styles.cardTitle}>
                  {booking.counterparty.displayName}
                </span>
                <span>
                  {formatBookingDateTime(booking.scheduledStart)} ·{' '}
                  {String(booking.durationMinutes)} min
                </span>
                <span className={styles.badge}>
                  {bookingStatusLabel(booking.status, temporal)}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export function BookingDetailView({
  booking,
  viewer,
}: {
  readonly booking: BookingDetail;
  readonly viewer: BookingViewer;
}) {
  const temporal = bookingTemporalState(
    booking.scheduledStart,
    booking.scheduledEnd,
  );
  const counterparty =
    viewer === 'CLIENT' ? booking.professional : booking.client;
  return (
    <div className={temporal === 'PAST' ? styles.past : undefined}>
      <section className={styles.card} aria-labelledby="booking-summary-title">
        <h2 id="booking-summary-title">{counterparty.displayName}</h2>
        <p className={styles.badge}>
          {bookingStatusLabel(booking.status, temporal)}
        </p>
        <dl className={styles.detailList}>
          <div>
            <dt>Inizio</dt>
            <dd>{formatBookingDateTime(booking.scheduledStart)}</dd>
          </div>
          <div>
            <dt>Fine</dt>
            <dd>{formatBookingDateTime(booking.scheduledEnd)}</dd>
          </div>
          <div>
            <dt>Durata</dt>
            <dd>{booking.durationMinutes} minuti</dd>
          </div>
          {booking.note !== null ? (
            <div>
              <dt>Nota del cliente</dt>
              <dd>{booking.note}</dd>
            </div>
          ) : null}
        </dl>
      </section>

      <section className={styles.section} aria-labelledby="booking-items-title">
        <h2 id="booking-items-title">Dettagli appuntamento</h2>
        <ul className={styles.itemList}>
          {booking.items.map((item) => (
            <li className={styles.card} key={item.id}>
              <p>{formatBookingDateTime(item.scheduledStart)}</p>
              <p>{item.durationMinutes} minuti</p>
              {item.locationLabel !== null ? (
                <p>Luogo: {item.locationLabel}</p>
              ) : null}
            </li>
          ))}
        </ul>
      </section>

      {booking.status === 'REJECTED' ? (
        <section className={styles.decision} aria-labelledby="rejection-title">
          <h2 id="rejection-title">Rifiutata</h2>
          {booking.rejectionReason !== null ? (
            <p>{booking.rejectionReason}</p>
          ) : null}
        </section>
      ) : null}

      {booking.status === 'CANCELLED' ? (
        <section
          className={styles.decision}
          aria-labelledby="cancellation-title"
        >
          <h2 id="cancellation-title">
            {cancellationActorLabel(booking.cancelledBy, viewer)}
          </h2>
          {booking.cancellationReason !== null ? (
            <p>{booking.cancellationReason}</p>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

export function LoadingState({ label }: { readonly label: string }) {
  return (
    <p className={styles.status} role="status">
      {label}
    </p>
  );
}

export function ErrorState({
  message,
  retry,
}: {
  readonly message: string;
  readonly retry: () => void;
}) {
  return (
    <div className={styles.error} role="alert">
      <p>{message}</p>
      <button className={styles.secondaryButton} type="button" onClick={retry}>
        Riprova
      </button>
    </div>
  );
}
