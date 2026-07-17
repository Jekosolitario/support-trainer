import { Outlet } from 'react-router-dom';

import { Branding } from '../../components/branding/Branding';
import styles from './ErrorLayout.module.css';

export function ErrorLayout() {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Branding linkTo="/" />
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
