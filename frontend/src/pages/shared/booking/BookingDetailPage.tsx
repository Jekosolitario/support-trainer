import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';

import {
  cancelBooking,
  confirmBooking,
  getBookingDetail,
  rejectBooking,
} from '../../../api/bookingApi';
import type { BookingDetail } from '../../../api/bookingTypes';
import { HttpApiError } from '../../../api/types';
import { PageTemplate } from '../../../components/page/PageTemplate';
import { ActionLink } from '../../../components/ui/ActionLink';
import { Button } from '../../../components/ui/Button';
import {
  bookingTemporalState,
  isAbortError,
  parseBookingId,
  type BookingViewer,
} from './bookingPresentation';
import { BookingDetailView, ErrorState, LoadingState } from './BookingUi';
import styles from './BookingWorkflow.module.css';
import { useBookingLoad } from './useBookingLoad';

type BookingAction = 'confirm' | 'reject' | 'cancel';

function DetailFlow({
  bookingId,
  viewer,
}: {
  readonly bookingId: number;
  readonly viewer: BookingViewer;
}) {
  const loader = useCallback(
    (signal: AbortSignal) => getBookingDetail(bookingId, { signal }),
    [bookingId],
  );
  const [loadState, reload, replaceData] = useBookingLoad(
    `booking-${String(bookingId)}`,
    loader,
  );
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<BookingAction | null>(
    null,
  );
  const mutationLockRef = useRef(false);
  const mutationAbortRef = useRef<AbortController | null>(null);
  const mutationGenerationRef = useRef(0);
  const mountedRef = useRef(false);
  const bookingIdRef = useRef(bookingId);

  useLayoutEffect(() => {
    mountedRef.current = true;
    bookingIdRef.current = bookingId;
    mutationGenerationRef.current += 1;
    mutationAbortRef.current?.abort();
    mutationAbortRef.current = null;
    mutationLockRef.current = false;
    return () => {
      mountedRef.current = false;
      mutationGenerationRef.current += 1;
      mutationAbortRef.current?.abort();
      mutationAbortRef.current = null;
      mutationLockRef.current = false;
    };
  }, [bookingId]);

  async function runAction(
    action: BookingAction,
    booking: BookingDetail,
  ): Promise<void> {
    if (mutationLockRef.current || booking.id !== bookingId) return;
    const normalizedReason = reason.trim();
    const reasonRequired =
      action === 'reject' ||
      (action === 'cancel' &&
        (viewer === 'PROFESSIONAL' || booking.status === 'CONFIRMED'));
    if (reasonRequired && normalizedReason === '') {
      setReasonError('La motivazione è obbligatoria.');
      return;
    }
    if (normalizedReason.length > 1000) {
      setReasonError('La motivazione non può superare 1000 caratteri.');
      return;
    }

    mutationLockRef.current = true;
    setPendingAction(action);
    setFeedback(null);
    setReasonError(null);
    const controller = new AbortController();
    mutationAbortRef.current = controller;
    const requestBookingId = bookingId;
    const requestGeneration = mutationGenerationRef.current + 1;
    mutationGenerationRef.current = requestGeneration;
    const isCurrentMutation = () =>
      mountedRef.current &&
      bookingIdRef.current === requestBookingId &&
      mutationGenerationRef.current === requestGeneration &&
      mutationAbortRef.current === controller &&
      !controller.signal.aborted;
    try {
      let updated: BookingDetail;
      if (action === 'confirm') {
        updated = await confirmBooking(booking.id, {
          signal: controller.signal,
        });
      } else if (action === 'reject') {
        updated = await rejectBooking(booking.id, normalizedReason, {
          signal: controller.signal,
        });
      } else {
        updated = await cancelBooking(
          booking.id,
          normalizedReason === '' ? null : normalizedReason,
          { signal: controller.signal },
        );
      }
      if (!isCurrentMutation()) return;
      if (updated.id !== requestBookingId) {
        setFeedback('La risposta ricevuta non è coerente. Ricarico i dati.');
        reload();
        return;
      }
      replaceData(updated);
      setReason('');
      setFeedback('Prenotazione aggiornata.');
    } catch (error) {
      if (!isCurrentMutation() || isAbortError(error)) return;
      if (error instanceof HttpApiError && error.status === 400) {
        const fieldError = error.body?.fieldErrors?.find(
          (entry) => entry.field === 'reason',
        );
        setReasonError(
          fieldError?.message ?? 'Controlla la motivazione inserita.',
        );
      } else if (error instanceof HttpApiError && error.status === 409) {
        setFeedback(
          error.body?.code === 'BOOKING_REQUEST_ENDED'
            ? 'Questo appuntamento è già trascorso e non può più essere modificato.'
            : 'La prenotazione è stata aggiornata nel frattempo.',
        );
        reload();
      } else if (error instanceof HttpApiError && error.status === 404) {
        setFeedback('Prenotazione non disponibile');
        reload();
      } else {
        setFeedback('Non è stato possibile aggiornare la prenotazione.');
      }
    } finally {
      if (isCurrentMutation()) {
        mutationAbortRef.current = null;
        mutationLockRef.current = false;
        setPendingAction(null);
      }
    }
  }

  if (loadState.status === 'loading') {
    return <LoadingState label="Caricamento prenotazione…" />;
  }
  if (loadState.status === 'error') {
    if (
      loadState.error instanceof HttpApiError &&
      loadState.error.status === 404
    ) {
      return <p> Prenotazione non disponibile </p>;
    }
    return (
      <ErrorState
        message="Non è stato possibile caricare la prenotazione."
        retry={reload}
      />
    );
  }

  const booking = loadState.data;
  const temporal = bookingTemporalState(
    booking.scheduledStart,
    booking.scheduledEnd,
  );
  const mutable = temporal !== 'PAST';
  const clientCanCancel =
    viewer === 'CLIENT' &&
    mutable &&
    (booking.status === 'PENDING' || booking.status === 'CONFIRMED');
  const professionalCanDecide =
    viewer === 'PROFESSIONAL' && mutable && booking.status === 'PENDING';
  const professionalCanCancel =
    viewer === 'PROFESSIONAL' && mutable && booking.status === 'CONFIRMED';
  const showsReason =
    clientCanCancel || professionalCanDecide || professionalCanCancel;
  const reasonRequired =
    professionalCanDecide ||
    professionalCanCancel ||
    (clientCanCancel && booking.status === 'CONFIRMED');

  return (
    <div className={styles.form}>
      <BookingDetailView booking={booking} viewer={viewer} />
      {feedback !== null ? (
        <p className={styles.feedback} role="status">
          {feedback}
        </p>
      ) : null}

      {showsReason ? (
        <section
          className={styles.form}
          aria-labelledby="booking-actions-title"
        >
          <h2 id="booking-actions-title">Azioni</h2>
          <label className={styles.field}>
            {professionalCanDecide
              ? 'Motivazione del rifiuto *'
              : reasonRequired
                ? 'Motivazione *'
                : 'Motivazione (facoltativa)'}
            <textarea
              maxLength={1000}
              value={reason}
              disabled={pendingAction !== null}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          {reasonError !== null ? (
            <p className={styles.fieldError} role="alert">
              {reasonError}
            </p>
          ) : null}
          <div className={styles.actionRow}>
            {professionalCanDecide ? (
              <>
                <Button
                  disabled={pendingAction !== null}
                  onClick={() => void runAction('confirm', booking)}
                  type="button"
                  variant="primary"
                >
                  {pendingAction === 'confirm' ? 'Conferma…' : 'Conferma'}
                </Button>
                <Button
                  disabled={pendingAction !== null}
                  onClick={() => void runAction('reject', booking)}
                  type="button"
                  variant="danger"
                >
                  {pendingAction === 'reject' ? 'Rifiuto…' : 'Rifiuta'}
                </Button>
              </>
            ) : null}
            {clientCanCancel || professionalCanCancel ? (
              <Button
                disabled={pendingAction !== null}
                onClick={() => void runAction('cancel', booking)}
                type="button"
                variant="secondary"
              >
                {pendingAction === 'cancel'
                  ? 'Annullamento…'
                  : booking.status === 'CONFIRMED'
                    ? 'Annulla appuntamento'
                    : 'Annulla richiesta'}
              </Button>
            ) : null}
          </div>
        </section>
      ) : null}

      <ActionLink
        to={
          viewer === 'CLIENT'
            ? '/app/client/bookings'
            : '/app/professional/bookings'
        }
        variant="secondary"
      >
        Torna alle prenotazioni
      </ActionLink>
    </div>
  );
}

export function BookingDetailPage({
  viewer,
}: {
  readonly viewer: BookingViewer;
}) {
  const { bookingRequestId: rawBookingId } = useParams();
  const bookingId = parseBookingId(rawBookingId);
  return (
    <PageTemplate
      appearance="authenticated"
      eyebrow={viewer === 'CLIENT' ? 'Area cliente' : 'Area personal trainer'}
      title="Dettaglio prenotazione"
      description="Consulta lo stato e i dati storici della prenotazione."
    >
      {bookingId === null ? (
        <p>Prenotazione non disponibile</p>
      ) : (
        <DetailFlow
          key={`${viewer}-${String(bookingId)}`}
          bookingId={bookingId}
          viewer={viewer}
        />
      )}
    </PageTemplate>
  );
}
