import { StrictMode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import {
  MemoryRouter,
  Outlet,
  Route,
  Routes,
  useNavigate,
} from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ClientOnboardingProvider } from './ClientOnboardingContext';
import { useClientOnboarding } from './clientOnboardingState';

function InviteProbe() {
  const { inviteCode, setValidatedInvite, clearInvite } = useClientOnboarding();

  return (
    <div>
      <output data-testid="invite-code">{inviteCode ?? 'null'}</output>
      <button
        type="button"
        onClick={() => {
          setValidatedInvite('  inv-abcdef1234  ');
        }}
      >
        set
      </button>
      <button
        type="button"
        onClick={() => {
          setValidatedInvite('INV-OTHER00001');
        }}
      >
        replace
      </button>
      <button type="button" onClick={clearInvite}>
        clear
      </button>
    </div>
  );
}

describe('ClientOnboardingProvider', () => {
  it('parte da null, set, replace e clear', () => {
    render(
      <ClientOnboardingProvider>
        <InviteProbe />
      </ClientOnboardingProvider>,
    );

    expect(screen.getByTestId('invite-code')).toHaveTextContent('null');

    fireEvent.click(screen.getByRole('button', { name: 'set' }));
    expect(screen.getByTestId('invite-code')).toHaveTextContent(
      'INV-ABCDEF1234',
    );

    fireEvent.click(screen.getByRole('button', { name: 'replace' }));
    expect(screen.getByTestId('invite-code')).toHaveTextContent(
      'INV-OTHER00001',
    );

    fireEvent.click(screen.getByRole('button', { name: 'clear' }));
    expect(screen.getByTestId('invite-code')).toHaveTextContent('null');
  });

  it('espone soltanto inviteCode nello state pubblico', () => {
    function KeysProbe() {
      const value = useClientOnboarding();
      return (
        <output data-testid="provider-keys">
          {Object.keys(value).sort().join(',')}
        </output>
      );
    }

    render(
      <ClientOnboardingProvider>
        <KeysProbe />
      </ClientOnboardingProvider>,
    );

    expect(screen.getByTestId('provider-keys')).toHaveTextContent(
      'clearInvite,inviteCode,setValidatedInvite',
    );
  });

  it('condivide invite tra validate e register e lo perde uscendo dal subtree', () => {
    function ValidateWithSet() {
      const { inviteCode, setValidatedInvite } = useClientOnboarding();
      const navigate = useNavigate();
      return (
        <div>
          <h1>validate</h1>
          <output data-testid="code">{inviteCode ?? 'null'}</output>
          <button
            type="button"
            onClick={() => {
              setValidatedInvite('INV-SHARED0001');
              navigate('/register/client');
            }}
          >
            continue
          </button>
        </div>
      );
    }

    function RegisterRead() {
      const { inviteCode } = useClientOnboarding();
      const navigate = useNavigate();
      return (
        <div>
          <h1>register</h1>
          <output data-testid="code">{inviteCode ?? 'null'}</output>
          <button type="button" onClick={() => navigate('/outside')}>
            leave
          </button>
        </div>
      );
    }

    function RemountValidate() {
      const navigate = useNavigate();
      return (
        <div>
          <h1>Outside</h1>
          <button type="button" onClick={() => navigate('/invite/validate')}>
            back-validate
          </button>
        </div>
      );
    }

    render(
      <MemoryRouter initialEntries={['/invite/validate']}>
        <Routes>
          <Route
            element={
              <ClientOnboardingProvider>
                <Outlet />
              </ClientOnboardingProvider>
            }
          >
            <Route path="/invite/validate" element={<ValidateWithSet />} />
            <Route path="/register/client" element={<RegisterRead />} />
          </Route>
          <Route path="/outside" element={<RemountValidate />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'continue' }));
    expect(screen.getByRole('heading', { name: 'register' })).toBeVisible();
    expect(screen.getByTestId('code')).toHaveTextContent('INV-SHARED0001');

    fireEvent.click(screen.getByRole('button', { name: 'leave' }));
    expect(screen.getByRole('heading', { name: 'Outside' })).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'back-validate' }));
    expect(screen.getByRole('heading', { name: 'validate' })).toBeVisible();
    expect(screen.getByTestId('code')).toHaveTextContent('null');
  });

  it('è StrictMode-safe: set sopravvive al double-invoke', () => {
    render(
      <StrictMode>
        <ClientOnboardingProvider>
          <InviteProbe />
        </ClientOnboardingProvider>
      </StrictMode>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'set' }));
    expect(screen.getByTestId('invite-code')).toHaveTextContent(
      'INV-ABCDEF1234',
    );
  });
});
