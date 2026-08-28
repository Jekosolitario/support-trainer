import { useCallback } from 'react';

import { listClientBookings } from '../../api/bookingApi';
import { PageTemplate } from '../../components/page/PageTemplate';
import { ActionLink } from '../../components/ui/ActionLink';
import { groupClientBookings } from '../shared/booking/bookingPresentation';
import {
  BookingListSection,
  ErrorState,
  LoadingState,
} from '../shared/booking/BookingUi';
import styles from '../shared/booking/BookingWorkflow.module.css';
import { useBookingLoad } from '../shared/booking/useBookingLoad';

export function ClientBookingsPage() {
  const loader = useCallback(
    (signal: AbortSignal) => listClientBookings({ signal }),
    [],
  );
  const [state, reload] = useBookingLoad('client-bookings', loader);
  const groups =
    state.status === 'success' ? groupClientBookings(state.data) : null;
  return (
    <PageTemplate
      appearance="authenticated"
      eyebrow="Area cliente"
      title="Prenotazioni"
      description="Consulta richieste, appuntamenti confermati e storico."
    >
      {state.status === 'loading' ? (
        <LoadingState label="Caricamento prenotazioni…" />
      ) : null}
      {state.status === 'error' ? (
        <ErrorState
          message="Non è stato possibile caricare le prenotazioni."
          retry={reload}
        />
      ) : null}
      {state.status === 'success' && state.data.length === 0 ? (
        <div className={styles.empty}>
          <p>Non hai ancora prenotazioni.</p>
          <ActionLink to="/app/client/professionals" variant="secondary">
            Vai ai miei professionisti
          </ActionLink>
        </div>
      ) : null}
      {groups !== null ? (
        <>
          <BookingListSection
            sectionId="client-bookings-upcoming-title"
            title="Prossime"
            bookings={groups.upcoming}
            detailBasePath="/app/client/bookings"
          />
          <BookingListSection
            sectionId="client-bookings-history-title"
            title="Storico"
            bookings={groups.history}
            detailBasePath="/app/client/bookings"
          />
        </>
      ) : null}
    </PageTemplate>
  );
}
