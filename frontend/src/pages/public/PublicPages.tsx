import pageTemplateStyles from '../../components/page/PageTemplate.module.css';
import { PageTemplate } from '../../components/page/PageTemplate';

export function LoginPage() {
  return (
    <PageTemplate
      eyebrow="Accesso pubblico"
      title="Login"
      description="Questa pagina ospiterà l’accesso per clienti e professionisti. L’autenticazione non è implementata in questa fondazione."
    />
  );
}

export function RegisterProfessionalPage() {
  return (
    <PageTemplate
      eyebrow="Registrazione"
      title="Registrazione professionista"
      description="Struttura destinata alla futura registrazione di personal trainer e nutrizionisti, senza form o integrazione API in questo step."
    />
  );
}

export function ValidateInvitePage() {
  return (
    <PageTemplate
      eyebrow="Invito cliente"
      title="Validazione invito"
      description="La futura procedura verificherà il codice di invito prima di consentire l’accesso alla registrazione cliente."
    />
  );
}

export function RegisterClientPage() {
  return (
    <PageTemplate
      eyebrow="Registrazione"
      title="Registrazione cliente"
      description="Questa pagina sarà usata soltanto dopo la validazione di un invito. Il form completo non è ancora presente."
    />
  );
}

const verificationStates = [
  'Controlla la tua email',
  'Reinvio della verifica',
  'Token presente',
  'Verifica in corso',
  'Verifica riuscita',
  'Token invalido o scaduto',
  'Errore temporaneo',
];

export function VerifyEmailPage() {
  return (
    <PageTemplate
      eyebrow="Verifica email"
      title="Verifica dell’indirizzo email"
      description="La pagina è predisposta per gestire in un unico percorso i futuri stati della verifica. In questa fondazione non legge token e non chiama il backend."
    >
      <section
        className={pageTemplateStyles.panel}
        aria-labelledby="verification-states"
      >
        <h2 id="verification-states">Stati previsti</h2>
        <ul>
          {verificationStates.map((state) => (
            <li key={state}>{state}</li>
          ))}
        </ul>
      </section>
    </PageTemplate>
  );
}
