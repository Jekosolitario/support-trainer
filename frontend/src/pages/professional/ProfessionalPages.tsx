import { PageTemplate } from '../../components/page/PageTemplate';

export function ProfessionalClientsPage() {
  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Clienti"
      description="Questa pagina mostrerà in futuro i clienti collegati al professionista."
    />
  );
}

export function ProfessionalClientDetailPage() {
  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Dettaglio cliente"
      description="Struttura destinata alle informazioni minime del cliente collegato identificato dalla rotta."
    />
  );
}

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
