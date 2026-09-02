import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import { clearCsrf } from '../../api/csrf';
import {
  HttpApiError,
  NetworkError,
  type ErrorResponse,
} from '../../api/types';
import { ForgotPasswordPage } from './ForgotPasswordPage';

const SUCCESS_COPY =
  'Se esiste un account associato a questa email, riceverai le istruzioni per reimpostare la password.';

function apiError(status: number, code: string): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-08-31T10:00:00Z',
    status,
    code,
    message: 'hidden',
    path: '/api/v1/auth/password-recovery/request',
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

function renderForgot() {
  return render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/login" element={<h1>Login</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ForgotPasswordPage', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    clearCsrf();
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('renderizza lo stato iniziale', () => {
    renderForgot();

    expect(
      screen.getByRole('heading', { name: 'Password dimenticata' }),
    ).toBeVisible();
    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText('Email')).toHaveAttribute(
      'autocomplete',
      'email',
    );
    expect(
      screen.getByRole('button', { name: 'Invia istruzioni' }),
    ).toBeEnabled();
    expect(
      screen.getByRole('link', { name: 'Torna al login' }),
    ).toHaveAttribute('href', '/login');
  });

  it('blocca email locale invalida senza chiamare l’API', async () => {
    const user = userEvent.setup();
    const requestSpy = vi.spyOn(authOnboardingApi, 'requestPasswordRecovery');
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));

    expect(
      await screen.findByText('Inserisci un indirizzo email valido.'),
    ).toBeInTheDocument();
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('blocca il doppio submit mentre la request è in volo', async () => {
    const user = userEvent.setup();
    const gate = deferred<{ message: string }>();
    const requestSpy = vi
      .spyOn(authOnboardingApi, 'requestPasswordRecovery')
      .mockReturnValue(gate.promise);
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));
    await user.click(screen.getByRole('button', { name: 'Invio in corso' }));

    expect(requestSpy).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole('button', { name: 'Invio in corso' }),
    ).toBeDisabled();

    gate.resolve({ message: SUCCESS_COPY });
    expect(await screen.findByText(SUCCESS_COPY)).toBeInTheDocument();
  });

  it('mostra successo neutro identico per 202', async () => {
    const user = userEvent.setup();
    vi.spyOn(authOnboardingApi, 'requestPasswordRecovery').mockResolvedValue({
      message: SUCCESS_COPY,
    });
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'unknown@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));

    expect(await screen.findByRole('status')).toHaveTextContent(SUCCESS_COPY);
    expect(screen.queryByText(/trovat/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/inesistent/i)).not.toBeInTheDocument();
  });

  it('mostra errore tecnico retryable e consente un nuovo invio', async () => {
    const user = userEvent.setup();
    const requestSpy = vi
      .spyOn(authOnboardingApi, 'requestPasswordRecovery')
      .mockRejectedValueOnce(new NetworkError('down'))
      .mockResolvedValueOnce({ message: SUCCESS_COPY });
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));

    expect(
      await screen.findByText(
        'Non è stato possibile inviare la richiesta. Riprova.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveValue('user@example.com');

    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));
    expect(await screen.findByText(SUCCESS_COPY)).toBeInTheDocument();
    expect(requestSpy).toHaveBeenCalledTimes(2);
  });

  it('su unexpected 2xx strutturato mostra errore tecnico, non successo né validation', async () => {
    const user = userEvent.setup();
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        structuredErrorResponse(
          200,
          'VALIDATION_ERROR',
          '/api/v1/auth/password-recovery/request',
        ),
      );
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));

    expect(
      await screen.findByText(
        'Non è stato possibile inviare la richiesta. Riprova.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Controlla i dati inseriti e riprova.'),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Invia istruzioni' }),
    ).toBeEnabled();
  });

  it('mappa VALIDATION_ERROR sul campo email', async () => {
    const user = userEvent.setup();
    vi.spyOn(authOnboardingApi, 'requestPasswordRecovery').mockRejectedValue(
      apiError(400, 'VALIDATION_ERROR'),
    );
    renderForgot();

    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: 'Invia istruzioni' }));

    await waitFor(() => {
      expect(
        screen.getByText('Controlla i dati inseriti e riprova.'),
      ).toBeInTheDocument();
    });
  });
});
