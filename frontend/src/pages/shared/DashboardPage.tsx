import {
  assertUnreachable,
  type UserAccessProfile,
} from '../../app/config/access';
import { FutureFeature } from '../../components/future/FutureFeature';
import { PageTemplate } from '../../components/page/PageTemplate';
import { ActionLink } from '../../components/ui/ActionLink';
import { Card } from '../../components/ui/Card';
import styles from './DashboardPage.module.css';

interface DashboardPageProps {
  profile: UserAccessProfile;
}

interface DashboardLink {
  label: string;
  path: string;
  description: string;
}

interface FutureModule {
  title: string;
  description: string;
}

function getDashboardLinks(profile: UserAccessProfile): DashboardLink[] {
  if (profile.role === 'CLIENT') {
    return [
      {
        label: 'Professionisti collegati',
        path: '/app/client/professionals',
        description:
          'Apri l’elenco dei professionisti collegati al tuo account.',
      },
      {
        label: 'Prenotazioni',
        path: '/app/client/bookings',
        description: 'Apri l’area delle prenotazioni.',
      },
      {
        label: 'Profilo',
        path: '/app/client/profile',
        description: 'Apri i dati del profilo.',
      },
    ];
  }

  const commonLinks = [
    {
      label: 'Clienti collegati',
      path: '/app/professional/clients',
      description: 'Apri l’elenco dei clienti collegati al tuo account.',
    },
    {
      label: 'Inviti',
      path: '/app/professional/invites',
      description: 'Apri l’area degli inviti.',
    },
    {
      label: 'Profilo',
      path: '/app/professional/profile',
      description: 'Apri i dati del profilo.',
    },
  ];

  const specialization = profile.specialization;

  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return [
        commonLinks[0],
        {
          label: 'Disponibilità',
          path: '/app/professional/availability',
          description: 'Apri l’area della disponibilità.',
        },
        {
          label: 'Prenotazioni',
          path: '/app/professional/bookings',
          description: 'Apri l’area delle prenotazioni.',
        },
        commonLinks[1],
        commonLinks[2],
      ];
    case 'NUTRITIONIST':
      return commonLinks;
    default:
      return assertUnreachable(specialization);
  }
}

function getFutureModules(profile: UserAccessProfile): FutureModule[] {
  if (profile.role === 'CLIENT') {
    return [
      {
        title: 'Workout',
        description: 'Programmi di allenamento previsti per uno step futuro.',
      },
      {
        title: 'Nutrition',
        description: 'Piani nutrizionali previsti per uno step futuro.',
      },
      {
        title: 'Progressi e misurazioni',
        description: 'Monitoraggio dei progressi previsto per uno step futuro.',
      },
    ];
  }

  const specialization = profile.specialization;

  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return [
        {
          title: 'Workout',
          description:
            'Gestione dei programmi di allenamento prevista in futuro.',
        },
      ];
    case 'NUTRITIONIST':
      return [
        {
          title: 'Nutrition',
          description: 'Gestione dei piani nutrizionali prevista in futuro.',
        },
      ];
    default:
      return assertUnreachable(specialization);
  }
}

export function DashboardPage({ profile }: DashboardPageProps) {
  const dashboardLinks = getDashboardLinks(profile);
  const futureModules = getFutureModules(profile);

  return (
    <PageTemplate
      appearance="authenticated"
      eyebrow="Area applicativa"
      title="Dashboard"
      description="Riepilogo strutturale delle aree previste. Non sono visualizzati dati provenienti dal backend."
    >
      <div className={styles.stack}>
        <section className={styles.section} aria-labelledby="operational-areas">
          <h2 className={styles.sectionTitle} id="operational-areas">
            Funzioni operative previste
          </h2>
          <ul className={styles.operationalList}>
            {dashboardLinks.map((item, index) => (
              <li key={item.path}>
                <Card
                  className={styles.operationalCard}
                  variant={index === 0 ? 'highlighted' : 'interactive'}
                >
                  <div className={styles.cardCopy}>
                    <h3 className={styles.cardTitle}>{item.label}</h3>
                    <p className={styles.cardDescription}>{item.description}</p>
                  </div>
                  <ActionLink
                    className={styles.actionLink}
                    to={item.path}
                    variant={index === 0 ? 'primary' : 'secondary'}
                  >
                    {item.label}
                  </ActionLink>
                </Card>
              </li>
            ))}
          </ul>
        </section>

        {futureModules.length > 0 ? (
          <section className={styles.section} aria-labelledby="future-modules">
            <h2 className={styles.futureTitle} id="future-modules">
              Moduli futuri
            </h2>
            <ul className={styles.futureList}>
              {futureModules.map((module) => (
                <li className={styles.futureItem} key={module.title}>
                  <FutureFeature {...module} />
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </div>
    </PageTemplate>
  );
}
