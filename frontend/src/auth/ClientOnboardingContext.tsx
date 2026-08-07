import { useCallback, useMemo, useState, type ReactNode } from 'react';

import {
  ClientOnboardingContext,
  type ClientOnboardingContextValue,
} from './clientOnboardingState';
import { canonicalizeInviteCode } from './inviteCode';

export function ClientOnboardingProvider({
  children,
}: {
  readonly children: ReactNode;
}) {
  const [inviteCode, setInviteCode] = useState<string | null>(null);

  const setValidatedInvite = useCallback((code: string) => {
    setInviteCode(canonicalizeInviteCode(code));
  }, []);

  const clearInvite = useCallback(() => {
    setInviteCode(null);
  }, []);

  const value = useMemo(
    (): ClientOnboardingContextValue => ({
      inviteCode,
      setValidatedInvite,
      clearInvite,
    }),
    [inviteCode, setValidatedInvite, clearInvite],
  );

  return (
    <ClientOnboardingContext.Provider value={value}>
      {children}
    </ClientOnboardingContext.Provider>
  );
}
