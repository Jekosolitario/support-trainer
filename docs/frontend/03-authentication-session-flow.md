# Authentication Session Flow — Frontend

## 1. Scopo

Questo documento è la **reference corrente del lifecycle di autenticazione frontend** di Support Trainer (session-based).

Documenta:

- principi del client auth;
- layer principali e responsabilità;
- stati auth;
- bootstrap / reconciliation;
- CSRF client;
- login / logout;
- guards e safe redirect;
- integrazione delle mutation Profilo / Operational Status con la source of truth auth (soft commit);
- protezione race tramite auth epoch;
- stato `unavailable`;
- proxy di sviluppo;
- maturity sintetica delle aree auth e della prima pagina business reale.

Non documenta:

- cookie flags, timeout server, readiness backend → [`docs/09-security-flow.md`](../09-security-flow.md);
- elenco completo degli endpoint → [`docs/08-endpoint-map.md`](../08-endpoint-map.md);
- schema database / Flyway → [`docs/10-database-schema.md`](../10-database-schema.md);
- roadmap business → [`docs/15-planned-endpoints-roadmap.md`](../15-planned-endpoints-roadmap.md);
- campi profilo, layout UX, mappa funzionale delle pagine e dettaglio frontend del follow-up **M1-R** → [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md);
- scope funzionale di prodotto (Account RO, enum status, follow-up high-level) → [`docs/01-functional-scope.md`](../01-functional-scope.md).

## 2. Principi

- autenticazione **server-side** con cookie di sessione gestito dal browser;
- **nessun** JWT, Bearer, refresh token o header `Authorization` lato client;
- **nessun** salvataggio di credenziali o CSRF in `localStorage` / `sessionStorage`;
- chiamate API su path **relativi** sotto `/api/v1/...`;
- `credentials: 'same-origin'`;
- token CSRF **solo in memoria**;
- `AuthProvider` è l’unica source of truth dello stato autenticato (`account`, `profile`, `accessProfile`);
- le pagine business non mantengono una seconda copia persistente dello stato server: eventuali draft di editing sono locali e temporanei.

## 3. Layer principali

| Layer | Responsabilità |
|---|---|
| `httpClient` | Fetch tipizzato verso `/api/v1`, errori HTTP normalizzati, `credentials: 'same-origin'`, eventuale invalidazione sessione su `401` session-bound |
| CSRF manager | Ensure / cache / invalidazione del token CSRF in memoria; header dinamico da `headerName` |
| `csrfMutation` | Mutazioni CSRF-aware con **un solo** retry mirato su `403 CSRF_VALIDATION_FAILED` |
| `authApi` | Contratti login, logout, CSRF, `/me/account`, `/me/profile`; lock globale di mutua esclusione su login/logout |
| `meProfileApi` | `PATCH /me/profile` e `PATCH /me/profile/operational-status` tramite `csrfMutation` (`invalidateOn401: true`) |
| `authEpoch` | Generazione monotona di epoch per scartare operazioni stale |
| `sessionInvalidation` | Pub/sub quando una richiesta session-bound riceve `401` ancora corrente |
| `AuthProvider` | Ownership dello stato auth, bootstrap, login, logout, reconciliation, soft commit profilo |
| Guards | Fail-closed su autenticazione, ruolo e specializzazione |
| `LoginPage` / `LogoutButton` | UI di ingresso e uscita allineate allo stato auth |
| `ProfilePage` | Prima pagina business reale: draft locale, PATCH, soft commit race-safe |

## 4. Stato auth

Stati runtime (`AuthStatus`):

| Stato | Significato |
|---|---|
| `initializing` | Verifica o transizione in corso (bootstrap, login, hydration, logout, reconciliation) |
| `unauthenticated` | Nessuna sessione utilizzabile; l'utente è trattato come anonimo |
| `authenticated` | Sessione coerente con `account`, `profile` e profilo di accesso derivato |
| `unavailable` | La sessione **non è verificabile** in modo affidabile: non equivale automaticamente ad anonimo |

Quando lo stato è `authenticated`, la source of truth espone almeno:

- `account` — dati account;
- `profile` — snapshot profilo corrente;
- `accessProfile` — vista di accesso derivata (ruolo, specializzazione, readiness di routing).

Motivi tipici di `unauthenticated` includono assenza di sessione, invalidazione, login rifiutato, post-login senza sessione, logout completato.

Motivi tipici di `unavailable` includono fallimenti di bootstrap/reconciliation, esiti indeterminati di login/logout, hydration o refresh CSRF post-login falliti, dati sessione incoerenti.

## 5. Bootstrap e reconciliation

Dopo un cookie di sessione potenzialmente valido (avvio app, reload, retry), il client riconcilia con:

- `GET /api/v1/me/account`
- `GET /api/v1/me/profile`

Comportamento generale:

- **401** su bootstrap/reconciliation → sessione assente o non valida → `unauthenticated` (senza trattare il caso come "feature business");
- **entrambe 200** e dati coerenti → `authenticated`;
- **fallimento non-401** (rete, 5xx, risposta inattesa, incoerenza) → `unavailable`, non un logout silenzioso;
- `reconcileSession()` consente un nuovo tentativo dallo stato `unavailable`.

I dettagli di payload restano in [`docs/08-endpoint-map.md`](../08-endpoint-map.md).

Il bootstrap/reconciliation resta il percorso completo di allineamento sessione. Le mutation Profilo/Status **non** lo riutilizzano dopo un PATCH riuscito (vedi soft commit).

## 6. Auth epoch e operazioni stale

L'epoch evita che richieste o lifecycle **vecchi** alterino lo stato dopo un cambio di sessione.

Esempi:

- un `401` di una richiesta iniziata prima di un nuovo login non deve invalidare la sessione appena creata;
- una reconciliation o un logout avviati in un'epoca precedente non devono fare commit su uno stato più recente;
- una response di PATCH profilo/status partita in un'epoca precedente non deve aggiornare logout, nuovo login o altro lifecycle corrente.

Il client confronta l'epoch catturata all'inizio dell'operazione con quella corrente prima di pubblicare invalidazioni o aggiornare lo stato.

## 7. Invalidazione su 401

Per le richieste **session-bound** (tipicamente dopo autenticazione), un `401` può:

1. invalidare l'epoch corrente se ancora valida;
2. notificare i listener di `sessionInvalidation`;
3. portare lo stato auth a `unauthenticated` con motivo di sessione invalidata.

Login e bootstrap/reconciliation possono usare regole diverse (`invalidateOn401` disabilitato dove un `401` è un esito atteso di "nessuna sessione", non un evento di espulsione mid-flight).

Le mutation Profilo e Operational Status usano `invalidateOn401: true` tramite la foundation CSRF/httpClient: non esiste un handling 401 locale alternativo nella ProfilePage.

## 8. CSRF lato client

1. `GET /api/v1/auth/csrf` restituisce `{ token, headerName }` (`Cache-Control: no-store` lato server).
2. Il client conserva token e nome header **solo in memoria**.
3. Ogni mutazione invia l'header indicato da `headerName`.
4. Dopo login (e quando necessario) il CSRF viene invalidato/rinfrescato.
5. Su `403 CSRF_VALIDATION_FAILED` è consentito **un solo** retry dopo refresh del token; non ci sono retry generici illimitati.
6. Nessuna persistenza su disco o storage del browser.

Le mutation Profile/Status riusano questa foundation (`csrfMutation`): non duplicano ensure/retry CSRF nella pagina.

La configurazione CSRF backend resta in [`docs/09-security-flow.md`](../09-security-flow.md).

## 9. Mutua esclusione delle transizioni auth

Login e logout condividono un **lock globale** (`AuthTransitionInProgressError`):

- una sola transizione auth può essere attiva;
- login/login, login/logout e logout/login concorrenti sono rifiutati;
- non esiste una coda automatica;
- la seconda richiesta viene rifiutata **prima** di avviare una nuova mutazione dello stato auth/epoch.

Questo lock **non** è lo stesso meccanismo usato dalla ProfilePage per serializzare salvataggio profilo e aggiornamento status (vedi §11.4).

## 10. Login

Lifecycle reale:

1. inizio di una nuova transizione auth (epoch aggiornata);
2. ensure CSRF;
3. `POST /api/v1/auth/login` con CSRF;
4. successo `204` (nessun body token);
5. clear / refresh CSRF;
6. hydration con `/me/account` e `/me/profile`;
7. commit `authenticated`;
8. redirect sicuro verso destinazione allowlistata sotto `/app/client/...` o `/app/professional/...`.

Distinzioni importanti:

- **login rejected** (`401`/`403` di autenticazione o account non idoneo) → resta/form `unauthenticated` con motivo `login-rejected`;
- **post-login session missing / unavailable** → il login HTTP può essere riuscito ma la sessione non è utilizzabile o verificabile → stati dedicati, non "successo silenzioso".

Il contratto server (cookie, readiness, timeout) è in [`docs/09-security-flow.md`](../09-security-flow.md).

### 10.1 Safe redirect post-login e follow-up E2E-1

Dopo login riuscito, il client può ripristinare una destinazione interna **safe** memorizzata dallo stato di navigazione (allowlist sotto `/app/client/...` o `/app/professional/...`).

Il safe redirect valida canonicalità e sicurezza dell’URL interno (no open redirect). **Non** valida che il target sia compatibile con il nuovo `accessProfile` (ruolo/specializzazione).

Follow-up **E2E-1** (MINOR, non bloccante):

- un target ricordato da una sessione precedente può essere incompatibile col ruolo della nuova sessione;
- esempio: route CLIENT → logout → login PROFESSIONAL → target CLIENT ancora considerato URL interno sicuro → `RequireRole` → `/forbidden`;
- `RequireRole` / `RequireSpecialization` bloccano correttamente: **nessun bypass autorizzativo**;
- impatto: UX (pagina forbidden invece dell’home di ruolo);
- remediation non decisa in questo documento; follow-up auth/routing separato.

## 11. Soft commit Profilo / Operational Status

### 11.1 Ruolo rispetto all’auth

La ProfilePage è la prima pagina business autenticata reale. Usa `AuthProvider` come source of truth:

1. legge `account` / `profile` autenticati;
2. mantiene un **draft locale** solo durante l’editing;
3. valida e invia un PATCH differenziale;
4. riceve `MyProfileResponse` dal server;
5. applica uno **soft commit** nella source of truth auth.

Il server response è autoritativo. **Non** c’è optimistic update. **Non** c’è refetch automatico di `/me/account` + `/me/profile` come conseguenza del salvataggio.

### 11.2 Flow

```text
AuthProvider snapshot (authenticated)
  → draft locale (temporaneo)
  → validazione client
  → PATCH /api/v1/me/profile
     oppure PATCH /api/v1/me/profile/operational-status
  → response MyProfileResponse
  → applyProfileSnapshot(profile, expectedEpoch)
```

Motivazione del pattern:

- applicare direttamente lo snapshot server autoritativo;
- evitare un dual GET `/me/*` non necessario post-PATCH;
- mantenere una sola source of truth auth;
- proteggere l’applicazione della response da cambi di lifecycle tramite auth epoch.

### 11.3 `applyProfileSnapshot(profile, expectedEpoch)`

Dopo una mutation riuscita, `AuthProvider.applyProfileSnapshot`:

- è consentito **solo** da stato `authenticated`;
- confronta `expectedEpoch` con l’epoch corrente;
- se l’epoch non coincide → `StaleAuthOperationError` e **nessun** aggiornamento dello stato;
- se coerente: aggiorna `profile` con la response server, **preserva** `account`, ricalcola `accessProfile`;
- **non** esegue bootstrap, reconcile o GET;
- **non** avanza l’auth epoch;
- **non** modifica il CSRF.

`expectedEpoch` viene catturato dalla ProfilePage **prima** del PATCH (`currentEpoch()`). Se nel frattempo la sessione cambia (logout, nuovo login, invalidazione), la response non viene applicata al lifecycle corrente: nessun falso successo UI e nessun reconcile forzato dalla pagina.

### 11.4 Serializzazione mutation nella ProfilePage

Nella ProfilePage è consentita **una sola** transizione business Profilo/Status alla volta (salvataggio profilo e aggiornamento status non concorrenti; doppio submit bloccato).

Questo meccanismo è **locale alla pagina** e distinto dal lock globale login/logout (§9).

### 11.5 Route Profile

| Area | Route |
|---|---|
| CLIENT | `/app/client/profile` |
| PROFESSIONAL | `/app/professional/profile` |

Campi editabili, Account read-only, enum Operational Status e confini funzionali: [`docs/01-functional-scope.md`](../01-functional-scope.md) e [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md).

## 12. Logout

`POST /api/v1/auth/logout` con CSRF. Il server invalida la **sessione corrente** (non revoke-all; vedi docs/09). Esiti frontend:

### Successo `204`
Logout completato: stato locale `unauthenticated` con reason `logout-completed`. La navigazione resta responsabilità di guards/router.

### Secondo `CSRF_VALIDATION_FAILED`
Dopo l'unico retry CSRF consentito, un secondo fallimento CSRF è deterministico: il client invalida/pulisce il CSRF locale, **non** assume chiusura della sessione server e, se l'operazione è ancora corrente, ripristina lo snapshot `authenticated` precedente. Non è `unauthenticated` né `unavailable`.

### Errore indeterminato
Quando il client non può sapere se il logout server-side sia avvenuto (es. network, `401`, `5xx`), lo stato diventa `unavailable` (`logout-indeterminate`): non si ripristina semplicemente `authenticated` e non si assume anonimo.

### Risultato stale
Se nel frattempo l'epoch/transizione è diventata stale, il vecchio lifecycle **non** ripristina snapshot né sovrascrive lo stato più recente.

## 13. Guards

| Guard | Ruolo |
|---|---|
| `RequireAuth` | Consente l'outlet solo se `authenticated`; gestisce `initializing` e `unavailable`; redirect a `/login` con destinazione sicura quando anonimo |
| `RequireRole` | Verifica `CLIENT` o `PROFESSIONAL`; fail-closed verso `/forbidden` |
| `RequireSpecialization` | Vincola aree PT-only (availability/bookings professionista) a `PERSONAL_TRAINER` |

Le guard migliorano UX e routing; **non** sostituiscono l'autorizzazione backend.

Le route Profile reali (`/app/client/profile`, `/app/professional/profile`) sono protette da `RequireAuth` + `RequireRole` del ruolo corrispondente.

## 14. Stato `unavailable`

`unavailable` significa: la sessione non può essere classificata in modo affidabile come autenticata o assente.

- non si deve forzare un trattamento "anonimo" che rischi di perdere contesto o mascherare un outage;
- `AuthUnavailableBoundary` presenta retry tramite `reconcileSession()`;
- le aree protette restano fail-closed finché la verifica non riesce.

## 15. Development proxy

In sviluppo Vite espone:

- frontend: `http://localhost:5173`
- proxy: `/api` → `http://localhost:8080`

Il client continua a usare path relativi `/api/v1/...` e `credentials: 'same-origin'`, così cookie e CSRF restano coerenti con il modello same-origin anche in locale. In produzione la topologia prevista è same-origin dietro reverse proxy.

## 16. Maturity (sintesi)

| Area | Stato |
|---|---|
| Foundation auth (httpClient, CSRF, AuthProvider, login, logout, guards, bootstrap) | **Implementata** |
| Soft commit profilo / operational status (`applyProfileSnapshot`) | **Implementato** |
| Home pubblica | **Implementata** |
| ProfilePage CLIENT (`/app/client/profile`) | **Reale** |
| ProfilePage PROFESSIONAL (`/app/professional/profile`) | **Reale** |
| Account (sezione read-only sulla ProfilePage) | **Reale** |
| Operational Status (aggiornamento indipendente) | **Reale** |
| Altre pagine business (dashboard dati, clients, professionals, availability, bookings) | **Placeholder** |
| Flussi pubblici di registrazione / invito / verify-email | **Placeholder** (route presenti, senza integrazione API completa) |

La matrice completa delle pagine resta in [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md). Il follow-up UI **M1-R** (fieldErrors dopo update Status) è high-level in [`docs/01-functional-scope.md`](../01-functional-scope.md) e nel dettaglio frontend/UX in [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md); FE03 non lo tratta come backlog dedicato. Il dettaglio tecnico di **E2E-1** resta in §10.1.

## 17. Confini

FE03 **non** è fonte per:

- flag cookie, timeout e readiness server;
- schema DB o migrazioni;
- catalogo endpoint completo;
- roadmap business futura;
- elenco campi profilo CLIENT/PROFESSIONAL, layout CSS o design system;
- enum Operational Status e confini Account oltre quanto necessario al lifecycle auth.

Riferimenti primari:

- [`docs/09-security-flow.md`](../09-security-flow.md)
- [`docs/08-endpoint-map.md`](../08-endpoint-map.md)
- [`docs/10-database-schema.md`](../10-database-schema.md)
- [`docs/15-planned-endpoints-roadmap.md`](../15-planned-endpoints-roadmap.md)
- [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md)
- [`docs/01-functional-scope.md`](../01-functional-scope.md)
