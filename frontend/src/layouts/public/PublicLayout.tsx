import { NavLink, Outlet } from 'react-router-dom';

import { Branding } from '../../components/branding/Branding';
import styles from './PublicLayout.module.css';

const publicNavigation = [
  { label: 'Login', path: '/login' },
  { label: 'Registrazione professionista', path: '/register/professional' },
  { label: 'Invito cliente', path: '/invite/validate' },
];

export function PublicLayout() {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Branding linkTo="/" />
        <nav className={styles.navigation} aria-label="Navigazione pubblica">
          <ul>
            {publicNavigation.map((item) => (
              <li key={item.path}>
                <NavLink to={item.path}>{item.label}</NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <main className={styles.main} id="main-content">
        <Outlet />
      </main>
    </div>
  );
}
