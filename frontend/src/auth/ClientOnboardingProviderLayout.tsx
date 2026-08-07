import { ClientOnboardingAuthGate } from './ClientOnboardingAuthGate';
import { ClientOnboardingProvider } from './ClientOnboardingContext';

/**
 * Pathless layout that scopes in-memory invite state and the local auth gate
 * to `/invite/validate` and `/register/client`.
 */
export function ClientOnboardingProviderLayout() {
  return (
    <ClientOnboardingProvider>
      <ClientOnboardingAuthGate />
    </ClientOnboardingProvider>
  );
}
