# Security Flow — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce il flusso di sicurezza **attualmente implementato** nella v1 di Support Trainer.

Lo scopo è chiarire:
- come avviene l’autenticazione server-side
- come vengono gestiti sessione HTTP, cookie e CSRF
- come viene gestita l’autorizzazione
- quali endpoint sono pubblici e quali protetti
- come funzionano verifica email, login, logout e readiness dinamica
- quali controlli spettano a Spring Security e quali al service layer

---

## 2. Principi guida

Nello stato attuale del progetto, il sistema adotta i seguenti principi:

- autenticazione con **Spring Security 7** e **Spring Session JDBC**
- sessione **server-side** (non JWT runtime)
- cookie di sessione **HttpOnly**; nessuna credenziale Bearer
- CSRF abilitato sulle mutazioni
- distinzione chiara tra:
  - **autenticazione**
  - **autorizzazione**
  - **business authorization**
- protezione degli endpoint sensibili
- verifica email obbligatoria e reinvio uniforme per professionista e cliente
- controlli business aggiuntivi nel service layer sulle risorse accessibili
- topologia di produzione **same-origin** dietro reverse proxy (`/` frontend, `/api/v1/**` backend): CORS browser non è richiesto in produzione

---

## 3. Modello di autenticazione

## 3.1 Strategia scelta
Il sistema usa:

- **Spring Security 7**
- **Spring Session JDBC** (schema creato da Flyway V7)
- cookie di sessione HttpOnly
- token CSRF di sessione

Non esiste runtime JWT: nessun `accessToken`, `refreshToken`, header `Authorization: Bearer` né dipendenza JJWT.

## 3.2 Obiettivo
L’utente, dopo login valido, ottiene una sessione server-side. Il browser conserva solo il cookie di sessione; il client applica il CSRF alle mutazioni.

## 3.3 Topologia
In produzione il browser parla same-origin con un reverse proxy che espone:

- `/` → frontend
- `/api/v1/**` → backend

CORS applicativo è disabilitato (`cors.disable()`). Non è un meccanismo richiesto per l’autenticazione browser in produzione.

---

## 4. Ruoli e specializzazione

## 4.1 Ruoli di sicurezza
I ruoli realmente usati nel codice sono:

- `PROFESSIONAL`
- `CLIENT`

## 4.2 Specializzazione professionista
La specializzazione non è un ruolo Spring Security separato, ma un attributo business del professionista:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

## 4.3 Regola pratica

I ruoli servono per:

- controllare l’accesso generale alle aree applicative.

La specializzazione serve per:

- applicare regole business specifiche sulle funzionalità professionali.

### Esempi implementati

- un utente con `PROFESSIONAL` può accedere agli endpoint dell’area professionista secondo `SecurityConfig`;
- un utente con `CLIENT` può accedere agli endpoint dell’area cliente;
- solo un professionista `PERSONAL_TRAINER` può creare e gestire slot availability;
- uno slot appartenente a un professionista `NUTRITIONIST` non può essere prenotato;
- un booking anomalo già esistente su slot di un `NUTRITIONIST` non può essere confermato.

---

## 5. Stato account e accesso

## 5.1 Account professionista e cliente
Alla registrazione entrambi i ruoli nascono con:
- `accountStatus = PENDING_VERIFICATION`
- `emailVerified = false`

## 5.2 Attivazione uniforme
Solo dopo verifica email:
- `accountStatus = ACTIVE`
- `emailVerified = true`

## 5.3 Blocco operativo e readiness
Il login e il mantenimento della sessione richiedono account `ACTIVE` ed `emailVerified=true`. Il flag `profile.active=false` **non** blocca login né invalidazione sessione per readiness di autenticazione.

`SessionAuthenticationStateFilter` rivaluta su ogni richiesta autenticata:

- esistenza dell’utente;
- `accountStatus = ACTIVE`;
- `emailVerified = true`;
- coerenza ruolo/authority;
- timeout assoluto di 8 ore da `authenticatedAt`.

Il profilo attivo resta richiesto per Availability, Booking, inviti e letture relazionali. Gli endpoint self-service `/api/v1/me/**` richiedono account attivo ed email verificata, ma non bloccano la consultazione o l'aggiornamento dello stato operativo del proprio profilo quando `active=false`.

## 5.4 Cliente
Il cliente può registrarsi solo tramite codice invito valido. La registrazione crea subito link e token e consuma l'invito, ma l'account resta pending e il professionista non può leggerlo fino alla conferma. La nuova regola riguarda le nuove registrazioni e non migra i clienti già salvati.

---

## 6. Sessione, cookie e timeout

## 6.1 Store di sessione
Le sessioni autenticate sono persistite via **Spring Session JDBC**. Lo schema (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`) è creato da Flyway **V7**; `spring.session.jdbc.initialize-schema=never`.

## 6.2 Cookie di sessione

### Produzione (esempio tracciato)
- nome: `__Host-STSESSION`
- `Secure=true`
- `HttpOnly=true`
- `SameSite=Strict`
- `Path=/`
- nessun `Domain`
- cookie di sessione (non persistente; nessun `Max-Age` applicativo)

### Locale / test
- nome: `STSESSION`
- `Secure=false`
- `HttpOnly=true`
- `SameSite=Strict`
- `Path=/`

Il browser gestisce il cookie; il frontend non lo legge né lo scrive in `localStorage`/`sessionStorage`.

## 6.3 Timeout
- **inattività:** 30 minuti (`spring.session.timeout=30m`)
- **assoluto:** 8 ore dall’attributo di sessione `authenticatedAt`, valutato da `SessionAuthenticationStateFilter`

Se la readiness o il timeout assoluto falliscono, la sessione viene invalidata e la richiesta riceve `401 UNAUTHORIZED`.

## 6.4 Attributi di sessione rilevanti
Al login valido viene impostato `authenticatedAt` (Instant). Il CSRF token di sessione viene ruotato dalle strategie di autenticazione sessione (rotazione session id + invalidazione CSRF).

## 6.5 Policy sessioni multiple (MVP)
Nell’MVP corrente **non** è configurato alcun limite di concorrenza sulle sessioni (`maximumSessions` / `SessionRegistry` / espulsione della sessione più vecchia assenti).

Comportamento reale:

- sessioni indipendenti da client/browser distinti **possono coesistere** per lo stesso utente;
- inactivity timeout: **30 minuti**;
- lifetime assoluta: **8 ore** da `authenticatedAt`;
- il logout invalida **solo la sessione corrente** (quella del cookie presentato);
- non esiste oggi gestione dispositivi/sessioni attive né un revoke-all implicito al logout.

Limiti concorrenti, gestione dispositivi ed eventuale revoca globale delle sessioni restano **fuori dall’MVP attuale**.

---

## 7. CSRF

## 7.1 Endpoint
`GET /api/v1/auth/csrf` restituisce:

```json
{ "token": "...", "headerName": "X-CSRF-TOKEN" }
```

con `Cache-Control: no-store`. `headerName` è quello esposto dal token Spring (tipicamente `X-CSRF-TOKEN`).

## 7.2 Uso
Le mutazioni (POST/PATCH e logout) devono inviare l’header CSRF indicato da `headerName`. Un fallimento CSRF produce `403` con codice `CSRF_VALIDATION_FAILED`.

La foundation frontend può invalidare il token, ottenerne uno nuovo e ripetere **una sola volta** la mutation dopo quella coppia esatta status/code. Il replay è tecnico e centralizzato: le pagine non implementano un secondo livello di retry applicativo.

## 7.3 Rotazione dopo login
Il login invalida/ruota il CSRF di sessione. Dopo un login riuscito il client deve richiamare `GET /api/v1/auth/csrf` e usare il nuovo token. Il token CSRF va tenuto solo in memoria, non in storage persistente del browser.

---

## 8. Endpoint pubblici e protetti

## 8.1 Endpoint pubblici
In base al codice attuale, gli endpoint pubblici effettivamente implementati sotto `/api/v1/auth/**` sono:

- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/email-verification/resend`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register/client/validate-invite`
- `POST /api/v1/auth/logout` (URL di logout Spring Security; richiede CSRF; risponde `204` e invalida la sessione)

## 8.2 Regola generale in SecurityConfig
Nel codice, Spring Security consente pubblicamente:
- `/error`
- `/api/v1/auth/**`

Swagger UI e OpenAPI non sono pubblici: senza autenticazione rispondono `401`; non essendo esposti dall'applicazione, con sessione valida rispondono con il `404` uniforme.

## 8.3 Endpoint protetti
Tutti gli altri endpoint richiedono una sessione autenticata valida (cookie + readiness), salvo regole più specifiche sui ruoli. Le mutazioni richiedono anche CSRF.

---

## 9. Flusso registrazione professionista

## 9.1 Step principali
1. il professionista invia richiesta di registrazione
2. il backend valida i dati
3. il sistema verifica in modo neutro se l’email sia già registrata
4. solo per una nuova email crea il professionista con stato `PENDING_VERIFICATION`, token e richiesta email after-commit
5. per un’email già esistente non muta account, profilo o token
6. la registrazione restituisce sempre `202 Accepted` con lo stesso DTO neutro, senza sessione di login, ID, ruolo o email
7. il professionista deve poi verificare l’email tramite endpoint dedicato oppure usare resend

## 9.2 Regola importante
Prima della verifica email:
- il professionista non può effettuare login operativo
- l’account non è ancora attivo

---

## 10. Flusso registrazione cliente con invito

## 10.1 Step principali
1. il cliente invia i dati di registrazione insieme al codice invito
2. il backend acquisisce e valida con lock il codice invito
3. il backend verifica il professionista associato e solo dopo controlla l'email in modo neutro
4. per un'email già esistente, con invito valido, restituisce `202` senza consumare invito, creare link, token o messaggio
5. per una nuova email il backend crea l’account cliente
6. il cliente viene impostato come:
   - `PENDING_VERIFICATION`
   - `emailVerified = false`
7. il backend crea il collegamento `ProfessionalClientLink`
8. il backend marca il codice invito come usato
9. il backend crea il token email da 24 ore
10. la registrazione restituisce `202 Accepted` con il DTO neutro

## 10.2 Regola importante
La registrazione cliente può essere completata solo con codice invito:
- esistente
- attivo
- non usato
- non scaduto

---

## 11. Flusso verifica email

## 11.1 Step principali
1. il professionista o cliente invia `POST /api/v1/auth/email-verification/confirm` con il token nel body
2. il backend cerca il token
3. il backend verifica che il token:
   - esista
   - non sia già usato, salvo stato finale già coerente
   - non sia scaduto
4. il backend aggiorna l’utente:
   - `emailVerified = true`
   - `accountStatus = ACTIVE`
5. il backend marca il token come usato e valorizza `usedAt`
6. un secondo POST sullo stesso stato coerente restituisce 200 senza modificare nuovamente `usedAt`

## 11.2 Errori gestiti
Il flusso gestisce almeno questi casi:
- body o campo `token` obbligatorio mancante/non valido
- token non trovato
- token usato con stato incoerente
- token scaduto, restituito come `410 Gone`

## 11.3 Reinvio uniforme

1. il chiamante invia `POST /api/v1/auth/email-verification/resend` con l'email nel body;
2. il service normalizza l'email e acquisisce un lock pessimista sull'utente, se esiste;
3. account inesistenti, verificati, non pending, incoerenti, con profilo inattivo o in cooldown terminano senza mutazioni;
4. per un account idoneo, se sono trascorsi almeno 60 secondi dal token più recente, i token non usati vengono marcati `used=true` e `usedAt=now`;
5. viene creato un solo nuovo token UUID v4 con durata esatta di 24 ore;
6. ogni richiesta sintatticamente valida restituisce lo stesso `202 Accepted`, senza email, ruolo, stato, cooldown o token.

Al boundary `now == latestToken.createdAt + 60 secondi` il reinvio è consentito. L'invalidazione tramite `used/usedAt` è un compromesso semantico dello schema esistente. Inviti e collegamenti non cambiano. Dopo la persistenza del token, Auth pubblica un evento immutabile nella transazione; il listener sincrono con `fallbackExecution=false` costruisce il link `#token=...` e chiama il sender soltanto `AFTER_COMMIT`. Rollback ed eventi senza transazione non producono invii. Gli errori sono assorbiti e registrati soltanto con correlation ID, motivo e tipo, senza email, token, URL o stack trace. Il sender SMTP reale usa JavaMail con credenziali esterne e trasforma i fallimenti di preparazione/consegna in un'eccezione sanitizzata; consegna durevole, retry e rate limiting distribuito non sono implementati.

## 11.4 Invarianti frontend (onboarding pubblico)

Il client pubblico di onboarding PROFESSIONAL e CLIENT e la verifica email rispettano almeno questi invarianti:

- neutralità UX di registration e resend (nessuna enumerazione account);
- CSRF sulle mutazioni pubbliche di validate-invite / register / confirm / resend;
- token di verifica trasportato nel fragment URL e sanitizzato immediatamente dall’address bar, anche se il fragment è invalido;
- token memory-only: nessun `localStorage` / `sessionStorage`, nessun router state, nessuna esposizione in DOM/ARIA/log;
- codice invito CLIENT conservato soltanto in un provider React limitato a `/invite/validate` e `/register/client`; nessun query parameter, fragment, pathname dinamico, `location.state` o storage del browser;
- accesso diretto/reload a `/register/client` senza invito memory-only fail-closed con redirect `replace` a `/invite/validate`;
- gate locale sulle route CLIENT: `initializing` non monta le pagine, `unavailable` resta fail-closed, `authenticated` pulisce l'invito e redirige alla dashboard del ruolo, `unauthenticated` consente il flusso;
- validazione invito con successo soltanto su `200` e body runtime-decoded coerente; rete, `5xx`, body illeggibile e response anomale falliscono chiuse;
- registrazione CLIENT confermata soltanto su `202`, indipendentemente dal body; `5xx`, altri `2xx`, transport e coppie status/code incoerenti restano outcome ambigui;
- una sola chiamata applicativa a `registerClient` per submit, nessun retry della pagina e al massimo un replay tecnico CSRF centralizzato;
- secondo consumo email coerente gestito idempotentemente dal backend; il frontend tratta come successo solo una risposta di successo;
- password e draft CLIENT rimossi dallo state frontend negli outcome terminali insieme all'invito; al massimo resta separata la sola email normalizzata necessaria al resend;
- reinvio CLIENT neutro, fenced same-tick e con cooldown UX di 60 secondi basato su deadline, senza ripristino dell'invito;
- nessun auto-login dopo register o verify: la sessione autenticata nasce solo con login.

Dettaglio tecnico frontend: [`docs/frontend/04-professional-onboarding-implementation.md`](frontend/04-professional-onboarding-implementation.md) per PROFESSIONAL/Verify e [`docs/frontend/06-client-onboarding-implementation.md`](frontend/06-client-onboarding-implementation.md) per CLIENT.

---

## 12. Flusso login

## 12.1 Step principali
1. il client ottiene un CSRF valido (`GET /api/v1/auth/csrf`) e lo invia nell’header richiesto
2. l’utente invia email e password a `POST /api/v1/auth/login`
3. il backend normalizza l’email
4. il backend rifiuta come credenziali non valide una password oltre 72 byte UTF-8
5. `AuthenticationManager` autentica le credenziali
6. il backend recupera l’utente e verifica l’eligibilità: `ACTIVE` + `emailVerified=true` (`profile.active` non è controllato)
7. se tutto è valido, crea la sessione server-side, ruota l’id di sessione, invalida il CSRF precedente e imposta `authenticatedAt`
8. la risposta è `204 No Content` senza body e senza token

## 12.2 Controlli aggiuntivi
Nel login vengono verificati almeno:
- credenziali corrette
- utente esistente
- account `ACTIVE`
- email verificata (entrambi i ruoli)

## 12.3 Dopo il login
Il client deve:
1. richiamare `GET /api/v1/auth/csrf` (token ruotato);
2. fare bootstrap con `GET /api/v1/me/account` e `GET /api/v1/me/profile`.

Non esistono `accessToken`, `refreshToken`, `tokenType` né header `Authorization`.

---

## 13. Logout

## 13.1 Stato attuale
`POST /api/v1/auth/logout` è implementato tramite Spring Security logout:

- richiede CSRF;
- invalida la sessione HTTP;
- cancella l’autenticazione;
- risponde `204 No Content`.

## 13.2 Significato pratico
Il logout invalida **la sessione corrente** server-side (cookie presentato). Non è un revoke-all: altre sessioni indipendenti dello stesso utente restano valide finché non scadono o non vengono invalidate separatamente. Il client deve scartare il CSRF in memoria e tornare allo stato anonimo. Non esiste revoca di JWT perché non esiste JWT runtime.

---

## 14. Forgot password / reset password

## 14.1 Stato attuale
Nel codice attuale **forgot password** e **reset password** **non sono implementati**.

## 14.2 Implicazione documentale
Questi flussi non devono essere considerati parte della sicurezza già disponibile nel progetto corrente.

---

## 15. Autenticazione vs autorizzazione

## 15.1 Autenticazione
Risponde alla domanda:
- **chi sei?**

È gestita da:
- login session-based
- verifica credenziali
- cookie di sessione Spring Session
- `SessionAuthenticationStateFilter`
- filter chain di Spring Security

## 15.2 Autorizzazione
Risponde alla domanda:
- **puoi entrare in questa area?**

È gestita da:
- authority utente
- protezione endpoint in `SecurityConfig`

## 15.3 Business authorization
Risponde alla domanda:
- **puoi davvero agire su questa specifica risorsa?**

Esempi già implementati:
- questo professionista può vedere questo cliente?
- questo cliente può vedere questo professionista?
- questo utente autenticato è davvero del tipo richiesto dalla funzionalità?

Questa parte non basta farla con i ruoli.  
Va controllata nel **service layer**.

---

## 16. Protezione endpoint per ruolo

## 16.1 Regole attualmente implementate in SecurityConfig

Le regole base reali sono:

- `/api/v1/auth/**` → pubblico
- `/api/v1/clients/**` → solo `PROFESSIONAL`
- `/api/v1/invites/**` → solo `PROFESSIONAL`
- `/api/v1/availability/**` → solo `PROFESSIONAL`
- `/api/v1/professionals/**` → solo `CLIENT`
- `/api/v1/me/**` → qualsiasi utente autenticato

### Booking

Le regole Booking sono definite in modo più specifico:

- `POST /api/v1/bookings` → solo `CLIENT`
- `GET /api/v1/bookings/client` → solo `CLIENT`
- `GET /api/v1/bookings/professional` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject` → solo `PROFESSIONAL`
- `GET /api/v1/bookings/{bookingRequestId}` → utente autenticato, con controllo ownership nel service
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel` → utente autenticato, con controllo ownership e stato nel service

Tutto il resto richiede autenticazione valida.

---

## 16.2 Esempi reali di accesso

### Solo professionista

- `GET /api/v1/clients/my`
- `GET /api/v1/clients/{clientId}`
- `POST /api/v1/invites`
- `GET /api/v1/invites`
- `POST /api/v1/availability`
- `GET /api/v1/availability/my`
- `PATCH /api/v1/availability/{slotId}`
- `PATCH /api/v1/availability/{slotId}/block`
- `PATCH /api/v1/availability/{slotId}/unblock`
- `GET /api/v1/bookings/professional`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject`

### Solo cliente

- `GET /api/v1/professionals/my`
- `GET /api/v1/professionals/{professionalId}`
- `GET /api/v1/professionals/{professionalId}/availability`
- `POST /api/v1/bookings`
- `GET /api/v1/bookings/client`

### Entrambi, se coinvolti nella risorsa

- `GET /api/v1/bookings/{bookingRequestId}`
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel`

### Entrambi autenticati

- `GET /api/v1/me/profile`
- `GET /api/v1/me/account`
- `PATCH /api/v1/me/profile`
- `PATCH /api/v1/me/profile/operational-status`

### Anti-enumerazione nei dettagli profilo

I dettagli `GET /api/v1/clients/{clientId}` e `GET /api/v1/professionals/{professionalId}` interrogano direttamente il perimetro accessibile al principal. La query combina ID richiesto, ID del principal, collegamento attivo e stati di account/profilo già richiesti dal dominio; non esegue prima un lookup del solo ID né una query di esistenza usata per scegliere un errore diverso.

Un risultato vuoto produce sempre il medesimo 404 specifico dell'endpoint, sia per ID inesistente sia per collegamento assente o inattivo e profilo non leggibile. La policy non modifica i confini di Spring Security: richiesta anonima e sessione non valida restano 401, mentre un ruolo non ammesso sull'endpoint resta 403. Gli stati operativi, come `PAUSA` o `FERIE`, restano informazioni di dominio e non sono criteri di occultamento del dettaglio.

Le guard di ruolo frontend impediscono navigazioni incoerenti e richieste evitabili, ma non sostituiscono l'autorizzazione backend: lo scope della relazione attiva e la neutralità del 404 sono applicati dal server.

### Minimizzazione del profilo cliente condiviso

Superato il controllo scoped, il professionista non riceve l'entity completa:

- la lista espone soltanto `id`, `firstName`, `lastName` e `profileImageUrl`;
- il dettaglio aggiunge `primaryGoal`, `operationalStatus`, `birthDate`, `heightCm` e `gender`;
- `medicalNotes`, `injuryNotes`, `notes`, flag `active`, dati account, dati tecnici del collegamento e audit non vengono serializzati;
- `PERSONAL_TRAINER` e `NUTRITIONIST` ricevono intenzionalmente lo stesso contratto condiviso.

Il profilo owner `/me` resta separato e completo. Le note sensibili sono persistite ma non condivise nel contratto corrente; un'eventuale condivisione richiede una decisione futura dedicata. La modifica non introduce consenso, scope, revoca, audit delle visualizzazioni o una differenziazione per specializzazione.

---

## 16.3 Nota su SecurityConfig e service layer

`SecurityConfig` controlla il ruolo minimo necessario per entrare nell’area corretta.

Il service layer controlla invece:

- account attivo;
- email verificata quando richiesta;
- profilo attivo;
- specializzazione del professionista per Availability e Bookings;
- ownership della risorsa;
- relazione attiva professionista-cliente;
- stato e validità temporale dello slot;
- stato del booking;
- transizioni consentite;
- blocco di booking e conferme su slot non coerenti con la specializzazione prevista.

---

## 17. Protezione per specializzazione

## 17.1 Stato attuale

La specializzazione del professionista non è gestita tramite authority Spring Security separate.

Gli utenti professionisti utilizzano il ruolo:

- `PROFESSIONAL`

La distinzione tra:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

viene applicata nel service layer come regola business.

## 17.2 Controlli attualmente implementati

### Availability

Il modulo Availability è riservato ai professionisti con specializzazione:

- `PERSONAL_TRAINER`

Un professionista `NUTRITIONIST` non può:

- creare slot availability;
- utilizzare il flusso operativo availability riservato al personal trainer.

### Bookings

Il modulo Bookings opera esclusivamente su slot availability appartenenti a professionisti `PERSONAL_TRAINER`.

Il sistema impedisce:

- la creazione di booking su slot appartenenti a un `NUTRITIONIST`;
- la conferma di booking anomali o storici collegati a slot appartenenti a un `NUTRITIONIST`.

## 17.3 Motivazione architetturale

`SecurityConfig` protegge l’area in base al ruolo generale dell’utente.

Il service layer applica invece la regola più specifica:

PROFESSIONAL + PERSONAL_TRAINER -> availability e booking consentiti
PROFESSIONAL + NUTRITIONIST -> availability e booking tramite slot non consentiti

---

## 18. Sicurezza sulle relazioni di dominio

## 18.1 Regola fondamentale

Avere il ruolo corretto non basta.

Bisogna anche verificare che la risorsa appartenga davvero all’utente o sia a lui accessibile.

Questi controlli vengono gestiti nel service layer.

---

## 18.2 Esempi già implementati

### Cliente

Un cliente può:

- vedere solo i professionisti a lui collegati
- vedere gli slot availability solo dei professionisti collegati
- creare booking solo su slot di professionisti collegati
- vedere solo i propri booking
- cancellare solo booking in cui è coinvolto

### Professionista

Un professionista può:

- vedere solo i clienti a lui collegati;
- se `PERSONAL_TRAINER`, creare, modificare, bloccare e sbloccare solo i propri slot availability;
- se `PERSONAL_TRAINER`, ricevere e gestire booking relativi ai propri slot validi;
- confermare o rifiutare solo booking che riguardano i propri slot autorizzati;
- cancellare booking confermati in cui è coinvolto;
- se `NUTRITIONIST`, non creare né confermare flussi booking basati su availability slot.

---

## 18.3 Dove viene controllata

Il controllo sulla relazione cliente-professionista viene fatto tramite verifica dell’esistenza di un `ProfessionalClientLink` attivo tra le due parti quando si crea un nuovo booking o si consulta Availability. Il link non è un filtro dello storico Booking: dopo la sua disattivazione, i partecipanti originari che restano autenticati e attivi possono leggere il dettaglio e le proprie liste e conservano le transizioni già consentite dal dominio.

Il controllo ownership sulle risorse viene fatto nei service specifici:

- `ClientService`
- `ProfessionalService`
- `AvailabilityService`
- `BookingService`

Per una risorsa identificata da ID, la policy è `404` indistinguibile quando la risorsa è inesistente, non collegata, non appartenente al principal o non visibile. In particolare vale per availability di un Professional non collegato, slot non accessibili in creazione Booking, dettaglio/cancellazione Booking di un estraneo e mutate Availability di un altro Professional. Le query mutate applicano prima lo scope di ownership o partecipazione e solo poi acquisiscono il lock pessimista.

`403` resta riservato a ruolo errato, account non attivo, email non verificata, profilo non operativo, specializzazione non ammessa o conflitto/stato business dopo il recupero di una risorsa accessibile.

Questa regola sul collegamento vale per le operazioni che richiedono una relazione attiva, non per la consultazione dello storico di una prenotazione già creata.

---

## 19. Password security

## 19.1 Stato attuale della validazione
Nelle registrazioni professionista e cliente la password viene validata con:
- obbligatorietà
- lunghezza minima `8`
- lunghezza massima `72` byte in codifica UTF-8
- almeno una lettera maiuscola
- almeno un numero
- almeno un carattere speciale

Il limite è espresso in byte, non in caratteri: alcuni caratteri Unicode occupano più byte in UTF-8. La password oltre il limite viene rifiutata come dato non valido prima dell’hashing. Il valore non viene troncato, normalizzato o trasformato.

## 19.2 Login
Nel login lo stesso limite è controllato prima della verifica BCrypt. Una password oltre 72 byte produce la stessa risposta generica `401 AUTHENTICATION_ERROR` delle altre credenziali non valide, indipendentemente dall’esistenza dell’account; il dettaglio del limite non viene esposto in questo flusso.

## 19.3 Storage
La password non viene mai salvata in chiaro.

Viene salvata:
- hashata
- tramite `BCryptPasswordEncoder`

Algoritmo, costo e formato degli hash esistenti restano invariati.

---

## 20. Token applicativi aggiuntivi

## 20.1 Token realmente presenti
Nel codice attuale esiste un token applicativo dedicato per:
- verifica email

Non esistono JWT di autenticazione runtime.

## 20.2 Regole del token di verifica email
Il token di verifica email è:
- casuale
- con scadenza
- consumabile una sola volta per attivare l’account
- idempotente dopo il consumo quando token, account e profilo sono già nello stato finale coerente
- marcato come usato dopo il primo utilizzo valido
- sostituito dal reinvio dopo un cooldown di 60 secondi, marcando come usati i precedenti token ancora inutilizzati
- mai restituito o registrato dal flusso di reinvio

## 20.3 Token non presenti
Nel codice attuale **non** risulta ancora implementato un token applicativo per:
- reset password
- refresh autenticazione

---

## 21. CORS e topologia same-origin

## 21.1 Stato attuale

- CORS applicativo è **disabilitato** nella `SecurityFilterChain`;
- in produzione il browser raggiunge frontend e API same-origin tramite reverse proxy;
- non è richiesta una lista `app.cors.allowed-origins` per l’autenticazione browser di produzione;
- le credenziali di autenticazione sono il cookie di sessione HttpOnly, non un Bearer header.

## 21.2 Nota importante

Ambienti di sviluppo con origini separate (es. Vite su porta diversa) non sono il modello di produzione documentato. Il contratto corrente assume same-origin in produzione; eventuali workaround locali di proxy non cambiano il modello di sicurezza runtime.

---

## 22. CSRF (riepilogo operativo)

## 22.1 Stato attuale
Nel `SecurityConfig` il CSRF è **abilitato** con `HttpSessionCsrfTokenRepository`.

## 22.2 Coerenza architetturale
Questa scelta è coerente con:
- API REST con sessione server-side
- cookie di sessione HttpOnly
- mutazioni autenticate e pubbliche che richiedono l’header CSRF

---

## 23. Security responsibilities

## 23.1 Spring Security gestisce
- autenticazione login e creazione sessione
- cookie di sessione e store JDBC
- CSRF
- logout con invalidazione sessione
- filtro readiness (`SessionAuthenticationStateFilter`)
- distinzione tra endpoint pubblici e protetti
- controllo base delle authority sugli endpoint configurati

## 23.2 Service layer gestisce

- verifica email e stato account;
- profilo attivo;
- accesso reale alle risorse collegate;
- controllo del tipo utente richiesto;
- controllo della specializzazione professionale;
- controlli business sui link professionista-cliente;
- blocco operativo per professionista non attivo o non verificato;
- validazione slot availability;
- blocco creazione e aggiornamento slot nel passato;
- assenza di sovrapposizioni availability;
- controllo ownership sugli slot;
- esclusione slot scaduti dalla lettura cliente;
- creazione booking solo su slot disponibili e futuri;
- creazione booking solo tra cliente e professionista collegati;
- blocco booking su slot appartenenti a nutrizionisti;
- blocco conferma booking con slot scaduti;
- blocco conferma booking su slot appartenenti a nutrizionisti;
- transizioni di stato booking consentite;
- aggiornamento stato slot dopo conferma o cancellazione booking.

---

## 24. Errori di sicurezza attesi

Nel codice attuale le situazioni seguenti devono produrre errori chiari:

- credenziali non valide
- utente non autenticato / sessione assente o invalidata
- CSRF mancante o non valido (`CSRF_VALIDATION_FAILED`)
- sessione scaduta per inattività o timeout assoluto
- account non più ACTIVE o email non verificata su richiesta autenticata (fail-closed con invalidazione sessione)
- route o risorsa inesistente dopo autenticazione
- metodo HTTP non supportato
- media type non supportato
- parametro HTTP obbligatorio mancante
- account non attivo in fase di login o operativa
- email non verificata
- profilo professionista non attivo (operazioni business, non login)
- profilo cliente non attivo (operazioni business, non login)
- accesso a endpoint con authority errata
- accesso ai dettagli cliente/professionista fuori dal perimetro del principal, esposto come 404 uniforme
- uso di codice invito non valido, non attivo, già usato o scaduto
- creazione slot availability nel passato
- creazione slot availability sovrapposto
- modifica di slot non disponibile
- creazione availability da parte di un professionista `NUTRITIONIST`
- esposizione al cliente di slot availability ormai scaduti
- booking su slot non disponibile
- booking su slot ormai scaduto
- booking su slot appartenente a un professionista `NUTRITIONIST`
- conferma booking pending con slot ormai scaduto
- conferma booking su slot appartenente a un professionista `NUTRITIONIST`
- booking tra cliente e professionista non collegati
- accesso a booking da utente non coinvolto
- transizione booking non consentita

Le risposte `401` usano il contratto `ErrorResponse` con codice tipico `UNAUTHORIZED` e **non** espongono `WWW-Authenticate: Bearer`.

---

## 25. Decisioni confermate

Per Support Trainer, nello stato attuale del progetto, si confermano le seguenti scelte:

- Spring Security 7 + Spring Session JDBC
- login `204` con cookie HttpOnly, senza JWT/Bearer/refresh
- CSRF abilitato; `GET /api/v1/auth/csrf` espone token e `headerName`
- logout `POST /api/v1/auth/logout` con CSRF → `204` e invalidazione della sola sessione corrente (non revoke-all)
- sessioni multiple indipendenti consentite; nessun limite di concorrenza né gestione dispositivi nell’MVP
- timeout 30 min inattività + 8 h assolute da `authenticatedAt`
- eligibilità login: `ACTIVE` + `emailVerified`; `profile.active=false` non blocca il login
- readiness dinamica su ogni richiesta autenticata
- topologia produzione same-origin; CORS non richiesto per auth browser
- ruoli reali: `PROFESSIONAL`, `CLIENT`
- specializzazione business: `PERSONAL_TRAINER`, `NUTRITIONIST`
- verifica email obbligatoria per professionista e cliente
- cliente registrabile solo tramite codice invito valido
- onboarding CLIENT frontend memory-only, senza secret in URL/storage e senza auto-login
- registrazione CLIENT confermata soltanto da `202`; outcome incerti trattati in modo conservativo e senza retry applicativo
- business authorization gestita nel service layer
- password hashata con BCrypt
- forgot password / reset password non ancora implementati
- Availability è modulo backend implementato e protetto
- Bookings è modulo backend implementato e protetto
- gli endpoint booking principali hanno regole esplicite in `SecurityConfig`
- ownership e transizioni booking restano controllate nel service layer
- il cliente può vedere availability solo di professionisti collegati
- il professionista può gestire solo i propri slot availability
- Availability è riservata ai professionisti `PERSONAL_TRAINER`
- Bookings tramite slot availability è riservato ai professionisti `PERSONAL_TRAINER`
- un `NUTRITIONIST` non può creare slot availability
- booking e conferme su slot di nutrizionisti vengono bloccati dal service layer
- slot scaduti non vengono esposti al cliente e non possono essere prenotati o confermati

## 26. Riferimento temporale dei flussi di sicurezza

Verifica email, inviti, `authenticatedAt` e timestamp delle risposte di errore usano l'unica fonte temporale applicativa. Il `Clock` tecnico opera in UTC. Le scadenze di verifica email e invito sono `Instant` persistiti in UTC e durano rispettivamente 24 e 168 ore reali. Il timestamp di `ErrorResponse` usa `ApplicationTimeProvider.nowInstant()` ed è serializzato in UTC con `Z`.

I test di sicurezza possono sostituire il bean con `Clock.fixed`, rendendo deterministici consumo dei token, timeout assoluto di sessione e timestamp 401/403. Le scadenze esposte per gli inviti sono serializzate in ISO-8601 UTC con `Z`; endpoint e messaggi restano invariati.
