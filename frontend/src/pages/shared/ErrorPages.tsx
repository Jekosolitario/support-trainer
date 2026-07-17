import { Link } from 'react-router-dom';

import pageTemplateStyles from '../../components/page/PageTemplate.module.css';
import { PageTemplate } from '../../components/page/PageTemplate';

export function ForbiddenPage() {
  return (
    <PageTemplate
      eyebrow="Accesso non consentito"
      title="Non puoi accedere a questa pagina"
      description="La risorsa richiesta non è disponibile per questo accesso."
    >
      <div className={pageTemplateStyles.panel}>
        <Link to="/">Torna alla home</Link>
      </div>
    </PageTemplate>
  );
}

export function NotFoundPage() {
  return (
    <PageTemplate
      eyebrow="Errore 404"
      title="Pagina non trovata"
      description="L’indirizzo richiesto non corrisponde a una pagina disponibile."
    >
      <div className={pageTemplateStyles.panel}>
        <Link to="/">Torna alla home</Link>
      </div>
    </PageTemplate>
  );
}
