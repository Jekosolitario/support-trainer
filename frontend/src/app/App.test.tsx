import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';

import { App } from './App';
import { RequireAuth } from '../auth/RequireAuth';
import { RequireRole } from '../auth/RequireRole';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
  renderWithAuthContext,
} from '../test/renderWithAuthContext';

function navigateBrowser(url: string): void {
  window.history.pushState({}, '', url);
}

describe('App BrowserRouter useTransitions={false}', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    navigateBrowser('/');
  });

  it('monta App e raggiunge /login tramite Link pubblico', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 401 }),
    );

    navigateBrowser('/');
    render(<App />);

    const loginLinks = await screen.findAllByRole('link', {
      name: /Accedi|Login|login/i,
    });
    await user.click(loginLinks[0]);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/login');
    });
    expect(
      await screen.findByRole('heading', { name: 'Login' }),
    ).toBeInTheDocument();
  });

  it('RequireAuth redirige anonymous a /login', async () => {
    renderWithAuthContext(
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/app/client/dashboard" element={<p>Private</p>} />
        </Route>
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>,
      createAuthContextValue(createUnauthenticatedAuthState()),
      { initialEntries: ['/app/client/dashboard'] },
    );

    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('RequireRole redirige ruolo non autorizzato a /forbidden', async () => {
    renderWithAuthContext(
      <Routes>
        <Route element={<RequireRole role="PROFESSIONAL" />}>
          <Route path="/app/professional/dashboard" element={<p>Pro</p>} />
        </Route>
        <Route path="/forbidden" element={<p>Forbidden</p>} />
      </Routes>,
      createAuthContextValue(
        createAuthenticatedAuthState({
          role: 'CLIENT',
          specialization: null,
        }),
      ),
      { initialEntries: ['/app/professional/dashboard'] },
    );

    expect(await screen.findByText('Forbidden')).toBeInTheDocument();
    expect(screen.queryByText('Pro')).not.toBeInTheDocument();
  });

  it('navigazione MemoryRouter Link resta funzionante', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <Link to="/login">Vai login</Link>
        <Routes>
          <Route path="/" element={<p>Home</p>} />
          <Route path="/login" element={<p>Login target</p>} />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: 'Vai login' }));
    expect(await screen.findByText('Login target')).toBeInTheDocument();
  });
});
