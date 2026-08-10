import { PageTemplate } from '../../components/page/PageTemplate';

export { ProfessionalClientDetailPage } from './ProfessionalClientDetailPage';
export { ProfessionalClientsPage } from './ProfessionalClientsPage';

export function ProfessionalAvailabilityPage() {
  return (
    <PageTemplate
      eyebrow="Area personal trainer"
      title="Disponibilità"
      description="Struttura per la futura gestione degli slot del personal trainer."
    />
  );
}

export function ProfessionalBookingsPage() {
  return (
    <PageTemplate
      eyebrow="Area personal trainer"
      title="Prenotazioni"
      description="Questa pagina ospiterà le richieste di prenotazione ricevute dal personal trainer."
    />
  );
}

export function ProfessionalBookingDetailPage() {
  return (
    <PageTemplate
      eyebrow="Area personal trainer"
      title="Dettaglio prenotazione"
      description="Struttura destinata al dettaglio e alle future transizioni della prenotazione identificata dalla rotta."
    />
  );
}
