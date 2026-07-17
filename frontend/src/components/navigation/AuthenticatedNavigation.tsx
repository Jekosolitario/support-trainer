import type { CSSProperties } from 'react';
import { NavLink } from 'react-router-dom';

import type { UserAccessProfile } from '../../app/config/access';
import styles from './AuthenticatedNavigation.module.css';
import { getNavigationItems } from './navigationConfig';

interface AuthenticatedNavigationProps {
  profile: UserAccessProfile;
}

export function AuthenticatedNavigation({
  profile,
}: AuthenticatedNavigationProps) {
  const items = getNavigationItems(profile);
  const navigationStyle = {
    '--navigation-count': items.length,
  } as CSSProperties;

  return (
    <nav className={styles.navigation} aria-label="Navigazione principale">
      <ul className={styles.list} style={navigationStyle}>
        {items.map((item) => (
          <li key={item.id}>
            <NavLink
              className={({ isActive }) =>
                `${styles.link} ${isActive ? styles.linkActive : ''}`
              }
              to={item.path}
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
