import { useEffect, type ReactNode } from 'react';
import { Outlet } from 'react-router-dom';

import {
  getAccessProfileLabel,
  type UserAccessProfile,
} from '../../app/config/access';
import { Branding } from '../../components/branding/Branding';
import { AuthenticatedNavigation } from '../../components/navigation/AuthenticatedNavigation';
import { MobileNavigationDrawer } from '../../components/navigation/MobileNavigationDrawer';
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
  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-st-authenticated', '');
    return () => {
      root.removeAttribute('data-st-authenticated');
    };
  }, []);

  return (
    <div className={styles.layout}>
      <a className={styles.skipLink} href="#main-content">
        Vai al contenuto
      </a>

      <aside className={styles.sidebar} aria-label="Area riservata">
        <div className={styles.sidebarBrand}>
          <Branding />
        </div>
        <div className={styles.sidebarNavigation}>
          <AuthenticatedNavigation profile={profile} />
        </div>
        <div className={styles.sidebarFooter}>
          <p className={styles.area}>{getAccessProfileLabel(profile)}</p>
          {headerActions}
        </div>
      </aside>

      <header className={styles.mobileHeader}>
        <Branding />
        <MobileNavigationDrawer profile={profile} footer={headerActions} />
      </header>

      <main className={styles.main} id="main-content">
        {children ?? <Outlet />}
      </main>
    </div>
  );
}
