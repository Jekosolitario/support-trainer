import { Link } from 'react-router-dom';

import {
  footerAccessLinks,
  footerContent,
  footerExploreLinks,
} from '../../pages/public/homeContent';
import { Branding } from '../branding/Branding';
import styles from './HomeFooter.module.css';

export function HomeFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className={styles.footer}>
      <div className={styles.branding}>
        <Branding linkTo="/" />
        <p>{footerContent.description}</p>
      </div>

      <nav className={styles.linkGroup} aria-label="Accesso">
        <h2>Accesso</h2>
        <ul className={styles.navigation}>
          {footerAccessLinks.map((item) => (
            <li key={item.label}>
              <Link to={item.to}>{item.label}</Link>
            </li>
          ))}
        </ul>
      </nav>

      <nav className={styles.linkGroup} aria-label="Esplora la home">
        <h2>Esplora</h2>
        <ul className={styles.navigation}>
          {footerExploreLinks.map((item) => (
            <li key={item.label}>
              <a href={item.to}>{item.label}</a>
            </li>
          ))}
        </ul>
      </nav>

      <section className={styles.status} aria-labelledby="home-status-title">
        <h2 id="home-status-title">Stato</h2>
        <ul className={styles.plainList}>
          {footerContent.futureSupport.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section className={styles.legal} aria-labelledby="home-legal-title">
        <h2 id="home-legal-title">{footerContent.legal.title}</h2>
        <ul className={styles.plainList}>
          {footerContent.legal.items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <ul className={styles.statusBar} aria-label="Stato del progetto">
        <li>MVP disponibile</li>
        <li>Moduli in sviluppo</li>
        <li>Informazioni legali in preparazione</li>
      </ul>

      <p className={styles.copyright}>© {currentYear} Support Trainer</p>
    </footer>
  );
}
