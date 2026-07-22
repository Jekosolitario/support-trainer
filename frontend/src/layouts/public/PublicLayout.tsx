import { NavLink, Outlet, useLocation } from 'react-router-dom';

import { Branding } from '../../components/branding/Branding';
import { HomeFooter } from '../../components/home/HomeFooter';
import styles from './PublicLayout.module.css';

const publicNavigation = [
  { label: 'Login', path: '/login' },
  { label: 'Registrazione professionista', path: '/register/professional' },
  { label: 'Invito cliente', path: '/invite/validate' },
];

export function PublicLayout() {
  const { pathname } = useLocation();

  return (
    <div className={styles.layout}>
      <a className={styles.skipLink} href="#main-content">
        Vai al contenuto
      </a>
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
      {pathname === '/' ? <HomeFooter /> : null}
    </div>
  );
}
