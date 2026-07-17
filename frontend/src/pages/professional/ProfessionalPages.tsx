import { Link } from 'react-router-dom';

import pageTemplateStyles from '../../components/page/PageTemplate.module.css';
import { PageTemplate } from '../../components/page/PageTemplate';

export function ProfessionalClientsPage() {
  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Clienti"
      description="Questa pagina mostrerà in futuro i clienti collegati al professionista."
    >
      <aside
        className={pageTemplateStyles.panel}
        aria-labelledby="invites-access"
      >
        <h2 id="invites-access">Accesso secondario</h2>
        <p>
          Gli inviti restano disponibili senza occupare una voce primaria
          aggiuntiva per il personal trainer.
        </p>
        <Link to="/app/professional/invites">Vai all’area inviti</Link>
      </aside>
    </PageTemplate>
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

export function ProfessionalInvitesPage() {
  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Inviti"
      description="Questa pagina ospiterà la creazione e la consultazione degli inviti cliente. Non sono presenti azioni simulate."
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
