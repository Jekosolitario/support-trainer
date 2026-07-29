import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import { clearCsrf } from '../../api/csrf';
import { HttpApiError, type ErrorResponse } from '../../api/types';
import { RegisterProfessionalPage } from './RegisterProfessionalPage';

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register/professional']}>
      <RegisterProfessionalPage />
    </MemoryRouter>,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nome'), 'Ada');
  await user.type(screen.getByLabelText('Cognome'), 'Lovelace');
  await user.type(screen.getByLabelText('Email'), 'ada@example.com');
  await user.type(screen.getByLabelText('Password'), 'Password1!');
  await user.click(screen.getByLabelText('Personal trainer'));
}

function validationError(
  fieldErrors: Array<{ field: string; code: string; message: string }>,
): HttpApiError {
  const body: ErrorResponse = {
    timestamp: '2026-07-29T10:00:00Z',
    status: 400,
    code: 'VALIDATION_ERROR',
    message: 'Validation failed',
    path: '/api/v1/auth/register/professional',
    fieldErrors,
  };

  return new HttpApiError(
    400,
    body,
    new Response(JSON.stringify(body), { status: 400 }),
  );
}

describe('RegisterProfessionalPage', () => {
  let registerSpy: ReturnType<typeof vi.spyOn>;
  let resendSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    clearCsrf();
    registerSpy = vi.spyOn(authOnboardingApi, 'registerProfessional');
    resendSpy = vi.spyOn(authOnboardingApi, 'resendEmailVerification');
  });

  afterEach(() => {
    cleanup();
    clearCsrf();
    vi.useRealTimers();
    registerSpy.mockRestore();
    resendSpy.mockRestore();
    vi.restoreAllMocks();
  });

  it('renderizza labels e specializzazioni', () => {
    renderRegister();
    expect(
      screen.getByRole('heading', { name: 'Registrazione professionista' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Personal trainer')).toBeInTheDocument();
    expect(screen.getByLabelText('Nutrizionista')).toBeInTheDocument();
  });

  it('blocca submit con validazione locale', async () => {
    const user = userEvent.setup();
    renderRegister();
    await user.click(screen.getByRole('button', { name: 'Registrati' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(registerSpy).not.toHaveBeenCalled();
  });

  it('gestisce 202 neutro, azzera draft/password e check-email/resend', async () => {
    const user = userEvent.setup();
    registerSpy.mockResolvedValue({ message: 'messaggio A' });
    resendSpy.mockResolvedValue({ message: 'resend A' });

    renderRegister();
    await fillValidForm(user);
    expect(screen.getByLabelText('Password')).toHaveValue('Password1!');
    await user.click(screen.getByRole('button', { name: 'Registrati' }));

    expect(
      await screen.findByText('Controlla la tua email'),
    ).toBeInTheDocument();
    expect(screen.getByText(/ada@example.com/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('Password1!')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain('Password1!');

    await user.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(
      await screen.findByText(/Se l’indirizzo è associato/),
    ).toBeInTheDocument();
    expect(resendSpy).toHaveBeenCalledWith({ email: 'ada@example.com' });
    expect(
      screen.getByRole('button', { name: /Invia di nuovo tra/ }),
    ).toBeDisabled();
  });

  it('tratta due body 202 differenti con la stessa UI', async () => {
    const user = userEvent.setup();
    registerSpy.mockResolvedValueOnce({ message: 'alpha' });

    const first = renderRegister();
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrati' }));
    const firstCopy = (
      await screen.findByText(/Se la registrazione può essere completata/)
    ).textContent;
    first.unmount();

    registerSpy.mockResolvedValueOnce({
      message: 'beta completamente diverso',
    });
    renderRegister();
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrati' }));
    const secondCopy = (
      await screen.findByText(/Se la registrazione può essere completata/)
    ).textContent;

    expect(firstCopy).toBe(secondCopy);
  });

  it('mappa fieldErrors multipli', async () => {
    const user = userEvent.setup();
    registerSpy.mockRejectedValue(
      validationError([
        { field: 'email', code: 'Email', message: 'bad' },
        { field: 'password', code: 'Pattern', message: 'weak' },
      ]),
    );

    renderRegister();
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrati' }));

    const alert = await screen.findByRole('alert');
    expect(
      within(alert).getByText('Inserisci un indirizzo email valido.'),
    ).toBeInTheDocument();
    expect(
      within(alert).getByText(/maiuscola, un numero e un carattere speciale/),
    ).toBeInTheDocument();
  });

  it('ignora response register dopo unmount', async () => {
    const user = userEvent.setup();
    let resolveRegister!: (value: { message: string }) => void;
    registerSpy.mockReturnValue(
      new Promise((resolve) => {
        resolveRegister = resolve;
      }),
    );

    const view = renderRegister();
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrati' }));
    view.unmount();
    resolveRegister({ message: 'late' });
    await waitFor(() => {
      expect(
        screen.queryByText('Controlla la tua email'),
      ).not.toBeInTheDocument();
    });
  });

  it('previene doppio submit e doppio resend', async () => {
    const user = userEvent.setup();
    let resolveRegister!: (value: { message: string }) => void;
    registerSpy.mockReturnValue(
      new Promise((resolve) => {
        resolveRegister = resolve;
      }),
    );

    renderRegister();
    await fillValidForm(user);
    const button = screen.getByRole('button', { name: 'Registrati' });
    await user.click(button);
    await user.click(button);
    expect(registerSpy).toHaveBeenCalledTimes(1);

    resolveRegister({ message: 'ok' });
    expect(
      await screen.findByText('Controlla la tua email'),
    ).toBeInTheDocument();

    let resolveResend!: (value: { message: string }) => void;
    resendSpy.mockReturnValue(
      new Promise((resolve) => {
        resolveResend = resolve;
      }),
    );
    const resendButton = screen.getByRole('button', { name: 'Invia di nuovo' });
    await user.click(resendButton);
    await user.click(resendButton);
    expect(resendSpy).toHaveBeenCalledTimes(1);
    resolveResend({ message: 'ok' });
  });

  it('applica cooldown 60s con salto temporale', async () => {
    const user = userEvent.setup();
    registerSpy.mockResolvedValue({ message: 'ok' });
    resendSpy.mockResolvedValue({ message: 'ok' });

    const start = Date.now();
    const dateNow = vi.spyOn(Date, 'now').mockImplementation(() => start);

    renderRegister();
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrati' }));
    await screen.findByText('Controlla la tua email');
    await user.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(
      await screen.findByRole('button', { name: /Invia di nuovo tra/ }),
    ).toBeDisabled();

    dateNow.mockImplementation(() => start + 30_000);
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /Invia di nuovo tra/ }),
      ).toBeDisabled();
    });

    dateNow.mockImplementation(() => start + 60_000);
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Invia di nuovo' }),
      ).toBeEnabled();
    });
  });
});
