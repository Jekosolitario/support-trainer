import { Link } from 'react-router-dom';

import styles from './HomePage.module.css';

export function HomePage() {
  return (
    <section className={styles.home}>
      <h1>Support Trainer</h1>
      <p>L’interfaccia pubblica è in fase di progettazione.</p>
      <ul className={styles.links}>
        <li>
          <Link to="/login">Vai al login</Link>
        </li>
        <li>
          <Link to="/register/professional">Registrazione professionista</Link>
        </li>
        <li>
          <Link to="/invite/validate">Valida un invito cliente</Link>
        </li>
      </ul>
    </section>
  );
}
