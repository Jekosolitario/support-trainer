import { useCallback } from 'react';

import { listProfessionalBookings } from '../../api/bookingApi';
import { PageTemplate } from '../../components/page/PageTemplate';
import { groupProfessionalBookings } from '../shared/booking/bookingPresentation';
import {
  BookingListSection,
  ErrorState,
  LoadingState,
} from '../shared/booking/BookingUi';
import { useBookingLoad } from '../shared/booking/useBookingLoad';

export function ProfessionalBookingsPage() {
  const loader = useCallback(
    (signal: AbortSignal) => listProfessionalBookings({ signal }),
    [],
  );
  const [state, reload] = useBookingLoad('professional-bookings', loader);
  const groups =
    state.status === 'success' ? groupProfessionalBookings(state.data) : null;
  return (
    <PageTemplate
      eyebrow="Area personal trainer"
      title="Prenotazioni"
      description="Gestisci le richieste e consulta gli appuntamenti."
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
        <p>Non ci sono prenotazioni.</p>
      ) : null}
      {groups !== null ? (
        <>
          <BookingListSection
            sectionId="professional-bookings-pending-title"
            title="Da gestire"
            bookings={groups.pending}
            detailBasePath="/app/professional/bookings"
          />
          <BookingListSection
            sectionId="professional-bookings-confirmed-title"
            title="Confermate"
            bookings={groups.confirmed}
            detailBasePath="/app/professional/bookings"
          />
          <BookingListSection
            sectionId="professional-bookings-history-title"
            title="Storico"
            bookings={groups.history}
            detailBasePath="/app/professional/bookings"
          />
        </>
      ) : null}
    </PageTemplate>
  );
}
