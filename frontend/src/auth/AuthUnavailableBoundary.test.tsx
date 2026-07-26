import { useState } from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';

import {
  createAuthContextValue,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';
import { AuthUnavailableBoundary } from './AuthUnavailableBoundary';
import {
  AuthContext,
  type AuthContextValue,
  type AuthState,
} from './authState';
import { RequireAuth } from './RequireAuth';

const unavailableState: AuthState = {
  status: 'unavailable',
  operation: null,
  reason: 'bootstrap-failed',
  account: null,
  profile: null,
  accessProfile: null,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });

  return { promise, resolve };
}

afterEach(() => {
  vi.restoreAllMocks();
});

it('invoca una sola reconciliation per interazioni sincrone duplicate', () => {
  const reconciliationGate = deferred<void>();
  const reconcileSession = vi.fn(() => reconciliationGate.promise);

  renderWithAuthContext(
    <AuthUnavailableBoundary />,
    createAuthContextValue(unavailableState, { reconcileSession }),
  );

  const retry = screen.getByRole('button', { name: 'Riprova' });
  fireEvent.click(retry);
  fireEvent.click(retry);

  expect(reconcileSession).toHaveBeenCalledTimes(1);
  expect(screen.queryByText(/user@example.com/i)).not.toBeInTheDocument();
});

it('può smontarsi sul passaggio globale a initializing senza continuazioni locali', async () => {
  const reconciliationGate = deferred<void>();
  const reconcileSession = vi.fn(() => reconciliationGate.promise);
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

  function ReconciliationHarness() {
    const [state, setState] = useState<AuthState>(unavailableState);
    const value: AuthContextValue = createAuthContextValue(state, {
      reconcileSession: () => {
        setState({
          status: 'initializing',
          operation: 'reconciliation',
          reason: null,
          account: null,
          profile: null,
          accessProfile: null,
        });
        return reconcileSession();
      },
    });

    return (
      <AuthContext.Provider value={value}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/private" element={<p>Contenuto privato</p>} />
          </Route>
          <Route path="/login" element={<p>Login destination</p>} />
        </Routes>
      </AuthContext.Provider>
    );
  }

  render(
    <MemoryRouter initialEntries={['/private']}>
      <ReconciliationHarness />
    </MemoryRouter>,
  );

  fireEvent.click(screen.getByRole('button', { name: 'Riprova' }));

  expect(reconcileSession).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('status')).toBeVisible();
  expect(
    screen.queryByRole('heading', { name: 'Sessione non verificabile' }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
  expect(screen.queryByText('Login destination')).not.toBeInTheDocument();

  await act(async () => {
    reconciliationGate.resolve();
    await reconciliationGate.promise;
  });
  expect(consoleError).not.toHaveBeenCalled();
});

it('assorbe un rigetto senza redirect o interpretazione anonymous', async () => {
  const reconcileSession = vi.fn().mockRejectedValue(new Error('offline'));

  renderWithAuthContext(
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/private" element={<p>Contenuto privato</p>} />
      </Route>
      <Route path="/login" element={<p>Login destination</p>} />
    </Routes>,
    createAuthContextValue(unavailableState, { reconcileSession }),
    { initialEntries: ['/private'] },
  );

  fireEvent.click(screen.getByRole('button', { name: 'Riprova' }));
  await waitFor(() => {
    expect(reconcileSession).toHaveBeenCalledTimes(1);
  });

  expect(
    screen.getByRole('heading', { name: 'Sessione non verificabile' }),
  ).toBeVisible();
  expect(screen.queryByText('Contenuto privato')).not.toBeInTheDocument();
  expect(screen.queryByText('Login destination')).not.toBeInTheDocument();
});
