import type { ReactNode } from 'react';
import { Outlet } from 'react-router-dom';

import {
  getAccessProfileLabel,
  type UserAccessProfile,
} from '../../app/config/access';
import { Branding } from '../../components/branding/Branding';
import { AuthenticatedNavigation } from '../../components/navigation/AuthenticatedNavigation';
import styles from './AuthenticatedLayout.module.css';

interface AuthenticatedLayoutProps {
  profile: UserAccessProfile;
  headerActions?: ReactNode;
  children?: ReactNode;
}

export function AuthenticatedLayout({
  profile,
  headerActions,
  children,
}: AuthenticatedLayoutProps) {
  return (
    <div className={styles.layout}>
      <a className={styles.skipLink} href="#main-content">
        Vai al contenuto
      </a>
      <header className={styles.header}>
        <Branding />
        <div className={styles.headerEnd}>
          <p className={styles.area}>{getAccessProfileLabel(profile)}</p>
          {headerActions}
        </div>
      </header>
      <div className={styles.body}>
        <div className={styles.navigationSlot}>
          <AuthenticatedNavigation profile={profile} />
        </div>
        <main className={styles.main} id="main-content">
          {children ?? <Outlet />}
        </main>
      </div>
    </div>
  );
}
