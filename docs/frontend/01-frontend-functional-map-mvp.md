# Frontend Functional Map MVP - Support Trainer

## 1. Obiettivo del documento

Questo documento traduce il backend MVP reale di Support Trainer in una mappa funzionale per UX/UI, prototipazione Figma e implementazione web con React. Non definisce nuove API e non amplia il perimetro del prodotto.

Le fonti per lo **stato corrente** sono il codice, i test, le configurazioni e la documentazione attiva coerente con la baseline (in particolare README, Endpoint Map, API Modules Overview, Security Flow). Sprint conclusi e [`final-audit-mvp.md`](../final-audit-mvp.md) restano riferimenti storici delle rispettive baseline, non source of truth prevalente sullo stato attuale.

Documentazione attiva di supporto:

- [`README.md`](../../README.md);
- [`08-endpoint-map.md`](../08-endpoint-map.md);
- [`07-api-modules-overview.md`](../07-api-modules-overview.md);
- [`09-security-flow.md`](../09-security-flow.md);
- [`06-client-onboarding-implementation.md`](./06-client-onboarding-implementation.md);
- controller, DTO, configurazione Spring Security, servizi ed enum presenti nel backend.

La mappa distingue sempre tre stati:

| Stato                   | Significato per il frontend                                                  |
| ----------------------- | ---------------------------------------------------------------------------- |
| **Implementabile ora**  | Esiste un contratto backend utilizzabile nell'MVP.                           |
| **Futuro / non attivo** | La UX può prevederne la posizione, ma non deve presentarlo come funzionante. |
| **Non presente**        | Non deve comparire come azione attiva né generare chiamate API.              |

La presenza di una pagina frontend non implica necessariamente un endpoint dedicato: una landing statica non ne richiede uno, mentre una dashboard MVP deve comporre dati restituiti da endpoint già esistenti. Nel backend risultano implementati **31 endpoint applicativi**: Auth 8, Me 4, Client 2, Professional 3, Invite 2, Availability 5 e Booking 7. `/error` è un fallback tecnico separato e non va trattato come endpoint funzionale.

La baseline certificata richiede inoltre che il client usi la risposta neutra `202` per entrambe le registrazioni, non cerchi `EMAIL_ALREADY_REGISTERED`, gestisca `ErrorResponse` tramite `code` e tratti gli orari Availability e gli snapshot Booking come `OffsetDateTime` con offset autorevole. Le sezioni successive dettagliano questi contratti.

## 2. Stato del frontend

- La fondazione frontend è implementata con **React + TypeScript + Vite**.
- React Router registra le route pubbliche, le aree cliente e professionista, le pagine di errore e una preview tecnica disponibile soltanto in sviluppo (`/dev/role-preview`).
- Sono presenti layout pubblico, autenticato e di errore, navigazione differenziata per ruolo, componenti condivisi e test automatici.
- La home pubblica sulla route `/` è **implementata**; dettagli in [Public Home Implementation](./02-public-home-implementation.md).
- L’**auth session-based frontend è implementata**: httpClient, CSRF memory-only, AuthProvider, bootstrap/reconciliation `/me`, login, logout, guards e routing protetto. Dettagli in [Authentication Session Flow](./03-authentication-session-flow.md).
- La **pagina Profilo autenticata** è **implementata** (CLIENT e PROFESSIONAL), con Account in sola lettura e Operational Status modificabile. Soft commit e race protection: [FE03](./03-authentication-session-flow.md).
- La **registrazione pubblica PROFESSIONAL** e la **verifica email** (confirm + resend pertinente) sono **implementate**. Dettaglio tecnico: [Professional Onboarding Implementation](./04-professional-onboarding-implementation.md).
- La **gestione inviti PROFESSIONAL** (`/app/professional/invites`: lista, genera, copia codice valido) è **implementata**. Dettaglio tecnico: [Professional Invites Implementation](./05-professional-invites-implementation.md).
- La **validazione invito e registrazione pubblica CLIENT** sono **implementate** con provider memory-only, auth gate locale, outcome conservativi e cleanup del draft. Dettaglio tecnico: [Client Onboarding Implementation](./06-client-onboarding-implementation.md).
- Le altre pagine business private (dashboard dati, clients, professionals, availability, bookings) restano **placeholder**: le route esistono, ma non sono flussi applicativi completi.
- Il client usa path relativi `/api/v1/...` con `credentials: 'same-origin'`. In sviluppo Vite proxya `/api` → `http://localhost:8080`. In produzione la topologia è same-origin dietro reverse proxy.
- Un'app mobile con React Native + Expo è una possibile evoluzione futura, fuori dall'MVP.
- L'implementazione corrente del frontend non modifica il contratto backend descritto in questa mappa.

## 3. Ruoli utente

Nel backend i soli ruoli di sicurezza sono `CLIENT` e `PROFESSIONAL`. `PERSONAL_TRAINER` e `NUTRITIONIST` sono specializzazioni business del ruolo `PROFESSIONAL`, non authority Spring Security distinte. Il visitatore pubblico non è un ruolo persistito.

| Profilo UX          | Identità backend                    | Può fare ora                                                                                                                                          | Non può fare ora                                                                               | Pagine private necessarie                                                               |
| ------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Visitatore pubblico | Nessuna                             | Consultare contenuti statici, registrare un professionista, validare un invito, registrare un cliente, verificare l'email tramite token, fare login   | Accedere a dati applicativi o registrarsi come cliente senza invito                            | Nessuna                                                                                 |
| Cliente             | `CLIENT`                            | Gestire profilo/account, vedere professionisti collegati, consultare disponibilità di personal trainer collegati, creare e gestire booking consentiti | Creare inviti/slot, vedere clienti, usare moduli Workout, Nutrition, Feedback o Measurements   | Dashboard, profilo/account, professionisti, disponibilità, booking                      |
| Professionista      | `PROFESSIONAL`                      | Gestire profilo/account, vedere clienti collegati, generare e consultare inviti                                                                       | Usare funzionalità non compatibili con la propria specializzazione; gestire manualmente i link | Dashboard, profilo/account, clienti, inviti; aree specialistiche solo quando supportate |
| Personal trainer    | `PROFESSIONAL` + `PERSONAL_TRAINER` | Tutte le funzioni comuni del professionista, più gestione availability e richieste booking                                                            | Workout, Feedback e Measurements, ancora non implementati                                      | Anche availability e booking                                                            |
| Nutrizionista       | `PROFESSIONAL` + `NUTRITIONIST`     | Funzioni comuni del professionista: profilo/account, clienti e inviti                                                                                 | Availability e booking basati su slot; piani Nutrition e Feedback, ancora non implementati     | Nessuna area operativa Nutrition nell'MVP                                               |

Conseguenze UX:

- il menu va costruito usando prima `role`, poi `specialization` ottenuta da `GET /api/v1/me/profile`;
- un nutrizionista non deve vedere Availability o Booking come aree attive;
- un cliente può essere collegato a più professionisti: le API restituiscono una lista e il backend ammette fino a tre collegamenti attivi;
- la UI deve mostrare soltanto i campi profilo pertinenti al ruolo.

## 4. Funzionalità pubbliche implementabili ora

Gli endpoint sotto `/api/v1/auth/**` sono pubblici.

| Schermata                    | Scopo ed endpoint                                                                                                                                                                                                                                                                 | Stati UI necessari                                                                                                                         | Errori principali                                                                                                                                                             |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Home / landing               | Pagina statica di ingresso. Nessun endpoint richiesto. CTA verso login e registrazione professionista; l'accesso cliente parte da un invito.                                                                                                                                      | Contenuto pronto, eventuale fallback contenuti                                                                                             | Nessun errore API                                                                                                                                                             |
| Login                        | Ottenere CSRF (`GET /api/v1/auth/csrf`), poi autenticare con `POST /api/v1/auth/login` + header CSRF. Campi: email e password. Risposta `204` senza token; il browser conserva il cookie HttpOnly. Dopo successo: nuovo `GET /csrf`, poi bootstrap `/me/account` e `/me/profile`. | Idle, invio, successo e redirect per ruolo, errore credenziali, account non attivo / email non verificata                                  | `400` validazione/body, `401 AUTHENTICATION_ERROR`, `403 ACCOUNT_NOT_ACTIVE` / `EMAIL_NOT_VERIFIED`, `403 CSRF_VALIDATION_FAILED`. `profile.active=false` non blocca il login |
| Registrazione professionista | Inviare `POST /api/v1/auth/register/professional`. Campi: nome, cognome, email, password, specializzazione `PERSONAL_TRAINER` o `NUTRITIONIST`.                                                                                                                                   | Form, validazione, invio, `202` neutro, istruzione di verifica email                                                                       | `400 VALIDATION_ERROR`; non cercare `EMAIL_ALREADY_REGISTERED`                                                                                                                |
| Verifica email               | Ricevere `/verify-email#token=...`, rimuovere subito il fragment e inviare `POST /api/v1/auth/email-verification/confirm` con body `token`.                                                                                                                                       | Verifica in corso, verificata, non valido o scaduto; secondo utilizzo idempotente; CTA al login dopo successo                              | `400` body/validazione, `404 EMAIL_VERIFICATION_TOKEN_NOT_FOUND`, `410 EMAIL_VERIFICATION_TOKEN_EXPIRED`                                                                      |
| Reinvio verifica             | Dalla schermata “Controlla la tua email”, inviare `POST /api/v1/auth/email-verification/resend` con body `email`.                                                                                                                                                                 | Messaggio sempre neutro; azione “Invia di nuovo”; pulsante UX disabilitato 60 secondi, senza assumere che il backend abbia creato un token | `400` validazione/body, `415` media type; ogni email valida riceve `202` identico                                                                                             |
| Validazione invito           | Verificare il codice prima di mostrare il form cliente con `POST /api/v1/auth/register/client/validate-invite`. Body: `code`.                                                                                                                                                     | Form codice, validazione, valido con scadenza, non valido/scaduto/usato, retry                                                             | `400 VALIDATION_ERROR` e codici `INVITE_CODE_*`; `404 INVITE_CODE_NOT_FOUND`                                                                                                  |
| Registrazione cliente        | Inviare `POST /api/v1/auth/register/client` dopo validazione invito. Campi: nome, cognome, email, password, codice, data di nascita, altezza, obiettivo, genere; note mediche/infortuni/generali facoltative.                                                                     | Form multi-sezione, validazione campo, invio, `202` neutro, schermata “Controlla la tua email”                                             | `400` validazione o invito non più valido, `403` professionista non utilizzabile; nessun `EMAIL_ALREADY_REGISTERED`                                                           |
| Pagine informative statiche  | Eventuali pagine legali o informative non richiedono backend, ma vanno create solo quando contenuti e requisiti sono definiti.                                                                                                                                                    | Contenuto e pagina non trovata frontend                                                                                                    | Nessun errore API                                                                                                                                                             |

Note di flusso verificate:

- tutte le mutazioni Auth (login, registrazioni, confirm/resend, validate-invite, logout) richiedono CSRF: prima `GET /api/v1/auth/csrf`, poi header `headerName` (tipicamente `X-CSRF-TOKEN`); il token va solo in memoria;
- entrambe le registrazioni usano una risposta pubblica `202 Accepted` intenzionalmente neutra: il frontend non può dedurre se l'email fosse già registrata né quali effetti persistenti siano stati eseguiti e non deve cercare `EMAIL_ALREADY_REGISTERED`;
- nel ramo di una nuova registrazione, cliente e professionista nascono `PENDING_VERIFICATION`, con `emailVerified=false`, e non possono fare login prima della conferma;
- nel ramo di una nuova registrazione CLIENT il backend crea account e collegamento, consuma l'invito e genera il token di verifica; per un'email già esistente può terminare anticipatamente mantenendo la risposta neutra, quindi nessuno di questi effetti è inferibile dal solo `202`;
- per una nuova registrazione idonea il token resta valido 24 ore e, dopo commit, il backend affida alla porta email una richiesta con link nel fragment; anche questa predisposizione non è deducibile dal `202`, `SMTP` è disponibile ma il default locale resta disabilitato;
- la pagina `/verify-email` (implementata) legge il fragment, lo rimuove immediatamente dall'URL prima di avviare analytics, monitoring o altre integrazioni, conserva il token solo in memoria e non lo inserisce in `localStorage`; invia quindi il POST, mostra la CTA login in caso di successo e il messaggio neutro di reinvio in caso di 410. Dettaglio tecnico: [FE04](./04-professional-onboarding-implementation.md);
- la pagina di verifica resta compatibile con `BrowserRouter` e non usa `HashRouter`, perché il fragment è riservato al token, né trasmette il token a strumenti di analytics o monitoring;
- l'azione “Invia di nuovo” richiede l'email dell'utente (inserita nella pagina di verifica), non mostra se l'account esiste e non invia email o token ad analytics; il frontend può disabilitare il pulsante per 60 secondi, ma il backend resta autoritativo e non espone il tempo residuo;
- il codice invito è monouso, non è legato a una specifica email destinataria e scade dopo 7 giorni.
- Validate e Register CLIENT condividono un provider limitato al proprio subtree che conserva soltanto il codice canonico. Il codice non passa in URL, router state o storage; reload e uscita dal subtree lo eliminano.
- l'accesso diretto a `/register/client` senza invito memory-only è fail-closed e usa redirect `replace` verso `/invite/validate`;
- Register CLIENT considera confermato soltanto `202`, tratta gli outcome incerti come ambigui e non ripete automaticamente la registrazione. Dettaglio: [FE06](./06-client-onboarding-implementation.md).

## 5. Funzionalità private implementabili ora

### 5.1 Area comune autenticata

| Area               | Endpoint                                           | Stato MVP                          | Note UX                                                                                                                                          |
| ------------------ | -------------------------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Bootstrap sessione | `GET /api/v1/me/account`, `GET /api/v1/me/profile` | **Implementato** nel client auth   | Confermare ruolo, stato account, specializzazione e dati profilo dopo login/reload. Dettagli in [FE03](./03-authentication-session-flow.md).     |
| Profilo            | lettura `/me/profile`; `PATCH /api/v1/me/profile`  | **UI implementata**                | Route reali sotto; form role-aware collegato al contratto.                                                                                       |
| Account            | lettura `/me/account`                              | **UI implementata (sola lettura)** | Nessun editing account in questa pagina.                                                                                                         |
| Stato operativo    | `PATCH /api/v1/me/profile/operational-status`      | **UI implementata**                | Sezione indipendente dal form Profilo. Cliente: `ATTIVO`, `INFORTUNATO`, `PAUSA`. Professionista: `DISPONIBILE`, `ASSENTE`, `FERIE`, `MALATTIA`. |
| Immagine profilo   | Campo `profileImageUrl` in lettura                 | Visualizzazione + fallback         | Nessun upload o update immagine: avatar con iniziali se assente.                                                                                 |

Nel `PATCH` profilo professionista, `instagramUrl` e `websiteUrl` seguono un contratto a tre stati: campo omesso o `null` = invariato; URL `http://`/`https://` = aggiornato; stringa vuota = rimosso. Il form conserva esplicitamente questa distinzione.

#### 5.1.1 Route Profile

| Ruolo        | Route                       | Componente                              |
| ------------ | --------------------------- | --------------------------------------- |
| CLIENT       | `/app/client/profile`       | `ProfilePage` (`area="cliente"`)        |
| PROFESSIONAL | `/app/professional/profile` | `ProfilePage` (`area="professionista"`) |

La stessa composizione è role-aware: campi e stati operativi dipendono dal ruolo autenticato. `PERSONAL_TRAINER` e `NUTRITIONIST` condividono la struttura Profile; la specialization è **sola lettura**.

#### 5.1.2 Profilo CLIENT — editing

Campi modificabili via PATCH differenziale:

- `firstName`, `lastName`, `birthDate`, `heightCm`, `primaryGoal`, `gender`, `medicalNotes`, `injuryNotes`, `notes`.

Nella sezione Profile (vista): oltre ai campi sopra sono mostrati anche `profileImageUrl` (o fallback accessibile) e `role`. Lo stato operativo è nella sezione dedicata, non nel form.

Non editabili e **non** renderizzati dalla UI Profile: `id`, `active`, campi PROFESSIONAL. I dati account non appartengono a questa sezione: vedi §5.1.4.

#### 5.1.3 Profilo PROFESSIONAL — editing

Campi modificabili:

- `firstName`, `lastName`, `phoneNumber`, `bio`, `workplaceName`, `city`, `instagramUrl`, `websiteUrl`.

Nella sezione Profile (vista): oltre ai campi sopra sono mostrati anche `profileImageUrl` (o fallback), `role` e `specialization` (**sola lettura**). Lo stato operativo è nella sezione dedicata, non nel form.

Non editabili e **non** renderizzati dalla UI Profile: `id`, `active`, campi CLIENT. I dati account non appartengono a questa sezione: vedi §5.1.4.

#### 5.1.4 Account — sola lettura

Campi mostrati dalla `AccountSection` (esattamente): `email`, `role`, `accountStatus`, `emailVerified`, `createdAt`.

Non implementati: editing email, password, cancellazione account, role/specialization editing, session/device management.

#### 5.1.5 Operational Status

Sezione indipendente dal form Profilo: stato corrente, select e CTA di aggiornamento.

- CLIENT: `ATTIVO`, `INFORTUNATO`, `PAUSA`.
- PROFESSIONAL: `DISPONIBILE`, `ASSENTE`, `FERIE`, `MALATTIA`.

Nella baseline corrente lo status **non** blocca automaticamente booking, availability, login o accesso applicativo: è informazione operativa modificabile.

#### 5.1.6 View / edit e salvataggio

- **View:** dati profilo in sola lettura; CTA «Modifica profilo».
- **Edit:** form role-specific; Salva / Annulla; validazione; stato saving; errori inline/globali; feedback «Profilo aggiornato» / «Stato aggiornato».
- Salvato solo il **delta** dei campi modificati; **nessun** update ottimistico; la UI applica la response server tramite soft commit nella source of truth auth ([FE03](./03-authentication-session-flow.md)). Dopo PATCH profilo/status **non** parte automaticamente un dual GET `/me/account` + `/me/profile`.
- `401` e CSRF usano la foundation auth/mutation; nessun handling locale alternativo.
- Validazione profilo (alto livello, coerente con il backend): required dove previsto; limiti di lunghezza; data di nascita nel passato; altezza valida; URL `http`/`https` per link professionista; campi cross-role non ammessi nel PATCH. Il dettaglio normativo resta in [Validation Rules](../06-validation-rules.md).

#### 5.1.7 Follow-up UI — M1-R

Dopo errori di campo sul form Profilo, un successivo aggiornamento indipendente dello Status riuscito può lasciare ancora visibili quei `fieldErrors`. Impatto UX, non bloccante; follow-up aperto (nessuna remediation decisa qui).

Il follow-up auth **E2E-1** (safe redirect cross-role → `/forbidden`) è documentato in [FE03](./03-authentication-session-flow.md).

### 5.2 Cliente

| Area/pagina                  | Endpoint                                                                       | Stato MVP                                              | Note UX                                                                                                                                                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dashboard cliente            | Composizione di `GET /api/v1/professionals/my` e `GET /api/v1/bookings/client` | Implementabile ora come composizione                   | Non esiste un endpoint dashboard. Mostrare riepiloghi derivati senza promettere statistiche avanzate.                                                                                                            |
| Profilo/account              | Endpoint `/api/v1/me/**`                                                       | **UI implementata**                                    | Vedi §5.1; form anagrafica e note cliente.                                                                                                                                                                       |
| Professionisti collegati     | `GET /api/v1/professionals/my`                                                 | **UI implementata**                                    | Route `/app/client/professionals`; lista responsive con loading, empty state, error e retry.                                                                                                                       |
| Dettaglio professionista     | `GET /api/v1/professionals/{professionalId}`                                   | **UI implementata**                                    | Route `/app/client/professionals/:professionalId`; solo collegamento attivo, 404 neutro, contatti e link esterni sicuri. Il flag tecnico `active` non è presentato.                                                |
| Disponibilità professionista | `GET /api/v1/professionals/{professionalId}/availability`                      | Implementabile ora solo per personal trainer collegato | Mostrare solo slot restituiti dal server. Per un nutrizionista l'area non va offerta. Empty state distinto da errore.                                                                                            |
| Crea booking                 | `POST /api/v1/bookings`                                                        | Implementabile ora                                     | Body: un solo `availabilitySlotId` e nota facoltativa fino a 1000 caratteri. Confermare data/ora e professionista prima dell'invio.                                                                              |
| Lista booking                | `GET /api/v1/bookings/client`                                                  | Implementabile ora                                     | Usa `BookingSummaryResponse`: controparte, stato, intervallo e durata sono già disponibili senza chiamate aggiuntive. Ordine iniziale: creazione decrescente e id decrescente; paginazione e filtri sono futuri. |
| Dettaglio booking            | `GET /api/v1/bookings/{bookingRequestId}`                                      | Implementabile ora                                     | Usa `BookingDetailResponse`. Solo utenti coinvolti, anche dopo chiusura del collegamento: mostra nome storico, intervallo snapshot, stato, nota e item senza dipendere da `slotStatus`.                          |
| Cancella booking             | `PATCH /api/v1/bookings/{bookingRequestId}/cancel`                             | Implementabile ora                                     | Il cliente può cancellare `PENDING` o `CONFIRMED`; usare conferma esplicita e aggiornare lo stato dalla risposta.                                                                                                |

### 5.3 Professionista: funzionalità comuni

| Area/pagina              | Endpoint                                                                           | Stato MVP                            | Note UX                                                                                                                                                                                                              |
| ------------------------ | ---------------------------------------------------------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dashboard professionista | Composizione di clienti, inviti e, solo per personal trainer, availability/booking | Implementabile ora come composizione | Nessun endpoint aggregato. I widget devono dipendere dalla specializzazione.                                                                                                                                         |
| Profilo/account          | Endpoint `/api/v1/me/**`                                                           | **UI implementata**                  | Vedi §5.1; contatti, bio, luogo, città e link.                                                                                                                                                                       |
| Clienti collegati        | `GET /api/v1/clients/my`                                                           | **UI implementata**                  | Route `/app/professional/clients`; disponibile sia a PT sia a nutrizionisti, con lista responsive, loading, empty state, error e retry.                                                                               |
| Dettaglio cliente        | `GET /api/v1/clients/{clientId}`                                                   | **UI implementata**                  | Route `/app/professional/clients/:clientId`; profilo condiviso approvato, collegamento attivo e 404 neutro. Le note sensibili e i dati account restano esclusi.                                                       |
| Crea invito              | `POST /api/v1/invites`                                                             | **UI implementata**                  | Nessun body. Dopo `201`, mostra codice/scadenza; Copia solo se Valido. Fail-closed su `expiresAt` invalido (`Non disponibile`). Dettaglio: [FE05](./05-professional-invites-implementation.md).                      |
| Lista inviti             | `GET /api/v1/invites`                                                              | **UI implementata**                  | Stati Non attivo / Usato / Scaduto / Non disponibile / Valido. Nessun dettaglio o disattivazione manuale.                                                                                                            |

### 5.4 Personal trainer

| Area/pagina            | Endpoint                                                 | Stato MVP                      | Note UX                                                                                                                      |
| ---------------------- | -------------------------------------------------------- | ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| Lista slot             | `GET /api/v1/availability/my`                            | Implementabile ora             | Stati `AVAILABLE`, `BLOCKED`, `BOOKED`; lista ordinata per inizio. Nessun calendario avanzato necessario.                    |
| Crea slot              | `POST /api/v1/availability`                              | Implementabile ora             | Inizio e fine futuri, fine successiva all'inizio, nessuna sovrapposizione.                                                   |
| Modifica slot          | `PATCH /api/v1/availability/{slotId}`                    | Implementabile ora con vincoli | Solo slot `AVAILABLE`, mai coinvolti in booking e senza booking `PENDING`. Il `PATCH` è parziale.                            |
| Blocca/sblocca slot    | `PATCH .../{slotId}/block`, `PATCH .../{slotId}/unblock` | Implementabile ora             | Azioni visibili solo negli stati coerenti. Uno slot con booking pending non è bloccabile.                                    |
| Lista booking ricevuti | `GET /api/v1/bookings/professional`                      | Implementabile ora             | Usa `BookingSummaryResponse`: mostra richieste in ordine recente, stato, cliente e intervallo; nessuna chiamata aggiuntiva.  |
| Dettaglio booking      | `GET /api/v1/bookings/{bookingRequestId}`                | Implementabile ora             | Usa `BookingDetailResponse`: mostra cliente, nota, stato, intervallo snapshot e timestamp della transizione quando presente. |
| Conferma/rifiuta       | `PATCH .../{id}/confirm`, `PATCH .../{id}/reject`        | Implementabile ora             | Solo da `PENDING`. Il rifiuto non accetta un motivo: non mostrare un campo motivo attivo.                                    |
| Cancella               | `PATCH .../{id}/cancel`                                  | Implementabile ora             | Il professionista può cancellare soltanto un booking `CONFIRMED`.                                                            |

### 5.5 Contratto Booking per il frontend

`BookingSummaryResponse` è il contratto delle liste; `BookingDetailResponse` è il contratto per creazione, dettaglio e mutazioni. Il frontend non deve richiedere Availability o profili per mostrare nome, data, ora e stato di una prenotazione.

- `displayName` dei partecipanti è storico e resta invariato se il profilo cambia;
- `profileImageUrl` è opzionale e corrente: se assente usare un fallback solo visivo, senza inventare URL;
- la specializzazione è corrente e disponibile solo sul partecipante professionista;
- non sono presenti `primaryGoal`, dati sanitari, email, telefono, note private o `slotStatus` live;
- le mutazioni restituiscono il dettaglio completo aggiornato, quindi la UI aggiorna lo stato dalla response;
- uno storico esistente resta consultabile dopo la chiusura del collegamento; la chiusura impedisce soltanto nuove prenotazioni.

### 5.6 Nutrizionista

Il nutrizionista usa le funzionalità comuni del professionista, ma non dispone di un modulo Nutrition attivo. Availability e Booking tramite slot sono bloccati nel service layer. Dashboard e navigazione devono quindi limitarsi a profilo/account, clienti e inviti. Una voce Nutrition può comparire solo in wireframe futuro o, se davvero utile alla comunicazione, disabilitata con badge “In arrivo”; nell'MVP operativo è preferibile nasconderla.

### 5.7 Contratto frontend del profilo cliente condiviso

- PT e nutrizionista usano temporaneamente lo stesso contratto minimo;
- la lista non deve mostrare o conservare `primaryGoal`, `operationalStatus` o altri campi assenti;
- il dettaglio aggiunge `primaryGoal`, `operationalStatus`, `birthDate`, `heightCm` e `gender` all'identità della lista;
- `medicalNotes`, `injuryNotes`, `notes`, dati account, stato tecnico e dati del collegamento non devono entrare nello state della pagina professionista, in cache persistenti o analytics;
- il profilo personale del cliente ottenuto da `/api/v1/me/**` è un contratto distinto e non va riutilizzato nelle schermate professionista;
- schermate anamnesi, infortuni o note professionali non fanno parte dell'MVP corrente; un'eventuale condivisione futura richiede una decisione dedicata.

## 6. Funzionalità future da prevedere ma non attivare

| Funzionalità            | Posizione UX futura possibile                          | Come trattarla ora                                                                              |
| ----------------------- | ------------------------------------------------------ | ----------------------------------------------------------------------------------------------- |
| Workout                 | Area personal trainer e area cliente                   | Nascosta nell'MVP operativo; placeholder solo nei wireframe, marcato “Futuro / non attivo”.     |
| Nutrition               | Area nutrizionista e area cliente                      | Nascosta; non usare l'assenza di booking per simulare piani alimentari.                         |
| Feedback                | Dettaglio futuro di workout/nutrition o area progressi | Nascosto: non esistono endpoint né dati.                                                        |
| Measurements            | Profilo/progressi cliente                              | Nascosta; niente grafici o inserimento misure.                                                  |
| Reset password          | Login / recupero account                               | Non mostrare un link attivo. Può apparire disabilitato solo in prototipi esplicitamente futuri. |
| Upload immagine profilo | Profilo                                                | Mostrare immagine esistente o avatar fallback; nessun controllo upload attivo.                  |
| App mobile              | Fuori dalla web app                                    | Nessun elemento nell'MVP web. React Native + Expo resta un'evoluzione separata.                 |

Sono inoltre non presenti e da non esporre come attivi: cambio password autenticato, gestione manuale dei collegamenti, disattivazione inviti, motivo di rifiuto booking, notifiche, pagamenti, chat, amministrazione e statistiche avanzate.

## 7. Sitemap MVP

I path seguenti sono registrati nel router frontend. **Route esistente ≠ funzionalità completa.** Home, login/auth foundation, onboarding pubblico **PROFESSIONAL e CLIENT**, **verifica email**, **Profilo/Account/Operational Status** e **gestione inviti PROFESSIONAL** sono implementati; le altre pagine business restano placeholder. È usato il plurale `professionals` perché il backend restituisce una lista di professionisti collegati.

### Pubblico

```text
/
/login
/register/professional
/invite/validate
/register/client
/verify-email#token=...
```

Flusso cliente implementato: `/invite/validate` valida il codice e, in caso positivo, passa a `/register/client` conservando soltanto il codice canonico nel provider memory-only condiviso. Il codice non entra nella URL o nello stato del router. Il backend ripete la validazione durante la registrazione.

Dopo la registrazione PROFESSIONAL o CLIENT la UI mostra “Controlla la tua email”. Il link apre `/verify-email`: la pagina legge e rimuove il token dall'URL (anche se il fragment non è valido), effettua il POST di conferma e presenta la CTA login. Un secondo utilizzo coerente resta un successo. Le schermate terminali offrono “Invia di nuovo” con messaggio neutro e blocco UX di 60 secondi, senza sostituire il cooldown del backend. L'adapter SMTP backend invia un messaggio testuale con URL `#token=...`. Dettagli: onboarding PROFESSIONAL e Verify in [FE04](./04-professional-onboarding-implementation.md), onboarding CLIENT in [FE06](./06-client-onboarding-implementation.md).

### Area cliente

```text
/app/client/dashboard
/app/client/profile
/app/client/professionals
/app/client/professionals/:professionalId
/app/client/professionals/:professionalId/availability
/app/client/bookings
/app/client/bookings/:bookingRequestId
```

### Area professionista

```text
/app/professional/dashboard
/app/professional/profile
/app/professional/clients
/app/professional/clients/:clientId
/app/professional/invites
/app/professional/availability              # solo PERSONAL_TRAINER
/app/professional/bookings                  # solo PERSONAL_TRAINER
/app/professional/bookings/:bookingRequestId # solo PERSONAL_TRAINER
```

Non servono route separate per personal trainer e nutrizionista: condividono il ruolo `PROFESSIONAL`; menu e guard specialistiche usano `specialization`.

### 7.1 Maturity delle route frontend

| Route / area                                            | Audience                  | Maturity                                                     |
| ------------------------------------------------------- | ------------------------- | ------------------------------------------------------------ |
| `/` home                                                | Pubblico                  | **Implementata**                                             |
| `/login`                                                | Pubblico                  | **Implementata**                                             |
| `/register/professional`                                | Pubblico                  | **Implementata**                                             |
| `/verify-email`                                         | Pubblico                  | **Implementata**                                             |
| `/invite/validate`, `/register/client`                  | Pubblico                  | **Implementate** (provider memory-only e auth gate locale)   |
| Guardie auth (`RequireAuth` / ruolo / specializzazione) | Privato                   | **Implementate**                                             |
| `/app/client/profile`, `/app/professional/profile`      | CLIENT / PROFESSIONAL     | **Implementata** (Profilo + Account RO + Operational Status) |
| `/app/professional/invites`                             | PROFESSIONAL              | **Implementata** (lista, genera, copia se Valido)            |
| `/app/*/dashboard`                                      | CLIENT / PROFESSIONAL     | **Placeholder** (shell; senza dati business aggregati)       |
| `/app/professional/clients` e dettaglio                 | PROFESSIONAL (PT + NUT)   | **Implementate**                                             |
| `/app/client/professionals` e dettaglio                 | CLIENT                    | **Implementate**                                             |
| Availability / bookings                                 | CLIENT; PROFESSIONAL + PT | **Placeholder**                                              |
| `/dev/role-preview`                                     | Dev-only                  | **Implementata** (solo `import.meta.env.DEV`)                |

## 8. Protezione rotte frontend

### 8.1 Classificazione

- Rotte pubbliche: landing, login, registrazioni, validazione invito e verifica email.
- Rotte private comuni: profilo/account e bootstrap sessione.
- Rotte `CLIENT`: professionisti collegati, availability consultabile e booking cliente.
- Rotte `PROFESSIONAL`: clienti e inviti.
- Rotte `PROFESSIONAL + PERSONAL_TRAINER`: availability e booking professionista.

Le guard frontend migliorano navigazione e chiarezza, ma non sostituiscono l'autorizzazione backend.

Nella fondazione corrente le guard `RequireAuth`, `RequireRole` e `RequireSpecialization` sono **implementate** e collegate allo stato auth. Profilo, inviti PROFESSIONAL e le quattro pagine di lista/dettaglio CLIENT ↔ PROFESSIONAL sono pagine business reali; dashboard, Availability e Booking restano placeholder di contenuto.

Il subtree CLIENT pubblico usa inoltre `ClientOnboardingAuthGate`: non monta le pagine durante `initializing`, resta fail-closed su `unavailable`, consente l'outlet soltanto a `unauthenticated` e redirige un utente `authenticated` alla dashboard coerente col ruolo dopo aver pulito l'invito.

### 8.2 Sessione, CSRF e bootstrap (implementati)

Reference operativa: [Authentication Session Flow](./03-authentication-session-flow.md). Sintesi:

1. CSRF da `GET /api/v1/auth/csrf`, conservato **solo in memoria**.
2. Mutazioni con header CSRF; `credentials: 'same-origin'`.
3. Login `POST /api/v1/auth/login` → `204`; nessun JWT/Bearer/refresh.
4. Dopo login: refresh CSRF + bootstrap `/me/account` e `/me/profile`.
5. Nessun salvataggio auth in `localStorage`/`sessionStorage`.
6. Logout `POST /api/v1/auth/logout` con CSRF → `204`.
7. Timeout backend documentati in [`docs/09-security-flow.md`](../09-security-flow.md); su `401` session-bound il client invalida lo stato auth.

### 8.3 Topologia e CORS

- in produzione: same-origin dietro reverse proxy; CORS non è il meccanismo di auth browser;
- le chiamate JSON usano `Content-Type` e cookie di sessione; non `Authorization: Bearer`;
- in sviluppo locale Vite proxya `/api` → `http://localhost:8080`, senza cambiare il contratto di produzione;
- il frontend non deve codificare origini di produzione nel sorgente.

### 8.4 Risposte 401, 403 e 404

- `401` durante il login: mostrare l'errore nel form, senza redirect ciclici.
- `401` su una rotta privata (`UNAUTHORIZED` o sessione invalidata): scartare CSRF/stato locale, conservare se utile la destinazione, e reindirizzare a `/login` con messaggio “Sessione scaduta” o “Accesso richiesto”. Non tentare refresh token (non esistono).
- `403 CSRF_VALIDATION_FAILED`: tipicamente ri-fetch di `/csrf` e retry controllato, oppure messaggio di integrazione.
- `403 ACCESS_DENIED` da SecurityConfig: lasciare intatta la sessione, mostrare pagina “Non autorizzato” e offrire ritorno alla dashboard corretta.
- nei dettagli cliente e professionista, `404 CLIENT_NOT_FOUND` e `404 PROFESSIONAL_NOT_FOUND` coprono in modo indistinguibile ID inesistente, relazione assente o inattiva e profilo non leggibile; il frontend non deve tentare di dedurre quale caso si sia verificato;
- gli altri `403` business, per esempio specializzazione non consentita in flussi diversi, mantengono il comportamento contestuale esistente.

L'azione “Esci” deve chiamare il logout backend con CSRF e poi ripulire lo stato client.

## 9. Gestione errori frontend

Il contratto comune reale è `ErrorResponse`:

```json
{
  "timestamp": "2026-07-15T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "La richiesta contiene dati non validi",
  "path": "/api/v1/auth/login",
  "fieldErrors": [
    {
      "field": "email",
      "code": "Email",
      "message": "Formato email non valido"
    }
  ]
}
```

| Campo         | Uso frontend                                                                                                                              |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `timestamp`   | Istante UTC con `Z`, utile alla diagnostica ma non necessario nel messaggio principale.                                                   |
| `status`      | Comportamento HTTP generale.                                                                                                              |
| `code`        | Identificatore macchina per comportamento e copy specifici.                                                                               |
| `message`     | Solo fallback leggibile: non usarlo per decidere la logica.                                                                               |
| `path`        | URI della richiesta senza query, utile per diagnostica locale.                                                                            |
| `fieldErrors` | Presente solo per `VALIDATION_ERROR`: lista `{field?, code, message}`; può contenere più errori per campo e errori globali senza `field`. |

| Status                      | Comportamento UX                                                                                                                                                                                                       |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | Mostrare errori campo per `VALIDATION_ERROR`; per body, path o query malformati mostrare messaggio generale. Comprende anche alcune violazioni di stato/invito.                                                        |
| `401 Unauthorized`          | Invalidare lo stato auth client (CSRF in memoria). Codice tipico `UNAUTHORIZED`. Il backend **non** include `WWW-Authenticate: Bearer`.                                                                                |
| `403 Forbidden`             | Ruolo, account, email o profilo non idonei, CSRF non valido (`CSRF_VALIDATION_FAILED`), oppure altra regola business non legata a una risorsa enumerabile. Non fare logout automatico salvo CSRF/sessione incoerente.  |
| `404 Not Found`             | Stato neutro “Risorsa non trovata”; offrire ritorno alla lista. Include risorsa inesistente, non collegata o non appartenente al principal: Availability non collegata e Booking estraneo non vanno distinti nella UI. |
| `409 Conflict`              | Stato concorrente/obsoleto, slot sovrapposto o transizione booking non più valida. Non usare più questo status per email duplicata. Mostrare messaggio e ricaricare la risorsa quando opportuno.                       |
| `410 Gone`                  | Per `EMAIL_VERIFICATION_TOKEN_EXPIRED`, proporre il reinvio della verifica email.                                                                                                                                      |
| `405/406/415`               | Errore di integrazione del client: non ritentare invariando metodo, `Accept` o `Content-Type`; 405 include `Allow`.                                                                                                    |
| `500 Internal Server Error` | Messaggio neutro, possibilità di riprovare e nessun dettaglio tecnico.                                                                                                                                                 |

Il client deve trattare separatamente gli errori di rete senza response HTTP.

Regole pratiche:

- usare `code` per decidere il comportamento, senza basarsi sul testo italiano;
- raggruppare `fieldErrors` per `field`, mostrando anche tutti gli errori globali; non assumere una sola violazione per campo;
- se la risposta non rispetta `ErrorResponse` o la rete non risponde, usare un fallback come “Impossibile completare l'operazione. Riprova.”;
- dopo un `409` su slot o booking, invalidare i dati locali e ricaricare lista/dettaglio;
- non mostrare stack trace, payload tecnici o identificatori interni non necessari;
- non inviare proprietà JSON sconosciute: il backend le rifiuta deliberatamente.

## 10. Stati UI necessari

| Tipo pagina/azione                  | Loading                                     | Empty                                                         | Error/forbidden                                                                 | Success e validazione                                                          |
| ----------------------------------- | ------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Login/registrazione                 | Disabilitare submit e mostrare progresso    | Non applicabile                                               | Errore generale e per campo; stato account distinto                             | Redirect o conferma; validazione client coerente ma il server resta autorevole |
| Verifica email/invito               | Stato iniziale automatico o submit in corso | Token/codice mancante                                         | Non valido, usato, scaduto o non trovato                                        | Conferma e CTA al passo successivo                                             |
| Dashboard                           | Skeleton dei widget                         | Messaggio utile senza dati inventati                          | Errore per singolo blocco, non pagina bianca se gli altri dati sono disponibili | Dati composti dagli endpoint esistenti                                         |
| Liste clienti/professionisti/inviti | Skeleton righe/card                         | “Nessun … disponibile” con CTA solo se esiste un'azione reale | Retry; `403` dedicato                                                           | Lista aggiornata dopo mutazioni                                                |
| Availability                        | Skeleton lista                              | Cliente: nessuno slot prenotabile; PT: nessuno slot creato    | Conflitto, slot obsoleto, ruolo/specializzazione non valida                     | Slot creato/modificato/bloccato con feedback                                   |
| Booking                             | Skeleton lista/dettaglio                    | Nessuna richiesta                                             | `403`, `404`, conflitto di transizione, slot non più disponibile                | Stato aggiornato dalla risposta server                                         |
| Profilo/account                     | Skeleton / loading sezione                  | Non applicabile                                               | Errori campo, globali, `401` via auth foundation                                | Conferma dopo soft commit; draft annullabile                                   |
| Azioni distruttive o irreversibili  | Stato pending sulla singola azione          | Non applicabile                                               | Ripristinare controlli e mostrare errore                                        | Conferma per cancellazione, rifiuto e blocco; evitare doppi click              |

Ogni pagina privata deve prevedere anche gli stati trasversali `unauthorized` e `forbidden`. L'interfaccia deve rispettare i requisiti minimi di accessibilità: focus sull'errore, messaggi associati ai campi, indicatori non affidati al solo colore e pulsanti disabilitati durante le mutazioni.

## 11. Priorità implementazione React

Completati: setup React/Vite/TypeScript, routing, layout, home pubblica, foundation API/auth session-based (httpClient, CSRF, AuthProvider, login, logout, guards, bootstrap), **Profilo/Account/Operational Status** collegati a `/me`, onboarding pubblico PROFESSIONAL e CLIENT/verifica email, **gestione inviti PROFESSIONAL** e liste/dettagli delle relazioni CLIENT ↔ PROFESSIONAL. Dettagli auth: [FE03](./03-authentication-session-flow.md); inviti: [FE05](./05-professional-invites-implementation.md); onboarding CLIENT: [FE06](./06-client-onboarding-implementation.md); relazioni: [FE07](./07-client-professional-relationships-implementation.md).

Ordine pragmatico **residuo**:

1. dashboard base composte, senza analytics;
2. Availability e Booking del personal trainer, poi Booking cliente;
3. hardening di errori, accessibilità, responsive e test dei flussi applicativi business residui.

La scelta del prossimo vertical slice business resta una decisione di prodotto. Availability e Booking vanno progettati insieme sul piano UX perché le transizioni booking modificano gli slot.

## 12. Cosa NON implementare nella prima fase frontend

- un design system complesso o una libreria interna estesa;
- JWT, Bearer auth, refresh token o storage di token in `localStorage`/`sessionStorage`;
- Workout, Nutrition, Feedback o Measurements;
- recupero/reset o cambio password;
- upload immagine profilo;
- gestione manuale dei collegamenti o disattivazione inviti;
- applicazione mobile React Native / Expo;
- grafici, statistiche e dashboard analitiche;
- calendario drag-and-drop, ricorrenze o viste complesse;
- filtri, paginazione o query param server non implementati;
- motivo di rifiuto booking, notifiche, chat, pagamenti o funzioni admin;
- nuove API o workaround che simulino feature mancanti nel solo client.

Per Availability è sufficiente iniziare con una lista cronologica e form data/ora. Un calendario semplice può essere valutato dopo aver validato il flusso, senza introdurre semantiche non presenti nel backend.

## 13. Output utile per Figma

### 13.1 Pagine da disegnare

- landing;
- login;
- registrazione professionista e conferma “verifica email”;
- verifica email nei quattro esiti principali;
- validazione invito e registrazione cliente;
- layout privato responsive;
- dashboard cliente;
- profilo/account cliente;
- lista e dettaglio professionisti;
- disponibilità prenotabili e conferma booking;
- lista e dettaglio booking cliente;
- dashboard professionista nelle varianti personal trainer e nutrizionista;
- profilo/account professionista;
- lista e dettaglio clienti;
- lista inviti e invito appena generato;
- lista/form availability del personal trainer;
- lista e dettaglio booking del personal trainer;
- pagine 401/sessione scaduta, 403 e 404.

### 13.2 Componenti e layout ricorrenti

- app shell con header, navigazione laterale/drawer e menu utente;
- route guard con stato di bootstrap;
- page header, breadcrumb e area azioni;
- card profilo professionista/cliente;
- badge per specializzazione, stato operativo, slot, invito e booking;
- avatar con immagine esistente o iniziali;
- alert/banner, toast e pannello errore con retry;
- skeleton, empty state e confirmation dialog;
- campo password, date picker, date-time input, select enum e textarea con contatore;
- tabella desktop con equivalente lista/card mobile;
- dettaglio key-value per account e booking.

### 13.3 Form, liste e conferme principali

- form login;
- form registrazione professionista;
- form codice invito e registrazione cliente;
- form profilo differenziato per ruolo;
- selezione stato operativo;
- form creazione/modifica slot;
- form booking con nota facoltativa;
- lista clienti, professionisti, inviti, slot e booking;
- conferme per cancellare booking, rifiutare booking e bloccare slot.

I prototipi devono includere loading, empty, errore, successo, validazione, unauthorized e forbidden, non soltanto l'happy path.

I form che impostano una nuova password devono mostrare una validazione preventiva del massimo di **72 byte UTF-8**, distinguendo byte e caratteri Unicode. Il frontend non deve troncare o trasformare il valore e non deve considerare il controllo locale una protezione sufficiente: il backend resta la fonte autoritativa. Nel login, una password oltre limite va presentata come generico errore di credenziali (`401 AUTHENTICATION_ERROR`), senza indicare se l’account esiste o se la causa è la lunghezza.

### Contratto frontend per gli orari degli slot

Un controllo HTML `datetime-local` non produce alcun offset. Per creare o modificare uno slot, il frontend deve:

- presentare chiaramente all'utente la zona `Europe/Rome`;
- associare il valore civile a `Europe/Rome`, senza usare implicitamente la timezone del browser;
- calcolare l'offset effettivo valido per quella specifica data e inviare una stringa ISO-8601, per esempio `2026-07-13T17:30:00+02:00` o `2026-01-13T17:30:00+01:00`;
- non aggiungere `Z` a una data locale e non inviare il precedente formato senza offset;
- impedire o segnalare gli orari nel gap primaverile e nell'overlap autunnale;
- inviare precisione massima al secondo, senza frazioni non nulle;
- trattare l'offset delle response Availability e degli orari snapshot Booking come già autorevole, senza una seconda conversione silenziosa.

Il backend resta la fonte autoritativa e ripete tutte le validazioni. Gli audit `createdAt`/`updatedAt` fanno parte del contratto come `Instant` ISO-8601 UTC con `Z`.

## 14. Punti da chiarire prima dell'implementazione

Questi punti non impediscono la mappa funzionale, ma non sono determinabili come contratto frontend completo dal repository attuale:

1. **Consegna verifica email:** nel ramo di una nuova registrazione e nei reinvii idonei il backend crea e salva il token e, dopo commit, usa una porta di consegna con adapter SMTP configurabile; la risposta pubblica neutra non prova che questo ramo sia stato eseguito. Il default locale resta disabilitato; la porta in-memory è solo per test/debug e non è esposta via endpoint. L'URL frontend remoto deve essere HTTPS; HTTP è ammesso solo per loopback locale. Non sono presenti outbox o retry, quindi la consegna non è garantita.
2. **Contratto temporale:** audit e scadenze account/booking/inviti arrivano come `Instant` UTC con `Z`; gli orari degli slot e gli snapshot Booking arrivano con offset esplicito `Europe/Rome`, mentre le date civili restano `LocalDate`. La UI deve distinguere questi tre tipi e non applicare una timezone globale ai payload.
3. **Auth client:** implementata session-based (vedi [FE03](./03-authentication-session-flow.md)); nessun JWT/Bearer/storage. Profilo/Account/Status, onboarding PROFESSIONAL/verifica email, onboarding CLIENT e gestione inviti PROFESSIONAL sono collegati (dettaglio: [FE04](./04-professional-onboarding-implementation.md), [FE05](./05-professional-invites-implementation.md), [FE06](./06-client-onboarding-implementation.md)); restano da collegare le altre pagine business.
4. **URL pubblico dei link:** `app.email.verification-page-url` configura la pagina di verifica, ma il valore pubblico definitivo di ciascun ambiente non è ancora definito; i link invito restano separati.
5. **Liste:** non esistono paginazione e filtri API per clienti, professionisti, inviti, slot o booking; la prima UI non deve dipenderne. Per Booking l'ordine iniziale è `createdAt DESC, id DESC`.
6. **Dashboard:** non esiste un contratto aggregato; contenuti e metriche devono restare una composizione minima dei dati già disponibili.
