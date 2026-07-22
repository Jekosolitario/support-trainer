import { Link } from 'react-router-dom';

import { FaqSection } from '../../components/faq/FaqSection';
import { FutureFeature } from '../../components/future/FutureFeature';
import {
  advantagesContent,
  closingContent,
  faqContent,
  futureContent,
  heroContent,
  processContent,
  projectContent,
  rolesContent,
} from './homeContent';
import styles from './HomePage.module.css';

export function HomePage() {
  return (
    <div className={styles.home}>
      <section
        className={`${styles.section} ${styles.hero}`}
        aria-labelledby="home-title"
      >
        <p className={styles.eyebrow}>{heroContent.eyebrow}</p>
        <h1 id="home-title">{heroContent.title}</h1>
        <p className={styles.lead}>{heroContent.introduction}</p>
        <div className={styles.heroVisual} aria-hidden="true">
          <div className={`${styles.visualNode} ${styles.professionalNode}`}>
            <span className={styles.visualNodeDot} />
            <span>Professionista</span>
          </div>
          <div className={`${styles.visualNode} ${styles.clientNode}`}>
            <span className={styles.visualNodeDot} />
            <span>Cliente</span>
          </div>
          <div className={styles.visualConnection}>
            <span />
          </div>
          <div className={`${styles.visualPanel} ${styles.invitePanel}`}>
            <span>Invito</span>
            <strong>Attivo</strong>
          </div>
          <div className={`${styles.visualPanel} ${styles.availabilityPanel}`}>
            <span>Disponibilità</span>
            <span className={styles.visualStatus} />
          </div>
          <div className={`${styles.visualPanel} ${styles.bookingPanel}`}>
            <span>Richiesta</span>
            <strong>Ricevuta</strong>
          </div>
        </div>
        <div className={styles.actions}>
          <a className={styles.primaryAction} href="#come-funziona">
            {heroContent.primaryAction}
          </a>
          <Link className={styles.secondaryAction} to="/invite/validate">
            {heroContent.inviteAction}
          </Link>
          <Link className={styles.textAction} to="/register/professional">
            {heroContent.professionalAction}
          </Link>
        </div>
        <p className={styles.reassurance}>{heroContent.reassurance}</p>
      </section>

      <section
        className={styles.section}
        id="il-progetto"
        aria-labelledby="project-title"
      >
        <header className={styles.sectionHeader}>
          <p className={styles.eyebrow}>{projectContent.eyebrow}</p>
          <h2 id="project-title">{projectContent.title}</h2>
          <p>{projectContent.introduction}</p>
        </header>
        <div className={styles.cardGrid}>
          {projectContent.concepts.map((concept, index) => (
            <article className={styles.card} key={concept.title}>
              <div className={styles.moduleMeta} aria-hidden="true">
                <span>{String(index + 1).padStart(2, '0')}</span>
                <span className={styles.moduleIndicator} />
              </div>
              <h3>{concept.title}</h3>
              <p>{concept.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section
        className={styles.section}
        id="vantaggi"
        aria-labelledby="advantages-title"
      >
        <header className={styles.sectionHeader}>
          <p className={styles.eyebrow}>{advantagesContent.eyebrow}</p>
          <h2 id="advantages-title">{advantagesContent.title}</h2>
          <p>{advantagesContent.introduction}</p>
        </header>

        <div className={styles.comparisonGrid}>
          {advantagesContent.comparisons.map((comparison, index) => (
            <article
              className={styles.comparison}
              data-flow={index === 0 ? 'fragmented' : 'connected'}
              key={comparison.title}
            >
              <div className={styles.comparisonMeta} aria-hidden="true">
                <span>{String(index + 1).padStart(2, '0')}</span>
                <span className={styles.comparisonSignal} />
              </div>
              <h3>{comparison.title}</h3>
              <p>{comparison.description}</p>
              <ul className={styles.itemList}>
                {comparison.items.map((item) => (
                  <li key={item.title}>
                    <h4>{item.title}</h4>
                    <p>{item.description}</p>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>

        <div className={`${styles.cardGrid} ${styles.outcomeGrid}`}>
          {advantagesContent.benefits.map((benefit, index) => (
            <article
              className={`${styles.card} ${styles.outcomeCard}`}
              key={benefit.title}
            >
              <span className={styles.outcomeCode} aria-hidden="true">
                RISULTATO {String(index + 1).padStart(2, '0')}
              </span>
              <h3>{benefit.title}</h3>
              <p>{benefit.description}</p>
            </article>
          ))}
        </div>
        <p className={styles.conclusion}>
          <span className={styles.statusLabel} aria-hidden="true">
            STATO OPERATIVO
          </span>
          <span>{advantagesContent.conclusion}</span>
        </p>
      </section>

      <section
        className={styles.section}
        id="come-funziona"
        aria-labelledby="process-title"
      >
        <header className={styles.sectionHeader}>
          <p className={styles.eyebrow}>{processContent.eyebrow}</p>
          <h2 id="process-title">{processContent.title}</h2>
          <p>{processContent.introduction}</p>
          <p className={styles.reassurance}>{processContent.clarification}</p>
        </header>

        <div className={styles.pathGrid}>
          {processContent.paths.map((path, pathIndex) => (
            <article
              className={styles.path}
              data-path={
                pathIndex === 0
                  ? 'trainer'
                  : pathIndex === 1
                    ? 'nutritionist'
                    : 'client'
              }
              key={path.role}
            >
              <div className={styles.pathMeta}>
                <span className={styles.pathCode} aria-hidden="true">
                  {String(pathIndex + 1).padStart(2, '0')}
                </span>
                <p className={styles.roleLabel}>{path.role}</p>
                <span className={styles.pathSignal} aria-hidden="true" />
              </div>
              <h3>{path.title}</h3>
              <ol className={styles.steps}>
                {path.steps.map((step, stepIndex) => (
                  <li key={step.title}>
                    <span className={styles.stepIndex} aria-hidden="true">
                      {String(stepIndex + 1).padStart(2, '0')}
                    </span>
                    <h4>{step.title}</h4>
                    <p>{step.description}</p>
                  </li>
                ))}
              </ol>
              {'contextualAction' in path ? (
                <Link className={styles.inlineAction} to="/invite/validate">
                  {path.contextualAction}
                </Link>
              ) : null}
            </article>
          ))}
        </div>
      </section>

      <section
        className={styles.section}
        id="per-chi"
        aria-labelledby="roles-title"
      >
        <header className={styles.sectionHeader}>
          <p className={styles.eyebrow}>{rolesContent.eyebrow}</p>
          <h2 id="roles-title">{rolesContent.title}</h2>
          <p>{rolesContent.introduction}</p>
          <p className={styles.reassurance}>{rolesContent.commonPrinciple}</p>
        </header>

        <div className={styles.roleGrid}>
          {rolesContent.experiences.map((experience, index) => (
            <article
              className={styles.experience}
              data-role={
                index === 0
                  ? 'trainer'
                  : index === 1
                    ? 'nutritionist'
                    : 'client'
              }
              key={experience.label}
            >
              <div className={styles.experienceTopline}>
                <p className={styles.roleLabel}>{experience.label}</p>
                <span className={styles.availabilityBadge}>Disponibile</span>
              </div>
              <h3>{experience.title}</h3>
              <p>{experience.description}</p>
              <ul className={styles.itemList}>
                {experience.features.map((feature) => (
                  <li key={feature.title}>
                    <span
                      className={styles.featureIndicator}
                      aria-hidden="true"
                    />
                    <h4>{feature.title}</h4>
                    <p>{feature.description}</p>
                  </li>
                ))}
              </ul>
              <p className={styles.benefit}>{experience.benefit}</p>
              {'future' in experience ? (
                <p className={styles.futureNote}>{experience.future}</p>
              ) : null}
            </article>
          ))}
        </div>

        <section
          className={styles.availableToday}
          id="funzionalita"
          aria-labelledby="features-title"
        >
          <div className={styles.availableHeader}>
            <p className={styles.eyebrow}>
              {rolesContent.availableToday.eyebrow}
            </p>
            <span className={styles.availabilityBadge}>Disponibile</span>
          </div>
          <h3 id="features-title">{rolesContent.availableToday.title}</h3>
          <p>{rolesContent.availableToday.introduction}</p>
          <ul className={styles.simpleList}>
            {rolesContent.availableToday.items.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
      </section>

      <section
        className={styles.section}
        id="in-arrivo"
        aria-labelledby="future-title"
      >
        <header className={styles.sectionHeader}>
          <p className={styles.eyebrow}>{futureContent.eyebrow}</p>
          <h2 id="future-title">{futureContent.title}</h2>
          <p>{futureContent.introduction}</p>
          <p className={styles.reassurance}>{futureContent.availableSummary}</p>
        </header>

        <div className={`${styles.cardGrid} ${styles.futureGrid}`}>
          {futureContent.areas.map((area) => (
            <FutureFeature key={area.title} {...area} variant="home" />
          ))}
        </div>

        <div className={`${styles.cardGrid} ${styles.futurePrinciples}`}>
          {futureContent.reassurances.map((reassurance, index) => (
            <article
              className={`${styles.card} ${styles.futurePrinciple}`}
              key={reassurance.title}
            >
              <span className={styles.principleCode} aria-hidden="true">
                PRINCIPIO {String(index + 1).padStart(2, '0')}
              </span>
              <h3>{reassurance.title}</h3>
              <p>{reassurance.description}</p>
            </article>
          ))}
        </div>
      </section>

      <FaqSection {...faqContent} />

      <section
        className={`${styles.section} ${styles.closing}`}
        aria-labelledby="closing-title"
      >
        <div className={styles.closingCopy}>
          <p className={styles.eyebrow}>{closingContent.eyebrow}</p>
          <h2 id="closing-title">{closingContent.title}</h2>
          <p>{closingContent.introduction}</p>
        </div>
        <div className={styles.closingControls}>
          <div className={styles.closingSignals} aria-hidden="true">
            <span>Professionista</span>
            <span>Cliente</span>
            <span>Codice invito</span>
            <span>Accesso</span>
          </div>
          <div className={styles.actions}>
            <Link className={styles.primaryAction} to="/register/professional">
              {closingContent.primaryAction}
            </Link>
            <Link className={styles.secondaryAction} to="/invite/validate">
              {closingContent.inviteAction}
            </Link>
            <Link className={styles.textAction} to="/login">
              {closingContent.loginAction}
            </Link>
          </div>
        </div>
        <p className={styles.reassurance}>{closingContent.reassurance}</p>
      </section>
    </div>
  );
}
