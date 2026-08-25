import { NavLink } from 'react-router-dom';

import type { UserAccessProfile } from '../../app/config/access';
import styles from './AuthenticatedNavigation.module.css';
import { getNavigationItems } from './navigationConfig';

interface AuthenticatedNavigationProps {
  readonly profile: UserAccessProfile;
  readonly onNavigate?: () => void;
}

export function AuthenticatedNavigation({
  profile,
  onNavigate,
}: AuthenticatedNavigationProps) {
  const items = getNavigationItems(profile);

  return (
    <nav className={styles.navigation} aria-label="Navigazione principale">
      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item.id}>
            <NavLink
              className={({ isActive }) =>
                `${styles.link} ${isActive ? styles.linkActive : ''}`
              }
              to={item.path}
              onClick={onNavigate}
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
