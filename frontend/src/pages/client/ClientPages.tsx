import { PageTemplate } from '../../components/page/PageTemplate';

export function ClientProfessionalsPage() {
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Professionisti"
      description="Questa pagina mostrerà in futuro i professionisti collegati al cliente. Nessun dato dimostrativo è stato inserito."
    />
  );
}

export function ClientProfessionalDetailPage() {
  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Dettaglio professionista"
      description="Struttura per le informazioni del professionista collegato identificato dalla rotta."
    />
  );
}

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
