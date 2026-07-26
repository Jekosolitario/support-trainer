import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../../auth/authState';
import styles from './LogoutButton.module.css';

export function LogoutButton() {
  const { logout } = useAuth();
  const [pending, setPending] = useState(false);
  const logoutStartedRef = useRef(false);
  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  async function handleLogout(): Promise<void> {
    if (logoutStartedRef.current) {
      return;
    }

    logoutStartedRef.current = true;
    setPending(true);

    try {
      await logout();
      // AuthProvider owns the terminal AuthState; no local navigation or mutation.
    } catch {
      // Consume rejection; never surface technical details.
      if (!mountedRef.current) {
        return;
      }

      logoutStartedRef.current = false;
      setPending(false);
    }
  }

  return (
    <button
      type="button"
      className={styles.button}
      disabled={pending}
      aria-busy={pending || undefined}
      onClick={() => {
        void handleLogout();
      }}
    >
      Esci
    </button>
  );
}
