import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import {
  BrowserRouter,
  MemoryRouter,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import { clearCsrf } from '../../api/csrf';
import {
  HttpApiError,
  NetworkError,
  type ErrorResponse,
} from '../../api/types';
import { ResetPasswordPage } from './ResetPasswordPage';

const TOKEN = 'raw-reset-token-value';
const INVALID_COPY = 'Questo link non è valido o non è più utilizzabile.';

function apiError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-31T10:00:00Z',
    status,
    code,
    message: 'hidden',
    path: '/api/v1/auth/password-recovery/confirm',
  };

  return new HttpApiError(
    status,
    body,
    new Response(JSON.stringify(body), { status }),
  );
}

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

function csrfResponse(): Response {
  return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
}

function structuredErrorResponse(
  status: number,
  code: string,
  path: string,
): Response {
  const body: ErrorResponse = {
    timestamp: '2026-08-31T10:00:00Z',
    status,
    code,
    message: code,
    path,
  };
  return jsonResponse(body, { status });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="router-location">
      {JSON.stringify({
        pathname: location.pathname,
        search: location.search,
        hash: location.hash,
        state: location.state ?? null,
      })}
    </output>
  );
}

function renderReset(hash: string) {
  window.history.replaceState(null, '', `/reset-password${hash}`);

  return render(
    <StrictMode>
      <BrowserRouter useTransitions={false}>
        <Routes>
          <Route
            path="/reset-password"
            element={
              <>
                <ResetPasswordPage />
                <LocationProbe />
              </>
            }
          />
          <Route
            path="/forgot-password"
            element={<h1>Password dimenticata</h1>}
          />
          <Route path="/login" element={<h1>Login</h1>} />
        </Routes>
      </BrowserRouter>
    </StrictMode>,
  );
}

function readRouterLocation(): {
  pathname: string;
  search: string;
  hash: string;
  state: unknown;
} {
  return JSON.parse(
    screen.getByTestId('router-location').textContent ?? '{}',
  ) as {
    pathname: string;
    search: string;
    hash: string;
    state: unknown;
  };
}

function serializeForTokenScan(value: unknown): string {
  const seen = new WeakSet<object>();
  try {
    return JSON.stringify(value, (_key, nested: unknown) => {
      if (typeof nested === 'object' && nested !== null) {
        if (seen.has(nested)) {
          return '[Circular]';
        }
        seen.add(nested);
      }
      return nested;
    });
  } catch {
    return String(value);
  }
}

function containsToken(value: unknown, token: string): boolean {
  return serializeForTokenScan(value).includes(token);
}

function storageHasToken(): boolean {
  const haystack = `${JSON.stringify(window.localStorage)} ${JSON.stringify(window.sessionStorage)}`;
  return haystack.includes(TOKEN);
}

function expectSanitizedNavigationSurfaces(): void {
  const routerLocation = readRouterLocation();
  expect(routerLocation.pathname).toBe('/reset-password');
  expect(routerLocation.search).toBe('');
  expect(routerLocation.hash).toBe('');
  expect(window.location.pathname).toBe('/reset-password');
  expect(window.location.search).toBe('');
  expect(window.location.hash).toBe('');
  expect(containsToken(routerLocation, TOKEN)).toBe(false);
  expect(containsToken(routerLocation.state, TOKEN)).toBe(false);
  expect(containsToken(window.history.state, TOKEN)).toBe(false);
  expect(storageHasToken()).toBe(false);
  expect(window.localStorage.length).toBe(0);
  expect(window.sessionStorage.length).toBe(0);
}

describe('ResetPasswordPage', () => {
  const originalFetch = globalThis.fetch;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    confirmSpy = vi.spyOn(authOnboardingApi, 'confirmPasswordRecovery');
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
    clearCsrf();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
    window.history.replaceState(null, '', '/');
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('mostra missing token senza hash e non chiama il backend', async () => {
    renderReset('');

    expect(
      await screen.findByText('Questo link non è valido.'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Richiedi un nuovo link' }),
    ).toHaveAttribute('href', '/forgot-password');
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('porta il focus sul heading in missing-token', async () => {
    renderReset('');

    const heading = await screen.findByRole('heading', {
      name: 'Reimposta la password',
    });
    await waitFor(() => {
      expect(document.activeElement).toBe(heading);
    });
    expect(heading).toHaveAttribute('tabindex', '-1');
  });

  it('porta il focus sul heading con fragment token vuoto', async () => {
    renderReset('#token=');

    const heading = await screen.findByRole('heading', {
      name: 'Reimposta la password',
    });
    expect(
      await screen.findByText('Questo link non è valido.'),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(document.activeElement).toBe(heading);
    });
  });

  it('sanitizza l’URL, tiene il token in memoria e non lo persiste', async () => {
    renderReset(`#token=${TOKEN}`);

    expect(await screen.findByLabelText('Nuova password')).toBeInTheDocument();
    await waitFor(() => {
      expectSanitizedNavigationSurfaces();
    });
    expect(readRouterLocation().state).toBeNull();
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('dopo refresh senza fragment entra in missing token con focus', async () => {
    const first = renderReset(`#token=${TOKEN}`);
    expect(await screen.findByLabelText('Nuova password')).toBeInTheDocument();
    first.unmount();

    renderReset('');
    const heading = await screen.findByRole('heading', {
      name: 'Reimposta la password',
    });
    expect(
      await screen.findByText('Questo link non è valido.'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Nuova password')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(document.activeElement).toBe(heading);
    });
  });

  it('rifiuta password debole e mismatch senza chiamare l’API', async () => {
    const user = userEvent.setup();
    renderReset(`#token=${TOKEN}`);
    await screen.findByLabelText('Nuova password');

    await user.type(screen.getByLabelText('Nuova password'), 'weak');
    await user.type(screen.getByLabelText('Conferma nuova password'), 'other');
    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));

    expect(
      await screen.findByText('La password deve contenere almeno 8 caratteri.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Le password non coincidono.')).toBeInTheDocument();
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('invia solo token e newPassword e blocca il doppio submit', async () => {
    const user = userEvent.setup();
    const gate = deferred<void>();
    confirmSpy.mockReturnValue(gate.promise);
    renderReset(`#token=${TOKEN}`);
    await screen.findByLabelText('Nuova password');

    await user.type(screen.getByLabelText('Nuova password'), 'Password1!');
    await user.type(
      screen.getByLabelText('Conferma nuova password'),
      'Password1!',
    );
    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));
    await user.click(
      screen.getByRole('button', { name: 'Aggiornamento in corso' }),
    );

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(confirmSpy).toHaveBeenCalledWith({
      token: TOKEN,
      newPassword: 'Password1!',
    });
    expect(JSON.stringify(confirmSpy.mock.calls[0]?.[0])).not.toContain(
      'confirmPassword',
    );

    gate.resolve();
    expect(
      await screen.findByRole('heading', { name: 'Password aggiornata' }),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: 'Accedi' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(screen.queryByLabelText('Nuova password')).not.toBeInTheDocument();
    expectSanitizedNavigationSurfaces();
  });

  it('mostra uno stato unico per token backend invalido', async () => {
    const user = userEvent.setup();
    confirmSpy.mockRejectedValue(
      apiError(400, 'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED'),
    );
    renderReset(`#token=${TOKEN}`);
    await screen.findByLabelText('Nuova password');

    await user.type(screen.getByLabelText('Nuova password'), 'Password1!');
    await user.type(
      screen.getByLabelText('Conferma nuova password'),
      'Password1!',
    );
    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));

    expect(await screen.findByText(INVALID_COPY)).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Richiedi un nuovo link' }),
    ).toBeVisible();
  });

  it('su unexpected 2xx strutturato mostra errore tecnico, non success né token invalid', async () => {
    const user = userEvent.setup();
    confirmSpy.mockRestore();
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        structuredErrorResponse(
          200,
          'PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED',
          '/api/v1/auth/password-recovery/confirm',
        ),
      );
    renderReset(`#token=${TOKEN}`);
    await screen.findByLabelText('Nuova password');

    await user.type(screen.getByLabelText('Nuova password'), 'Password1!');
    await user.type(
      screen.getByLabelText('Conferma nuova password'),
      'Password1!',
    );
    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));

    expect(
      await screen.findByText(
        'Non è stato possibile aggiornare la password. Riprova.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Password aggiornata' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(INVALID_COPY)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Nuova password')).toHaveValue('Password1!');
  });

  it('su errore tecnico mantiene i campi e consente retry', async () => {
    const user = userEvent.setup();
    confirmSpy
      .mockRejectedValueOnce(new NetworkError('down'))
      .mockResolvedValueOnce(undefined);
    renderReset(`#token=${TOKEN}`);
    await screen.findByLabelText('Nuova password');

    await user.type(screen.getByLabelText('Nuova password'), 'Password1!');
    await user.type(
      screen.getByLabelText('Conferma nuova password'),
      'Password1!',
    );
    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));

    expect(
      await screen.findByText(
        'Non è stato possibile aggiornare la password. Riprova.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Nuova password')).toHaveValue('Password1!');
    expect(screen.getByLabelText('Conferma nuova password')).toHaveValue(
      'Password1!',
    );

    await user.click(screen.getByRole('button', { name: 'Aggiorna password' }));
    expect(
      await screen.findByRole('heading', { name: 'Password aggiornata' }),
    ).toBeVisible();
    expect(confirmSpy).toHaveBeenCalledTimes(2);
  });
});

describe('ResetPasswordPage MemoryRouter missing token', () => {
  it('non chiama il backend su mount senza fragment', () => {
    const confirmSpy = vi.spyOn(authOnboardingApi, 'confirmPasswordRecovery');
    render(
      <MemoryRouter initialEntries={['/reset-password']}>
        <ResetPasswordPage />
      </MemoryRouter>,
    );
    expect(confirmSpy).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });
});
