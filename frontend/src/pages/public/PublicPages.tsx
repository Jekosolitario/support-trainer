import { PageTemplate } from '../../components/page/PageTemplate';

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
