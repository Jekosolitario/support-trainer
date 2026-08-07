import { createContext, useContext } from 'react';

export interface ClientOnboardingContextValue {
  readonly inviteCode: string | null;
  setValidatedInvite(code: string): void;
  clearInvite(): void;
}

export const ClientOnboardingContext =
  createContext<ClientOnboardingContextValue | null>(null);

export function useClientOnboarding(): ClientOnboardingContextValue {
  const value = useContext(ClientOnboardingContext);

  if (value === null) {
    throw new Error(
      'useClientOnboarding must be used within ClientOnboardingProvider',
    );
  }

  return value;
}
