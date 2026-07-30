# Professional Invites Implementation — Frontend

## 1. Scopo e maturity

Questo documento è la **source of truth tecnica frontend** dello slice autenticato **Gestione inviti PROFESSIONAL**.

Documenta:

- workflow e route;
- UI e stati;
- modello stato invito;
- temporalità (timer one-shot, clock refresh, foreground);
- concurrency GET/POST (lock sincroni), reconciliation ownership;
- stale auth;
- clipboard policy;
- navigation e responsive narrow;
- accessibilità;
- foundation riusate;
- test;
- out-of-scope.

Non documenta:

- catalogo endpoint completo → [`docs/08-endpoint-map.md`](../08-endpoint-map.md);
- security/session/CSRF backend → [`docs/09-security-flow.md`](../09-security-flow.md);
- lifecycle sessione generico → [`03-authentication-session-flow.md`](./03-authentication-session-flow.md);
- mappa funzionale complessiva → [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md).

### Implementato

- route `/app/professional/invites` (`RequireRole(PROFESSIONAL)`, senza specialization);
- `GET /api/v1/invites` → lista / empty / loading / error + retry;
- `POST /api/v1/invites` → genera (nessun body, CSRF via foundation);
- stati UX: Non attivo → Usato → Scaduto → Non disponibile (timestamp invalido) → Valido;
- clock UI riallineato a ogni commit intenzionale di dataset (GET/reconcile/create);
- timer one-shot + `visibilitychange`;
- copia codice **solo** per `Valido`;
- primary nav Inviti per PERSONAL_TRAINER e NUTRITIONIST;
- nav narrow (`max-width: 47.99rem`): griglia a 3 colonne / due righe per PT.

### Fuori scope

- revoke / deactivate / delete / modifica invito;
- email/SMS/WhatsApp/share;
- onboarding CLIENT / validate-invite / register CLIENT;
- clients/professionals/availability/booking/dashboard dati;
- E2E-1, M1-R;
- nuove API backend.

## 2. Workflow

`Login PROFESSIONAL` → `/app/professional/invites` → caricamento lista → genera → visualizza codice/stato/scadenza → copia se Valido → handoff fuori app verso futuro onboarding CLIENT.

La validità **reale** del codice resta autoritativa sul server.

## 3. Contratti usati (semantica)

Catalogo: [`docs/08-endpoint-map.md`](../08-endpoint-map.md) § Invites.

| Operazione | Endpoint | Note FE |
|------------|----------|---------|
| Lista | `GET /api/v1/invites` | `listMyInvites`; `invalidateOn401: true`; `AbortSignal`; empty `[]` |
| Crea | `POST /api/v1/invites` | `createInvite` via `performCsrfMutation`; nessun body; nessun Content-Type artificiale; `invalidateCsrfOnCommit: false` |

DTO: `id`, `code`, `professionalId`, `expiresAt`, `used`, `usedAt`, `active`, `createdAt`.
`professionalId` non esposto in UI. Instant UTC con `Z`.

## 4. Stato invito

Helper puro `deriveInviteDisplayStatus`:

1. `active === false` → **Non attivo**
2. else `used === true` → **Usato**
3. else `expiresAt` non parseable → **Non disponibile** (fail-closed; mai Valido; nessuna Copia)
4. else `expiresAtMs <= nowMs` → **Scaduto**
5. else → **Valido**

## 5. Temporalità

- `nowMs` in state;
- **clock refresh**: ogni commit intenzionale di dataset (GET success, reconcile success, POST `201`) chiama `Date.now()` e aggiorna `nowMs` prima/insieme al merge lista;
- **un** timer one-shot verso la prossima scadenza Valido (delay clampato a `2^31-1`, poi re-check);
- al fire: aggiorna `nowMs`, ricalcola, riprogramma;
- cleanup su dataset change / unmount;
- `document.visibilitychange` → `visible`: aggiorna `nowMs` e riprogramma;
- niente polling / `setInterval` / timer per card.

## 6. Concurrency e reconciliation

### Generate disponibile solo se

`listStatus === 'success'` **e** nessuna list op in corso **e** create non locked/pending **e** non `outcome-unconfirmed` **e** non gate chiuso (stale auth hold).

Dopo load **error**: Generate OFF.

### Lock GET sincrono (`listLockRef`)

Acquisito **prima** del primo `await` di initial/retry/reconcile.
Create controlla `listLockRef` prima del POST.
Retry/GET manuale non partono se create lock attivo (salvo reconcile controllato).

### Lock create (`createLockRef` + `createGateClosed`)

Acquisito prima del primo `await` del POST. Una azione locale pendente → al massimo un POST.

### Success `201`

Prepend + dedupe `id`; bump generation; clock refresh; no refetch obbligatorio.

### Ambiguous network

`outcome-unconfirmed` + messaggio di non conferma; Generate OFF; reconcile automatica.

### Ownership reconciliation (`reconcileAttemptRef`)

Solo il tentativo corrente può: commitare lista, clear ambiguity, rilasciare create lock, oppure mantenere ambiguity + Aggiorna elenco. Tentativi stale non mutano stato/lock/lista.

### StaleAuthOperationError

Non è known-error né successo né ambiguity. Nessun messaggio “non generato” / “Invito generato”; nessuna seconda create dalla pagina; gate create resta chiuso; la transizione auth foundation rende obsoleta l’operazione.

## 7. Clipboard

CTA Copia solo su **Valido**. `navigator.clipboard.writeText`; failure senza fallback automatici/persistenti.

## 8. Navigation / responsive

- PT: `Dashboard → Clienti → Inviti → Disponibilità → Prenotazioni → Profilo`
- NUT: Inviti invariata
- rimosso aside “Accesso secondario” da Clients
- CSS: sotto `48rem` la bottom nav usa griglia a 3 colonne (due righe per le 6 voci PT), `min-height: 2.75rem` (~44px), `overflow-wrap`, focus-visible. Desktop (`min-width: 48rem`) resta colonna laterale.

Verifica reale (CDP Emulation + `/dev/role-preview` Personal Trainer):

| Viewport | overflow-x | layout | target height | esito |
|----------|------------|--------|---------------|-------|
| 320px | no | 3×2 | 44px | OK — label leggibili, Inviti presente |
| 375px | no | 3×2 | 44px | OK — dopo fix breakpoint `max-width: 47.99rem` |

## 9. Foundation riusate

`httpClient.request`, `performCsrfMutation`, error model, sessionInvalidation/AuthProvider (401), `RequireRole`, layout autenticato, `PageTemplate`.

Non modificati: AuthProvider, httpClient, csrfMutation, backend, ACL.

## 10. Test

- API GET/POST (no body/Content-Type); helper stato inclusa Non disponibile; classificazione `inviteErrors`;
- pagina: load error → Generate OFF; retry+generate same-tick → 0 POST; N-01 clock refresh; timer multi-scadenza; visibility; cleanup timer (`clearTimeout` del timer slice) e `visibilitychange` all’unmount; GET in-flight stale dopo prepend 201; create lock; stale auth; ambiguous+reconcile; ownership reconcile A/B sovrapposti (B success→A fail stale; B fail→A success stale); clipboard; Non disponibile senza Copia.

## 11. File principali

- `frontend/src/api/invitesTypes.ts`, `invitesApi.ts`
- `frontend/src/pages/professional/ProfessionalInvitesPage.tsx`
- `frontend/src/pages/professional/inviteStatus.ts`, `inviteErrors.ts`
- `frontend/src/components/navigation/navigationConfig.ts`
- `frontend/src/components/navigation/AuthenticatedNavigation.module.css`
