import pageTemplateStyles from '../../components/page/PageTemplate.module.css';
import { PageTemplate } from '../../components/page/PageTemplate';

interface ProfilePageProps {
  area: 'cliente' | 'professionista';
}

export function ProfilePage({ area }: ProfilePageProps) {
  return (
    <PageTemplate
      eyebrow={`Area ${area}`}
      title="Profilo"
      description="Un unico percorso raccoglierà le responsabilità relative al profilo personale, all’account e allo stato operativo."
    >
      <section className={pageTemplateStyles.panel}>
        <h2>Profilo</h2>
        <p>Dati personali e informazioni pertinenti al tipo di profilo.</p>
      </section>
      <section className={pageTemplateStyles.panel}>
        <h2>Account</h2>
        <p>
          Informazioni dell’account previste senza introdurre una rotta
          separata.
        </p>
      </section>
      <section className={pageTemplateStyles.panel}>
        <h2>Stato operativo</h2>
        <p>Area concettuale per il futuro stato operativo del profilo.</p>
      </section>
    </PageTemplate>
  );
}
