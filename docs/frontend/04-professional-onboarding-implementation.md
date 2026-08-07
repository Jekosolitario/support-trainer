# Professional Onboarding Implementation — Frontend

## 1. Scopo e maturity

Questo documento è la **source of truth tecnica frontend** del workflow pubblico PROFESSIONAL: registrazione, verifica email e reinvio.

Documenta:

- maturity del perimetro implementato;
- workflow end-to-end;
- contratti API usati dal frontend (semantica, non catalogo);
- architettura client (separazione da session authentication);
- comportamento Register e Verify;
- modello errori UX;
- invarianti di sicurezza e password lato client;
- principi di accessibilità e di test.

Non documenta:

- lifecycle di sessione autenticata (login, logout, bootstrap, guards) → [`03-authentication-session-flow.md`](./03-authentication-session-flow.md);
- catalogo endpoint completo → [`docs/08-endpoint-map.md`](../08-endpoint-map.md);
- regole di validazione/password come source of truth principale → [`docs/06-validation-rules.md`](../06-validation-rules.md);
- cookie, timeout server e flusso sicurezza backend → [`docs/09-security-flow.md`](../09-security-flow.md);
- mappa funzionale/maturity delle pagine → [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md);
- home pubblica → [`02-public-home-implementation.md`](./02-public-home-implementation.md).

### Implementato

- route `/register/professional`;
- registrazione pubblica PROFESSIONAL (form, validazione client, submit CSRF-aware);
- stato post-register “Controlla la tua email”;
- reinvio verifica (`resend`) con cooldown UX;
- route `/verify-email`;
- conferma email da fragment `#token=...`;
- handoff esplicito verso Login (nessuna sessione creata da register/verify).

### Fuori scope del documento

- `/invite/validate`, `/register/client` e onboarding CLIENT → implementati e documentati in [`06-client-onboarding-implementation.md`](./06-client-onboarding-implementation.md);
- auto-login dopo register o verify;
- password reset / forgot password;
- dashboard o altre pagine business.

Non anticipare feature future oltre questi confini.

## 2. Workflow end-to-end

Percorso implementato:

`Home` → `/register/professional` → submit → risposta `202` neutra → stato in-page “Controlla la tua email” → email con link → `/verify-email#token=...` → verifica → CTA Login.

Invarianti di autenticazione:

- **register non autentica**;
- **verify non autentica**;
- la **sessione autenticata nasce soltanto con Login**.

Dopo il successo di verify l’utente viene indirizzato al Login. Il dettaglio del lifecycle di sessione resta in [`03-authentication-session-flow.md`](./03-authentication-session-flow.md).

## 3. Contratti frontend/backend usati

Sintesi semantica per il client. Catalogo completo e payload: [`docs/08-endpoint-map.md`](../08-endpoint-map.md).

### Register — `POST /api/v1/auth/register/professional`

- mutazione pubblica con CSRF (`GET /api/v1/auth/csrf` + header da `headerName`);
- successo atteso: **`202 Accepted`** con messaggio neutro;
- la UI **non** deduce creazione account né enumera email già registrate;
- errori principali: `400 VALIDATION_ERROR` (field/general); altri errori applicativi/temporanei senza messaggi che rivelino esistenza account.

### Confirm — `POST /api/v1/auth/email-verification/confirm`

- mutazione pubblica con CSRF;
- body con `token` (valore già sanitizzato dal fragment, solo in memoria);
- successo: risposta positiva (tipicamente `200`) → UI di successo + CTA Login;
- secondo consumo coerente gestito idempotentemente dal backend: se la risposta è di successo, il frontend tratta il caso come successo;
- errori tipici: token scaduto, non trovato, already-used incoerente, account non attivo, errori temporanei/rete/CSRF.

### Resend — `POST /api/v1/auth/email-verification/resend`

- mutazione pubblica con CSRF;
- body con `email`;
- ogni input sintatticamente valido restituisce **`202`** neutro identico;
- la UI non rivela se l’account esista, sia già verificato o sia in cooldown;
- cooldown UX locale (60 s) non sostituisce il cooldown autoritativo backend.

## 4. Architettura frontend

Principi:

- **Register** e **Verify** sono pagine pubbliche separate;
- le API di onboarding sono separate da login/logout;
- riuso di `performCsrfMutation` e della foundation HTTP/CSRF esistenti;
- **nessun** secondo HTTP client;
- **nessun** secondo sistema CSRF;
- **nessun** coupling con `AuthProvider`;
- **nessun** avanzamento diretto dell’auth epoch;
- **nessun** mutex login/logout;
- **nessun** bootstrap sessione;
- **nessun** JWT né storage auth.

Principio centrale: **onboarding pubblico ≠ session authentication**.

`AuthProvider` resta la source of truth dello stato autenticato; l’onboarding non lo modifica. Session flow: [`03-authentication-session-flow.md`](./03-authentication-session-flow.md).

## 5. Register PROFESSIONAL

Campi del form:

- `firstName`;
- `lastName`;
- `email`;
- `password`;
- `specialization`: `PERSONAL_TRAINER` | `NUTRITIONIST`.

Comportamento:

- validazione client coerente con la policy documentata (vedi §9 e [`docs/06-validation-rules.md`](../06-validation-rules.md));
- il **backend resta autoritativo**;
- errori di campo e messaggi generali senza enumerazione account;
- protezione double-submit (busy/disabled durante l’invio);
- su `202` neutro: passaggio **in-page** a “Controlla la tua email”;
- conservazione della sola `registeredEmail` per resend/CTA;
- cancellazione immediata di draft e password dallo state;
- azione resend con messaggio neutro e cooldown UX 60 s;
- CTA verso Login (senza auto-login).

## 6. Verify Email

Invarianti (non un walkthrough React linea per linea):

- il token arriva nel **fragment** (`#token=...`);
- parsing robusto del fragment;
- sanitizzazione di **qualsiasi** fragment non vuoto (valido o invalido) dall’address bar / Router **prima** della confirm;
- token valido mantenuto **solo in memoria** per il tentativo corrente;
- nessuna persistenza in `localStorage` / `sessionStorage` / router state;
- `BrowserRouter` (il fragment è riservato al token; non si usa `HashRouter` per l’app);
- `useTransitions={false}` sul router per evitare race con la sanitizzazione del fragment;
- protezione StrictMode, generation/attempt fencing e scarto di async stale;
- bounded retry su `StaleAuthOperationError` quando ancora pertinente al tentativo corrente;
- resend manuale richiede email inserita dall’utente (messaggio neutro).

## 7. Error model UX

Stati UX di verifica (almeno):

| Stato UX | Significato operativo |
| --- | --- |
| `success` | Verifica completata; CTA Login |
| `expired` | Link scaduto |
| `not-found` | Link non valido |
| `already-used` | Link non più utilizzabile (stato **non** successo automatico) |
| `inactive` / application | Account non verificabile in questo momento / errore applicativo |
| `temporary` | Rete, 5xx, CSRF, stale operation, risposta indeterminata |
| generic application error | Fallimento 4xx non mappato specificamente |

Precisazione:

- `EMAIL_VERIFICATION_TOKEN_ALREADY_USED` **non** significa automaticamente successo UX;
- se il backend restituisce **`200`** per un secondo consumo coerente, il frontend tratta l’esito come **successo**.

## 8. Security invariants

Stabilmente:

- architettura sessione/cookie invariata rispetto al modello session-based esistente;
- CSRF su tutte le mutazioni di onboarding;
- token di verifica mai in `localStorage` / `sessionStorage`;
- token mai in router state;
- token non mostrato in DOM/ARIA/log;
- fragment sanitizzato prima della confirm; fragment invalido comunque rimosso;
- neutralità register/resend (nessuna enumerazione account tramite UX);
- password eliminata dallo state dopo `202`;
- nessun auto-login.

Dettaglio sicurezza di piattaforma: [`docs/09-security-flow.md`](../09-security-flow.md).

## 9. Password validation

Policy lato client allineata al contratto server (source of truth: [`docs/06-validation-rules.md`](../06-validation-rules.md)):

- minimo **8 unità UTF-16** (equivalente a `@Size(min=8)` / lunghezza `String` Java);
- almeno una uppercase ASCII;
- almeno un digit ASCII;
- almeno uno special fuori da `[A-Za-z0-9]`;
- massimo **72 byte UTF-8** con semantica Java (`getBytes(UTF_8)` con replacement sui surrogate isolati);
- nessun trim, nessuna normalize, nessun truncate lato client;
- il server resta autoritativo.

Non si documenta qui l’algoritmo byte-per-byte.

## 10. UX / accessibilità

- mobile-first e coerenza con il linguaggio visuale dark-tech esistente;
- label reali associate ai controlli;
- error summary e focus management sugli errori;
- `aria-invalid` e `aria-describedby` sui campi in errore;
- stato busy/disabled durante le mutazioni;
- cooldown e status annunciabili in modo non affidato al solo colore;
- CTA Login dopo verify riuscita;
- messaggi neutri su register/resend.

Non si dichiara certificazione WCAG.

## 11. Strategia test

Principi (non metriche volatile):

- copertura API/CSRF delle mutazioni di onboarding;
- `BrowserRouter` reale e StrictMode dove rilevante al lifecycle fragment;
- sanitizzazione fragment (valido e invalido);
- lifecycle A→B e protezione async stale;
- retry/epoch bounded su operazioni stale;
- concorrenza/resend;
- boundary password e Unicode / surrogate;
- isolation dei test con `isolate: false` dove richiesto dalla suite;
- esecuzione shuffled della suite.

Non documentare come stato permanente: conteggi test, hash commit, seed specifici o finding di audit.

## 12. Relazioni con gli altri documenti

| Documento | Responsabilità |
| --- | --- |
| [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md) | Mappa funzionale e maturity delle pagine |
| [`02-public-home-implementation.md`](./02-public-home-implementation.md) | Home pubblica e navigazione di ingresso |
| [`03-authentication-session-flow.md`](./03-authentication-session-flow.md) | Session authentication (login, logout, bootstrap, guards) |
| **Questo documento (FE04)** | Onboarding pubblico PROFESSIONAL (register, verify, resend) |
| [`06-client-onboarding-implementation.md`](./06-client-onboarding-implementation.md) | Validazione invito e onboarding pubblico CLIENT |
| [`docs/06-validation-rules.md`](../06-validation-rules.md) | Validation / password (source of truth) |
| [`docs/08-endpoint-map.md`](../08-endpoint-map.md) | Catalogo endpoint |
| [`docs/09-security-flow.md`](../09-security-flow.md) | Security flow di piattaforma |
