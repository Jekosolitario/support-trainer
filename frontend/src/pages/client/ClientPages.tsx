import { PageTemplate } from '../../components/page/PageTemplate';

export { ClientProfessionalDetailPage } from './ClientProfessionalDetailPage';
export { ClientProfessionalsPage } from './ClientProfessionalsPage';

export function ClientProfessionalAvailabilityPage() {
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Disponibilità professionista"
      description="Struttura per consultare in futuro la disponibilità di un personal trainer collegato."
    />
  );
}

export function ClientBookingsPage() {
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Prenotazioni"
      description="Questa pagina ospiterà l’elenco delle richieste di prenotazione del cliente."
    />
  );
}

export function ClientBookingDetailPage() {
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Dettaglio prenotazione"
      description="Struttura destinata ai dettagli storici della prenotazione identificata dalla rotta."
    />
  );
}
