import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { createBooking } from '../../api/bookingApi';
import { listProfessionalAvailability } from '../../api/availabilityApi';
import type {
  ClientAvailabilityWindow,
  ClientBookableOption,
} from '../../api/availabilityTypes';
import { HttpApiError } from '../../api/types';
import { PageTemplate } from '../../components/page/PageTemplate';
import {
  formatBookingDay,
  formatBookingTime,
  parseBookingId,
  isAbortError,
} from '../shared/booking/bookingPresentation';
import { ErrorState, LoadingState } from '../shared/booking/BookingUi';
import styles from '../shared/booking/BookingWorkflow.module.css';
import { useBookingLoad } from '../shared/booking/useBookingLoad';

interface StartChoice {
  readonly professionalId: number;
  readonly occurrenceId: number;
  readonly option: ClientBookableOption;
  readonly location: string | null;
}

function AvailabilityFlow({
  professionalId,
}: {
  readonly professionalId: number;
}) {
  const navigate = useNavigate();
  const loader = useCallback(
    (signal: AbortSignal) =>
      listProfessionalAvailability(professionalId, { signal }),
    [professionalId],
  );
  const [loadState, reload] = useBookingLoad(
    `availability-${String(professionalId)}`,
    loader,
  );
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [selectedStart, setSelectedStart] = useState<StartChoice | null>(null);
  const [selectedDuration, setSelectedDuration] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [noteError, setNoteError] = useState<string | null>(null);
  const submittingRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const mountedRef = useRef(false);
  const professionalIdRef = useRef(professionalId);
  const createGenerationRef = useRef(0);
  const createAbortRef = useRef<AbortController | null>(null);

  useLayoutEffect(() => {
    mountedRef.current = true;
    professionalIdRef.current = professionalId;
    createGenerationRef.current += 1;
    createAbortRef.current?.abort();
    createAbortRef.current = null;
    submittingRef.current = false;
    return () => {
      mountedRef.current = false;
      createGenerationRef.current += 1;
      createAbortRef.current?.abort();
      createAbortRef.current = null;
      submittingRef.current = false;
    };
  }, [professionalId]);

  const choices = useMemo(() => {
    if (loadState.status !== 'success') return [];
    return loadState.data.flatMap((window: ClientAvailabilityWindow) =>
      window.bookableOptions.map((option) => ({
        professionalId,
        occurrenceId: window.occurrenceId,
        option,
        location: window.location,
      })),
    );
  }, [loadState, professionalId]);
  const days = Array.from(
    new Set(choices.map((choice) => choice.option.startDateTime.slice(0, 10))),
  );
  const starts =
    selectedDay === null
      ? []
      : choices.filter((choice) =>
          choice.option.startDateTime.startsWith(selectedDay),
        );

  async function submit(): Promise<void> {
    if (
      submittingRef.current ||
      selectedStart === null ||
      selectedStart.professionalId !== professionalId ||
      selectedDuration === null
    ) {
      return;
    }
    const normalizedNote = note.trim();
    if (normalizedNote.length > 1000) {
      setNoteError('La nota non può superare 1000 caratteri.');
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    setFeedback(null);
    setNoteError(null);
    const requestProfessionalId = professionalId;
    const requestGeneration = createGenerationRef.current + 1;
    createGenerationRef.current = requestGeneration;
    const controller = new AbortController();
    createAbortRef.current = controller;
    const isCurrentRequest = () =>
      mountedRef.current &&
      professionalIdRef.current === requestProfessionalId &&
      createGenerationRef.current === requestGeneration &&
      createAbortRef.current === controller &&
      !controller.signal.aborted;
    try {
      const booking = await createBooking(
        {
          availabilitySlotId: selectedStart.occurrenceId,
          startDateTime: selectedStart.option.startDateTime,
          durationMinutes: selectedDuration,
          note: normalizedNote === '' ? null : normalizedNote,
        },
        { signal: controller.signal },
      );
      if (
        !isCurrentRequest() ||
        booking.professional.id !== requestProfessionalId
      ) {
        return;
      }
      navigate(`/app/client/bookings/${String(booking.id)}`, { replace: true });
    } catch (error) {
      if (!isCurrentRequest() || isAbortError(error)) return;
      if (error instanceof HttpApiError && error.status === 400) {
        const fieldError = error.body?.fieldErrors?.find(
          (entry) => entry.field === 'note',
        );
        setNoteError(fieldError?.message ?? 'Controlla la nota inserita.');
      } else if (
        error instanceof HttpApiError &&
        (error.status === 409 ||
          (error.status === 404 &&
            error.body?.code === 'AVAILABILITY_SLOT_NOT_FOUND'))
      ) {
        setFeedback(
          'La disponibilità selezionata non è più attuale. Scegli una nuova opzione.',
        );
        setSelectedDay(null);
        setSelectedStart(null);
        setSelectedDuration(null);
        reload();
      } else {
        setFeedback('Non è stato possibile creare la prenotazione. Riprova.');
      }
    } finally {
      if (isCurrentRequest()) {
        createAbortRef.current = null;
        submittingRef.current = false;
        setSubmitting(false);
      }
    }
  }

  if (loadState.status === 'loading') {
    return <LoadingState label="Caricamento disponibilità…" />;
  }
  if (loadState.status === 'error') {
    if (
      loadState.error instanceof HttpApiError &&
      loadState.error.status === 404
    ) {
      return (
        <div className={styles.empty}>
          <p>Professionista non disponibile</p>
          <Link to="/app/client/professionals">Torna ai professionisti</Link>
        </div>
      );
    }
    return (
      <ErrorState
        message="Non è stato possibile caricare la disponibilità."
        retry={reload}
      />
    );
  }
  if (choices.length === 0) {
    return (
      <div className={styles.empty}>
        <p>Non ci sono orari disponibili al momento.</p>
        <button
          className={styles.secondaryButton}
          type="button"
          onClick={reload}
        >
          Aggiorna
        </button>
      </div>
    );
  }

  return (
    <div className={styles.form}>
      {feedback !== null ? <p role="alert">{feedback}</p> : null}
      <fieldset disabled={submitting}>
        <legend>1. Scegli il giorno</legend>
        <div className={styles.actionRow}>
          {days.map((day) => (
            <button
              className={`${styles.optionButton} ${selectedDay === day ? styles.optionButtonSelected : ''}`}
              key={day}
              type="button"
              onClick={() => {
                setSelectedDay(day);
                setSelectedStart(null);
                setSelectedDuration(null);
              }}
            >
              {formatBookingDay(`${day}T12:00:00+02:00`)}
            </button>
          ))}
        </div>
      </fieldset>

      {selectedDay !== null ? (
        <fieldset disabled={submitting}>
          <legend>2. Scegli l’orario</legend>
          <div className={styles.actionRow}>
            {starts.map((choice) => (
              <button
                className={`${styles.optionButton} ${selectedStart?.occurrenceId === choice.occurrenceId && selectedStart.option.startDateTime === choice.option.startDateTime ? styles.optionButtonSelected : ''}`}
                key={`${String(choice.occurrenceId)}-${choice.option.startDateTime}`}
                type="button"
                onClick={() => {
                  setSelectedStart(choice);
                  setSelectedDuration(null);
                }}
              >
                {formatBookingTime(choice.option.startDateTime)}
                {choice.location !== null ? ` · ${choice.location}` : ''}
              </button>
            ))}
          </div>
        </fieldset>
      ) : null}

      {selectedStart !== null ? (
        <fieldset disabled={submitting}>
          <legend>3. Scegli la durata</legend>
          <div className={styles.actionRow}>
            {selectedStart.option.allowedDurations.map((duration) => (
              <button
                className={`${styles.optionButton} ${selectedDuration === duration ? styles.optionButtonSelected : ''}`}
                key={duration}
                type="button"
                onClick={() => setSelectedDuration(duration)}
              >
                {duration} minuti
              </button>
            ))}
          </div>
        </fieldset>
      ) : null}

      {selectedStart !== null && selectedDuration !== null ? (
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <h2>Riepilogo</h2>
          <p>
            {formatBookingDay(selectedStart.option.startDateTime)},{' '}
            {formatBookingTime(selectedStart.option.startDateTime)} ·{' '}
            {selectedDuration} minuti
          </p>
          {selectedStart.location !== null ? (
            <p>Luogo: {selectedStart.location}</p>
          ) : null}
          <label className={styles.field}>
            Nota (facoltativa)
            <textarea
              maxLength={1000}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              disabled={submitting}
            />
          </label>
          {noteError !== null ? (
            <p className={styles.fieldError} role="alert">
              {noteError}
            </p>
          ) : null}
          <button
            className={styles.primaryButton}
            disabled={submitting}
            type="submit"
          >
            {submitting ? 'Invio in corso…' : 'Invia richiesta'}
          </button>
        </form>
      ) : null}
    </div>
  );
}

export function ClientProfessionalAvailabilityPage() {
  const { professionalId: rawId } = useParams();
  const professionalId = parseBookingId(rawId);
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Disponibilità professionista"
      description="Scegli giorno, orario e durata tra le opzioni disponibili."
    >
      {professionalId === null ? (
        <div className={styles.empty}>Professionista non disponibile</div>
      ) : (
        <AvailabilityFlow
          key={professionalId}
          professionalId={professionalId}
        />
      )}
    </PageTemplate>
  );
}
