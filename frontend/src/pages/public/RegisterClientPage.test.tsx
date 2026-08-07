import { StrictMode, useState, type ReactNode } from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  MemoryRouter,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import * as authOnboardingApi from '../../api/authOnboardingApi';
import type { RegisterClientOutcome } from '../../auth/clientOnboardingOutcome';
import { AuthContext, type AuthState } from '../../auth/authState';
import { ClientOnboardingProviderLayout } from '../../auth/ClientOnboardingProviderLayout';
import { ClientOnboardingProvider } from '../../auth/ClientOnboardingContext';
import { useClientOnboarding } from '../../auth/clientOnboardingState';
import {
  createAuthenticatedAuthState,
  createAuthContextValue,
  createUnauthenticatedAuthState,
} from '../../test/renderWithAuthContext';
import { HttpApiError, type FieldErrorResponse } from '../../api/types';
import { RegisterClientPage } from './RegisterClientPage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function InviteProbe() {
  const { inviteCode } = useClientOnboarding();
  return <p data-testid="provider-invite">{inviteCode ?? 'null'}</p>;
}

function ClearInviteButton() {
  const { clearInvite } = useClientOnboarding();
  return (
    <button type="button" onClick={clearInvite}>
      clear-invite
    </button>
  );
}

function TestProviderLayout() {
  return (
    <ClientOnboardingProvider>
      <InviteProbe />
      <ClearInviteButton />
      <Outlet />
    </ClientOnboardingProvider>
  );
}

function SeedInvite({ code = 'INV-CLIENT00001' }: { readonly code?: string }) {
  const { setValidatedInvite } = useClientOnboarding();
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        setValidatedInvite(code);
        navigate('/register/client');
      }}
    >
      enter-register
    </button>
  );
}

function LocationProbe() {
  const location = useLocation();
  return (
    <div>
      <p data-testid="pathname">{location.pathname}</p>
      <p data-testid="search">{location.search}</p>
      <p data-testid="hash">{location.hash}</p>
      <p data-testid="location-state">{JSON.stringify(location.state)}</p>
    </div>
  );
}

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      back
    </button>
  );
}

function ForwardButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(1)}>
      forward
    </button>
  );
}

function TestRoutes({ seedCode }: { readonly seedCode?: string }) {
  return (
    <>
      <LocationProbe />
      <Routes>
        <Route element={<TestProviderLayout />}>
          <Route path="/seed" element={<SeedInvite code={seedCode} />} />
          <Route path="/register/client" element={<RegisterClientPage />} />
          <Route
            path="/invite/validate"
            element={
              <>
                <h1>Validazione invito</h1>
                <BackButton />
                <ForwardButton />
              </>
            }
          />
        </Route>
        <Route path="/prior" element={<h1>Pagina precedente</h1>} />
        <Route
          path="/login"
          element={
            <>
              <h1>Login</h1>
              <BackButton />
            </>
          }
        />
      </Routes>
    </>
  );
}

function renderRegister(options?: {
  readonly seedCode?: string;
  readonly initialEntries?: string[];
  readonly initialIndex?: number;
  readonly enter?: boolean;
  readonly strictMode?: boolean;
}) {
  const routes = <TestRoutes seedCode={options?.seedCode} />;
  const view = render(
    <MemoryRouter
      initialEntries={options?.initialEntries ?? ['/seed']}
      initialIndex={options?.initialIndex}
    >
      {options?.strictMode === true ? (
        <StrictMode>{routes}</StrictMode>
      ) : (
        routes
      )}
    </MemoryRouter>,
  );

  if (options?.enter !== false) {
    fireEvent.click(screen.getByRole('button', { name: 'enter-register' }));
  }

  return view;
}

function fillValidForm(overrides?: Partial<Record<string, string>>): void {
  const values = {
    firstName: ' Ada ',
    lastName: ' Lovelace ',
    email: ' ADA@Example.COM ',
    password: 'Password1!',
    birthDate: '1995-12-10',
    heightCm: '170,25',
    primaryGoal: ' Migliorare la forma fisica ',
    ...overrides,
  };

  fireEvent.change(screen.getByLabelText('Nome'), {
    target: { value: values.firstName },
  });
  fireEvent.change(screen.getByLabelText('Cognome'), {
    target: { value: values.lastName },
  });
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: values.email },
  });
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: values.password },
  });
  fireEvent.change(screen.getByLabelText('Data di nascita'), {
    target: { value: values.birthDate },
  });
  fireEvent.change(screen.getByLabelText('Altezza (cm)'), {
    target: { value: values.heightCm },
  });
  fireEvent.change(screen.getByLabelText('Obiettivo principale'), {
    target: { value: values.primaryGoal },
  });
  fireEvent.click(screen.getByLabelText('Donna'));
}

function submitForm(): void {
  fireEvent.submit(
    screen
      .getByRole('button', { name: 'Crea account cliente' })
      .closest('form')!,
  );
}

function knownFailure(
  status: number,
  code: string,
  fieldErrors?: FieldErrorResponse[],
): RegisterClientOutcome {
  return {
    kind: 'known_failure',
    error: new HttpApiError(
      status,
      {
        timestamp: '2026-08-05T10:00:00Z',
        status,
        code,
        message: `backend:${code}`,
        path: '/api/v1/auth/register/client',
        fieldErrors,
      },
      new Response(null, { status }),
    ),
  };
}

const unavailableState: AuthState = {
  status: 'unavailable',
  operation: null,
  reason: 'bootstrap-failed',
  account: null,
  profile: null,
  accessProfile: null,
};

function AuthTransitionHarness({
  children,
}: {
  readonly children?: ReactNode;
}) {
  const [state, setState] = useState<AuthState>(
    createUnauthenticatedAuthState(),
  );

  return (
    <AuthContext.Provider value={createAuthContextValue(state)}>
      <button
        type="button"
        onClick={() =>
          setState(
            createAuthenticatedAuthState({
              role: 'CLIENT',
              specialization: null,
            }),
          )
        }
      >
        become-authenticated
      </button>
      <button type="button" onClick={() => setState(unavailableState)}>
        become-unavailable
      </button>
      {children}
      <MemoryRouter initialEntries={['/seed']}>
        <Routes>
          <Route element={<ClientOnboardingProviderLayout />}>
            <Route path="/seed" element={<SeedInvite />} />
            <Route path="/register/client" element={<RegisterClientPage />} />
          </Route>
          <Route
            path="/app/client/dashboard"
            element={<h1>Client dashboard</h1>}
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('RegisterClientPage', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('mostra il form reale completo soltanto con invite memory-only', () => {
    renderRegister({ seedCode: 'INV-SECRET0001' });

    expect(
      screen.getByRole('heading', { name: 'Registrazione cliente' }),
    ).toBeVisible();
    for (const label of [
      'Nome',
      'Cognome',
      'Email',
      'Password',
      'Data di nascita',
      'Altezza (cm)',
      'Obiettivo principale',
      'Note mediche (facoltative)',
      'Note sugli infortuni (facoltative)',
      'Altre note (facoltative)',
    ]) {
      expect(screen.getByLabelText(label)).toBeVisible();
    }
    expect(screen.getByRole('group', { name: 'Genere' })).toBeVisible();
    expect(screen.getByText(/Questi campi sono facoltativi/)).toBeVisible();
    expect(
      screen.getByRole('article', { name: 'Registrazione cliente' })
        .textContent ?? '',
    ).not.toContain('INV-SECRET0001');
  });

  it('espone required sui campi obbligatori senza preselezionare gender', () => {
    renderRegister();

    for (const label of [
      'Nome',
      'Cognome',
      'Email',
      'Password',
      'Data di nascita',
      'Altezza (cm)',
      'Obiettivo principale',
    ]) {
      expect(screen.getByLabelText(label)).toBeRequired();
    }

    const genderOptions = screen.getAllByRole('radio');
    expect(genderOptions).toHaveLength(4);
    for (const option of genderOptions) {
      expect(option).toBeRequired();
      expect(option).not.toBeChecked();
    }

    for (const label of [
      'Note mediche (facoltative)',
      'Note sugli infortuni (facoltative)',
      'Altre note (facoltative)',
    ]) {
      expect(screen.getByLabelText(label)).not.toBeRequired();
    }
    expect(screen.getByText(/Questi campi sono facoltativi/)).toBeVisible();
  });

  it('direct access senza invite usa replace verso validate', async () => {
    renderRegister({
      initialEntries: ['/prior', '/register/client'],
      initialIndex: 1,
      enter: false,
    });

    expect(
      screen.getByRole('heading', { name: 'Validazione invito' }),
    ).toBeVisible();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/invite/validate',
    );

    await userEvent.setup().click(screen.getByRole('button', { name: 'back' }));
    expect(
      screen.getByRole('heading', { name: 'Pagina precedente' }),
    ).toBeVisible();
  });

  it('clear esterno durante il form fail-closed senza chiamare register', () => {
    const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');
    renderRegister();
    fillValidForm({ password: 'SecretPass1!' });

    fireEvent.click(screen.getByRole('button', { name: 'clear-invite' }));

    expect(
      screen.getByRole('heading', { name: 'Validazione invito' }),
    ).toBeVisible();
    expect(registerSpy).not.toHaveBeenCalled();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(document.body.textContent ?? '').not.toContain('SecretPass1!');
  });

  it('required invalidi non chiamano API e focusano il primo campo', async () => {
    const registerSpy = vi.spyOn(authOnboardingApi, 'registerClient');
    renderRegister();

    submitForm();

    expect(registerSpy).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByLabelText('Nome')).toHaveFocus());
    expect(screen.getByLabelText('Nome')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(screen.getByText('Inserisci il nome.')).toBeVisible();
    expect(screen.getByText('Seleziona il genere.')).toBeVisible();
  });

  it('modificare un campo elimina soltanto il suo errore stale', () => {
    renderRegister();
    submitForm();

    fireEvent.change(screen.getByLabelText('Nome'), {
      target: { value: 'Ada' },
    });

    expect(screen.queryByText('Inserisci il nome.')).not.toBeInTheDocument();
    expect(screen.getByText('Inserisci il cognome.')).toBeVisible();
  });

  it('invia uno snapshot esatto e immutabile con note trimmed e password identica', async () => {
    const gate = deferred<RegisterClientOutcome>();
    const registerSpy = vi
      .spyOn(authOnboardingApi, 'registerClient')
      .mockReturnValue(gate.promise);
    renderRegister({ seedCode: 'INV-CANONICAL01' });
    fillValidForm({ password: ' Password1! ' });
    fireEvent.change(screen.getByLabelText('Note mediche (facoltative)'), {
      target: { value: '  Controllo periodico ' },
    });
    fireEvent.change(
      screen.getByLabelText('Note sugli infortuni (facoltative)'),
      {
        target: { value: '   ' },
      },
    );
    fireEvent.change(screen.getByLabelText('Altre note (facoltative)'), {
      target: { value: ' Preferenza oraria ' },
    });

    submitForm();

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      password: ' Password1! ',
      inviteCode: 'INV-CANONICAL01',
      birthDate: '1995-12-10',
      heightCm: 170.25,
      primaryGoal: 'Migliorare la forma fisica',
      gender: 'FEMALE',
      medicalNotes: 'Controllo periodico',
      notes: 'Preferenza oraria',
    });
    expect(Object.isFrozen(registerSpy.mock.calls[0]?.[0])).toBe(true);

    await act(async () => gate.resolve({ kind: 'accepted' }));
  });

  it('double submit same-tick produce una sola chiamata applicativa a registerClient', async () => {
    const gate = deferred<RegisterClientOutcome>();
    const registerSpy = vi
      .spyOn(authOnboardingApi, 'registerClient')
      .mockReturnValue(gate.promise);
    renderRegister();
    fillValidForm();
    const form = screen
      .getByRole('button', { name: 'Crea account cliente' })
      .closest('form')!;

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(registerSpy).toHaveBeenCalledTimes(1);
    await act(async () => gate.resolve({ kind: 'accepted' }));
    expect(registerSpy).toHaveBeenCalledTimes(1);
  });

  it('accepted pulisce provider e draft, smonta il form e usa copy neutra', async () => {
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'accepted',
    });
    renderRegister({ seedCode: 'INV-TERMINAL01' });
    fillValidForm({ password: 'SecretPass1!' });
    fireEvent.change(screen.getByLabelText('Note mediche (facoltative)'), {
      target: { value: 'Nota privata terminale' },
    });

    submitForm();

    expect(
      await screen.findByRole('heading', { name: 'Controlla la tua email' }),
    ).toBeVisible();
    expect(screen.getByText(/Richiesta ricevuta/)).toBeVisible();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(document.body.textContent ?? '').not.toContain('SecretPass1!');
    expect(document.body.textContent ?? '').not.toContain(
      'Nota privata terminale',
    );
    expect(document.body.textContent ?? '').not.toContain('INV-TERMINAL01');
    expect(screen.getByRole('link', { name: 'Vai al login' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(
      screen.queryByRole('link', { name: /verifica email/i }),
    ).not.toBeInTheDocument();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/register/client',
    );
    expect(screen.getByTestId('search')).toHaveTextContent('');
    expect(screen.getByTestId('hash')).toHaveTextContent('');
    expect(screen.getByTestId('location-state')).toHaveTextContent('null');
  });

  it('uscita e Back dopo confirmed non ripristinano provider o draft', async () => {
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'accepted',
    });
    renderRegister();
    fillValidForm({ password: 'SecretPass1!' });
    submitForm();
    await screen.findByRole('heading', { name: 'Controlla la tua email' });

    fireEvent.click(screen.getByRole('link', { name: 'Vai al login' }));
    expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));

    expect(
      await screen.findByRole('heading', { name: 'Validazione invito' }),
    ).toBeVisible();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(document.body.textContent ?? '').not.toContain('SecretPass1!');

    fireEvent.click(screen.getByRole('button', { name: 'forward' }));
    expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    expect(document.body.textContent ?? '').not.toContain('SecretPass1!');
  });

  it('VALIDATION_ERROR conserva form/invite e preserva più errori mappati', async () => {
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue(
      knownFailure(400, 'VALIDATION_ERROR', [
        { field: 'email', code: 'NotBlank', message: 'backend hidden 1' },
        { field: 'email', code: 'Email', message: 'backend hidden 2' },
        { field: 'unknown', code: 'Size', message: 'backend hidden 3' },
      ]),
    );
    renderRegister({ seedCode: 'INV-RETRY00001' });
    fillValidForm();
    submitForm();

    expect(await screen.findByText('Inserisci l’email.')).toBeVisible();
    expect(
      screen.getByText('Inserisci un indirizzo email valido.'),
    ).toBeVisible();
    expect(
      screen.getByText('Controlla i dati inseriti e riprova.'),
    ).toBeVisible();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent(
      'INV-RETRY00001',
    );
    expect(
      screen.getByRole('button', { name: 'Crea account cliente' }),
    ).toBeEnabled();
    expect(document.body.textContent ?? '').not.toContain('backend hidden');
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus());
  });

  it.each([
    [400, 'MALFORMED_REQUEST'],
    [403, 'CSRF_VALIDATION_FAILED'],
  ] as const)(
    '%i %s mantiene form e consente retry manuale',
    async (status, code) => {
      vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue(
        knownFailure(status, code),
      );
      renderRegister({ seedCode: 'INV-RETRY00002' });
      fillValidForm();
      submitForm();

      expect(await screen.findByRole('alert')).toBeVisible();
      expect(
        screen.getByRole('button', { name: 'Crea account cliente' }),
      ).toBeEnabled();
      expect(screen.getByTestId('provider-invite')).toHaveTextContent(
        'INV-RETRY00002',
      );
    },
  );

  it.each([
    [404, 'INVITE_CODE_NOT_FOUND'],
    [400, 'INVITE_CODE_NOT_ACTIVE'],
    [400, 'INVITE_CODE_ALREADY_USED'],
    [400, 'INVITE_CODE_EXPIRED'],
    [403, 'ACCOUNT_NOT_ACTIVE'],
    [403, 'EMAIL_NOT_VERIFIED'],
    [403, 'PROFESSIONAL_NOT_ACTIVE'],
  ] as const)(
    '%i %s usa outcome invito neutro e cleanup terminale',
    async (status, code) => {
      vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue(
        knownFailure(status, code),
      );
      renderRegister({ seedCode: 'INV-UNAVAILABLE' });
      fillValidForm({ password: 'SecretPass1!' });
      submitForm();

      expect(
        await screen.findByRole('heading', { name: 'Invito non disponibile' }),
      ).toBeVisible();
      expect(
        screen.getByText(/Questo invito non è più disponibile/),
      ).toBeVisible();
      expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
      expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
      expect(document.body.textContent ?? '').not.toContain(code);
      expect(document.body.textContent ?? '').not.toContain('SecretPass1!');
      expect(
        screen.getByRole('link', { name: 'Verifica un altro codice' }),
      ).toHaveAttribute('href', '/invite/validate');
    },
  );

  it.each([
    '500 INTERNAL_SERVER_ERROR',
    '500 VALIDATION_ERROR',
    '500 INVITE_CODE_EXPIRED',
    '200 unexpected',
    '201 unexpected',
    '204 unexpected',
    'status/code mismatch',
    'unknown code',
    'network transport',
    'stale auth operation',
    'anomalous response',
    'unknown error',
  ])('%s resta ambiguous, senza retry register', async (label) => {
    const registerSpy = vi
      .spyOn(authOnboardingApi, 'registerClient')
      .mockResolvedValue({ kind: 'ambiguous', cause: new Error(label) });
    renderRegister({ seedCode: 'INV-AMBIGUOUS' });
    fillValidForm({ password: 'SecretPass1!' });
    submitForm();

    expect(
      await screen.findByRole('heading', {
        name: 'Esito della registrazione non confermato',
      }),
    ).toBeVisible();
    expect(screen.getByText(/Non possiamo confermare l’esito/)).toBeVisible();
    expect(
      screen.queryByRole('button', { name: /Crea account/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
    expect(document.body.textContent ?? '').not.toContain('SecretPass1!');
    expect(registerSpy).toHaveBeenCalledTimes(1);
  });

  it('resend confirmed è neutro, fenced same-tick e applica cooldown 60s', async () => {
    vi.useFakeTimers();
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'accepted',
    });
    const resendGate = deferred<{ message: string }>();
    const resendSpy = vi
      .spyOn(authOnboardingApi, 'resendEmailVerification')
      .mockReturnValue(resendGate.promise);
    renderRegister({ strictMode: true });
    fillValidForm();
    submitForm();
    await act(async () => Promise.resolve());

    const button = screen.getByRole('button', { name: 'Invia di nuovo' });
    act(() => {
      button.click();
      button.click();
    });
    expect(resendSpy).toHaveBeenCalledTimes(1);
    expect(resendSpy).toHaveBeenCalledWith({ email: 'ada@example.com' });

    await act(async () => resendGate.resolve({ message: 'backend hidden' }));
    expect(screen.getByRole('status')).toHaveTextContent(
      'Se l’indirizzo è associato a un account da verificare',
    );
    expect(document.body.textContent ?? '').not.toContain('backend hidden');
    expect(
      screen.getByRole('button', { name: /Invia di nuovo tra/ }),
    ).toBeDisabled();
    expect(vi.getTimerCount()).toBe(1);

    await act(async () => {
      vi.advanceTimersByTime(60_000);
      await Promise.resolve();
    });
    expect(
      screen.getByRole('button', { name: 'Invia di nuovo' }),
    ).toBeEnabled();
    expect(vi.getTimerCount()).toBe(0);
    expect(authOnboardingApi.registerClient).toHaveBeenCalledTimes(1);
  });

  it('la deadline blocca un resend post-completion prima del sync visuale del countdown', async () => {
    vi.useFakeTimers();
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'accepted',
    });
    const resendGate = deferred<{ message: string }>();
    const resendSpy = vi
      .spyOn(authOnboardingApi, 'resendEmailVerification')
      .mockReturnValue(resendGate.promise);
    const view = renderRegister();
    fillValidForm();
    submitForm();
    await act(async () => Promise.resolve());

    fireEvent.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    expect(resendSpy).toHaveBeenCalledTimes(1);

    const queuedCountdownUpdates: VoidFunction[] = [];
    const queueMicrotaskSpy = vi
      .spyOn(globalThis, 'queueMicrotask')
      .mockImplementation((callback) => {
        queuedCountdownUpdates.push(callback);
      });

    await act(async () => {
      resendGate.resolve({ message: 'backend hidden' });
      await resendGate.promise;
    });

    expect(queuedCountdownUpdates.length).toBeGreaterThan(0);
    const resendButton = screen.getByRole('button', {
      name: /Invia di nuovo tra/,
    });
    expect(resendButton).not.toHaveTextContent('Invio in corso');
    expect(resendButton).toBeDisabled();

    resendButton.removeAttribute('disabled');
    fireEvent.click(resendButton);
    expect(resendSpy).toHaveBeenCalledTimes(1);
    expect(authOnboardingApi.registerClient).toHaveBeenCalledTimes(1);

    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
    queueMicrotaskSpy.mockRestore();
  });

  it('resend è disponibile anche su ambiguous senza ripristinare invite', async () => {
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'ambiguous',
      cause: new Error('unknown'),
    });
    const resendSpy = vi
      .spyOn(authOnboardingApi, 'resendEmailVerification')
      .mockResolvedValue({ message: 'neutral' });
    renderRegister();
    fillValidForm();
    submitForm();
    await screen.findByRole('heading', {
      name: 'Esito della registrazione non confermato',
    });

    fireEvent.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    await waitFor(() => expect(resendSpy).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId('provider-invite')).toHaveTextContent('null');
  });

  it('unmount durante resend pending ignora completion tardiva', async () => {
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);
    vi.spyOn(authOnboardingApi, 'registerClient').mockResolvedValue({
      kind: 'accepted',
    });
    const resendGate = deferred<{ message: string }>();
    const resendSpy = vi
      .spyOn(authOnboardingApi, 'resendEmailVerification')
      .mockReturnValue(resendGate.promise);
    const view = renderRegister();
    fillValidForm();
    submitForm();
    await screen.findByRole('heading', { name: 'Controlla la tua email' });
    fireEvent.click(screen.getByRole('button', { name: 'Invia di nuovo' }));
    view.unmount();

    await act(async () => resendGate.resolve({ message: 'neutral' }));
    expect(resendSpy).toHaveBeenCalledTimes(1);
    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('unmount durante register pending ignora outcome tardivo senza abort o secondo POST', async () => {
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);
    const gate = deferred<RegisterClientOutcome>();
    const registerSpy = vi
      .spyOn(authOnboardingApi, 'registerClient')
      .mockReturnValue(gate.promise);
    const view = renderRegister();
    fillValidForm();
    submitForm();
    view.unmount();

    await act(async () => gate.resolve({ kind: 'accepted' }));
    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('uscita dal subtree durante POST distrugge provider e ignora response tardiva', async () => {
    const gate = deferred<RegisterClientOutcome>();
    vi.spyOn(authOnboardingApi, 'registerClient').mockReturnValue(gate.promise);
    renderRegister();
    fillValidForm();
    submitForm();

    fireEvent.click(screen.getByRole('link', { name: /Hai già un account/ }));
    expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    expect(screen.queryByTestId('provider-invite')).not.toBeInTheDocument();

    await act(async () => gate.resolve({ kind: 'accepted' }));
    expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible();
    expect(
      screen.queryByText(/Controlla la tua email/),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['become-authenticated', 'Client dashboard'],
    ['become-unavailable', 'Sessione non verificabile'],
  ] as const)(
    'auth transition %s durante POST ignora completion tardiva',
    async (action, heading) => {
      const gate = deferred<RegisterClientOutcome>();
      const registerSpy = vi
        .spyOn(authOnboardingApi, 'registerClient')
        .mockReturnValue(gate.promise);
      render(<AuthTransitionHarness />);
      fireEvent.click(screen.getByRole('button', { name: 'enter-register' }));
      fillValidForm();
      submitForm();

      fireEvent.click(screen.getByRole('button', { name: action }));
      expect(screen.getByRole('heading', { name: heading })).toBeVisible();

      await act(async () => gate.resolve({ kind: 'accepted' }));
      expect(screen.getByRole('heading', { name: heading })).toBeVisible();
      expect(
        screen.queryByText(/Controlla la tua email/),
      ).not.toBeInTheDocument();
      expect(registerSpy).toHaveBeenCalledTimes(1);
    },
  );
});
