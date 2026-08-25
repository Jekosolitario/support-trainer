import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

import {
  getAccessProfileLabel,
  type UserAccessProfile,
} from '../../app/config/access';
import { AuthenticatedNavigation } from './AuthenticatedNavigation';
import styles from './MobileNavigationDrawer.module.css';

const DESKTOP_MEDIA_QUERY = '(min-width: 48rem)';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

interface MobileNavigationDrawerProps {
  readonly profile: UserAccessProfile;
  readonly footer?: ReactNode;
}

export function MobileNavigationDrawer({
  profile,
  footer,
}: MobileNavigationDrawerProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isDesktop, setIsDesktop] = useState(
    () => window.matchMedia(DESKTOP_MEDIA_QUERY).matches,
  );
  const drawerId = useId();
  const titleId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const drawerRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const isModalActive = isOpen && !isDesktop;

  useEffect(() => {
    const mediaQuery = window.matchMedia(DESKTOP_MEDIA_QUERY);

    function handleChange(event: MediaQueryListEvent): void {
      setIsDesktop(event.matches);
      if (event.matches) {
        setIsOpen(false);
      }
    }

    mediaQuery.addEventListener('change', handleChange);

    return () => {
      mediaQuery.removeEventListener('change', handleChange);
    };
  }, []);

  useEffect(() => {
    if (!isModalActive) {
      return;
    }

    const trigger = triggerRef.current;
    const previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        event.preventDefault();
        setIsOpen(false);
        return;
      }

      if (event.key !== 'Tab') {
        return;
      }

      const drawer = drawerRef.current;
      const focusableElements = drawer
        ? Array.from(drawer.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
        : [];

      if (focusableElements.length === 0) {
        event.preventDefault();
        drawer?.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const activeElement = document.activeElement;
      const focusIsInside = activeElement
        ? drawer?.contains(activeElement)
        : false;

      if (
        event.shiftKey &&
        (!focusIsInside || activeElement === firstElement)
      ) {
        event.preventDefault();
        lastElement.focus();
      } else if (
        !event.shiftKey &&
        (!focusIsInside || activeElement === lastElement)
      ) {
        event.preventDefault();
        firstElement.focus();
      }
    }

    function handleFocusIn(event: FocusEvent): void {
      const drawer = drawerRef.current;
      const target = event.target;

      if (drawer && target instanceof Node && !drawer.contains(target)) {
        (closeButtonRef.current ?? drawer).focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('focusin', handleFocusIn);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('focusin', handleFocusIn);
      document.body.style.overflow = previousBodyOverflow;

      if (
        trigger?.isConnected &&
        !window.matchMedia(DESKTOP_MEDIA_QUERY).matches
      ) {
        trigger.focus();
      }
    };
  }, [isModalActive]);

  const layerClassName = [styles.layer, isModalActive && styles.layerOpen]
    .filter(Boolean)
    .join(' ');

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={styles.trigger}
        aria-expanded={isModalActive}
        aria-controls={drawerId}
        onClick={() => {
          if (!isDesktop) {
            setIsOpen(true);
          }
        }}
      >
        <span className={styles.menuIcon} aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
        Menu
      </button>

      <div
        id={drawerId}
        className={layerClassName}
        role="dialog"
        aria-modal={isModalActive || undefined}
        aria-labelledby={titleId}
        aria-hidden={!isModalActive}
        inert={!isModalActive}
      >
        <button
          className={styles.overlay}
          type="button"
          tabIndex={-1}
          aria-label="Chiudi il menu di navigazione"
          disabled={!isModalActive}
          onClick={() => {
            setIsOpen(false);
          }}
        />

        <div className={styles.drawer} ref={drawerRef} tabIndex={-1}>
          <div className={styles.drawerHeader}>
            <div>
              <p className={styles.eyebrow}>Area riservata</p>
              <h2 className={styles.title} id={titleId}>
                Navigazione
              </h2>
            </div>
            <button
              ref={closeButtonRef}
              type="button"
              className={styles.closeButton}
              aria-label="Chiudi menu"
              onClick={() => {
                setIsOpen(false);
              }}
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>

          <div className={styles.navigationArea}>
            <AuthenticatedNavigation
              profile={profile}
              onNavigate={() => {
                setIsOpen(false);
              }}
            />
          </div>

          <div className={styles.footer}>
            <p className={styles.profileLabel}>
              Profilo attivo: {getAccessProfileLabel(profile)}
            </p>
            {isModalActive ? footer : null}
          </div>
        </div>
      </div>
    </>
  );
}
