import { Link } from 'react-router-dom';

import {
  assertUnreachable,
  type UserAccessProfile,
} from '../../app/config/access';
import { FutureFeature } from '../../components/future/FutureFeature';
import { PageTemplate } from '../../components/page/PageTemplate';
import styles from './DashboardPage.module.css';

interface DashboardPageProps {
  profile: UserAccessProfile;
}

interface DashboardLink {
  label: string;
  path: string;
}

interface FutureModule {
  title: string;
  description: string;
}

function getDashboardLinks(profile: UserAccessProfile): DashboardLink[] {
  if (profile.role === 'CLIENT') {
    return [
      { label: 'Professionisti collegati', path: '/app/client/professionals' },
      { label: 'Prenotazioni', path: '/app/client/bookings' },
      { label: 'Profilo', path: '/app/client/profile' },
    ];
  }

  const commonLinks = [
    { label: 'Clienti collegati', path: '/app/professional/clients' },
    { label: 'Inviti', path: '/app/professional/invites' },
    { label: 'Profilo', path: '/app/professional/profile' },
  ];

  const specialization = profile.specialization;

  switch (specialization) {
    case 'PERSONAL_TRAINER':
      return [
        commonLinks[0],
        { label: 'Disponibilità', path: '/app/professional/availability' },
        { label: 'Prenotazioni', path: '/app/professional/bookings' },
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
      eyebrow="Area applicativa"
      title="Dashboard"
      description="Riepilogo strutturale delle aree previste. Non sono visualizzati dati provenienti dal backend."
    >
      <section className={styles.grid} aria-labelledby="operational-areas">
        <h2 className={styles.sectionTitle} id="operational-areas">
          Funzioni operative previste
        </h2>
        <ul className={styles.operationalList}>
          {dashboardLinks.map((item) => (
            <li key={item.path}>
              <Link className={styles.operationalLink} to={item.path}>
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {futureModules.length > 0 ? (
        <section className={styles.grid} aria-labelledby="future-modules">
          <h2 className={styles.sectionTitle} id="future-modules">
            Moduli futuri
          </h2>
          <ul className={styles.futureList}>
            {futureModules.map((module) => (
              <li key={module.title}>
                <FutureFeature {...module} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </PageTemplate>
  );
}
