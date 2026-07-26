import { useRef } from 'react';

import { useAuth } from './authState';

export function AuthUnavailableBoundary() {
  const { reconcileSession } = useAuth();
  const retryStartedRef = useRef(false);

  function handleRetry(): void {
    if (retryStartedRef.current) {
      return;
    }

    retryStartedRef.current = true;
    void reconcileSession().catch(() => {
      retryStartedRef.current = false;
    });
  }

  return (
    <section aria-labelledby="auth-unavailable-title">
      <h1 id="auth-unavailable-title">Sessione non verificabile</h1>
      <p>Non è possibile verificare la sessione in questo momento.</p>
      <button type="button" onClick={handleRetry}>
        Riprova
      </button>
    </section>
  );
}
