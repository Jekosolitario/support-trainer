# Frontend Functional Map MVP - Support Trainer

## 1. Obiettivo del documento

Questo documento traduce il backend MVP reale di Support Trainer in una mappa funzionale per UX/UI, prototipazione Figma e futura implementazione web con React. Non definisce nuove API e non amplia il perimetro del prodotto.

Le fonti verificate sono:

- [`README.md`](../../README.md);
- [`final-audit-mvp.md`](../final-audit-mvp.md);
- [`08-endpoint-map.md`](../08-endpoint-map.md);
- [`07-api-modules-overview.md`](../07-api-modules-overview.md);
- [`09-security-flow.md`](../09-security-flow.md);
- i documenti di sprint Profile, Availability e Booking;
- controller, DTO, configurazione Spring Security, servizi ed enum presenti nel backend.

La mappa distingue sempre tre stati:

| Stato | Significato per il frontend |
|---|---|
| **Implementabile ora** | Esiste un contratto backend utilizzabile nell'MVP. |
| **Futuro / non attivo** | La UX può prevederne la posizione, ma non deve presentarlo come funzionante. |
| **Non presente** | Non deve comparire come azione attiva né generare chiamate API. |

La presenza di una pagina frontend non implica necessariamente un endpoint dedicato: una landing statica non ne richiede uno, mentre una dashboard MVP deve comporre dati restituiti da endpoint già esistenti. Nel backend risultano implementati **28 endpoint**.

## 2. Stato del frontend

- La directory `frontend/` è vuota: il frontend non è ancora implementato.
- La direzione tecnica scelta è **React + TypeScript + Vite**.
- Non esistono ancora routing, componenti, API client, gestione dello stato auth o design system.
- Il web frontend dovrà comunicare con API REST JSON sotto il prefisso `/api/v1`.
- Un'app mobile con React Native + Expo è una possibile evoluzione futura, fuori dall'MVP.
- Questa fase produce solo documentazione: non avvia il progetto React e non modifica il backend.

## 3. Ruoli utente

Nel backend i soli ruoli di sicurezza sono `CLIENT` e `PROFESSIONAL`. `PERSONAL_TRAINER` e `NUTRITIONIST` sono specializzazioni business del ruolo `PROFESSIONAL`, non authority Spring Security distinte. Il visitatore pubblico non è un ruolo persistito.

| Profilo UX | Identità backend | Può fare ora | Non può fare ora | Pagine private necessarie |
|---|---|---|---|---|
| Visitatore pubblico | Nessuna | Consultare contenuti statici, registrare un professionista, validare un invito, registrare un cliente, verificare l'email tramite token, fare login | Accedere a dati applicativi o registrarsi come cliente senza invito | Nessuna |
| Cliente | `CLIENT` | Gestire profilo/account, vedere professionisti collegati, consultare disponibilità di personal trainer collegati, creare e gestire booking consentiti | Creare inviti/slot, vedere clienti, usare moduli Workout, Nutrition, Feedback o Measurements | Dashboard, profilo/account, professionisti, disponibilità, booking |
| Professionista | `PROFESSIONAL` | Gestire profilo/account, vedere clienti collegati, generare e consultare inviti | Usare funzionalità non compatibili con la propria specializzazione; gestire manualmente i link | Dashboard, profilo/account, clienti, inviti; aree specialistiche solo quando supportate |
| Personal trainer | `PROFESSIONAL` + `PERSONAL_TRAINER` | Tutte le funzioni comuni del professionista, più gestione availability e richieste booking | Workout, Feedback e Measurements, ancora non implementati | Anche availability e booking |
| Nutrizionista | `PROFESSIONAL` + `NUTRITIONIST` | Funzioni comuni del professionista: profilo/account, clienti e inviti | Availability e booking basati su slot; piani Nutrition e Feedback, ancora non implementati | Nessuna area operativa Nutrition nell'MVP |

Conseguenze UX:

- il menu va costruito usando prima `role`, poi `specialization` ottenuta da `GET /api/v1/me/profile`;
- un nutrizionista non deve vedere Availability o Booking come aree attive;
- un cliente può essere collegato a più professionisti: le API restituiscono una lista e il backend ammette fino a tre collegamenti attivi;
- la UI deve mostrare soltanto i campi profilo pertinenti al ruolo.

## 4. Funzionalità pubbliche implementabili ora

Gli endpoint sotto `/api/v1/auth/**` sono pubblici.

| Schermata | Scopo ed endpoint | Stati UI necessari | Errori principali |
|---|---|---|---|
| Home / landing | Pagina statica di ingresso. Nessun endpoint richiesto. CTA verso login e registrazione professionista; l'accesso cliente parte da un invito. | Contenuto pronto, eventuale fallback contenuti | Nessun errore API |
| Login | Autenticare cliente o professionista con `POST /api/v1/auth/login`. Campi: email e password. | Idle, invio, successo e redirect per ruolo, errore credenziali, account non attivo | `400` validazione/body, `401 AUTHENTICATION_ERROR`, `403 ACCOUNT_NOT_ACTIVE`, `EMAIL_NOT_VERIFIED`, profilo non attivo |
| Registrazione professionista | Creare un account con `POST /api/v1/auth/register/professional`. Campi: nome, cognome, email, password, specializzazione `PERSONAL_TRAINER` o `NUTRITIONIST`. | Form, validazione, invio, `201`, istruzione di verifica email | `400 VALIDATION_ERROR`, `409 EMAIL_ALREADY_REGISTERED` |
| Verifica email | Ricevere `/verify-email#token=...`, rimuovere subito il fragment e inviare `POST /api/v1/auth/email-verification/confirm` con body `token`. | Verifica in corso, verificata, non valido o scaduto; secondo utilizzo idempotente; CTA al login dopo successo | `400` body/validazione, `404 EMAIL_VERIFICATION_TOKEN_NOT_FOUND`, `410 EMAIL_VERIFICATION_TOKEN_EXPIRED` |
| Reinvio verifica | Dalla schermata “Controlla la tua email”, inviare `POST /api/v1/auth/email-verification/resend` con body `email`. | Messaggio sempre neutro; azione “Invia di nuovo”; pulsante UX disabilitato 60 secondi, senza assumere che il backend abbia creato un token | `400` validazione/body, `415` media type; ogni email valida riceve `202` identico |
| Validazione invito | Verificare il codice prima di mostrare il form cliente con `POST /api/v1/auth/register/client/validate-invite`. Body: `code`. | Form codice, validazione, valido con scadenza, non valido/scaduto/usato, retry | `400 VALIDATION_ERROR` e codici `INVITE_CODE_*`; `404 INVITE_CODE_NOT_FOUND` |
| Registrazione cliente | Creare l'account con `POST /api/v1/auth/register/client` dopo validazione invito. Campi: nome, cognome, email, password, codice, data di nascita, altezza, obiettivo, genere; note mediche/infortuni/generali facoltative. | Form multi-sezione, validazione campo, invio, `201`, schermata “Controlla la tua email” | `400` validazione o invito non più valido, `403` professionista non utilizzabile, `409 EMAIL_ALREADY_REGISTERED` |
| Pagine informative statiche | Eventuali pagine legali o informative non richiedono backend, ma vanno create solo quando contenuti e requisiti sono definiti. | Contenuto e pagina non trovata frontend | Nessun errore API |

Note di flusso verificate:

- le risposte di registrazione contengono identità e ruolo, ma `accessToken`, `refreshToken` e `tokenType` sono `null`; dopo la registrazione occorre passare dal login;
- cliente e professionista nascono `PENDING_VERIFICATION`, con `emailVerified=false`, e non possono fare login prima della conferma;
- per il cliente il link è già creato e l'invito consumato, ma il cliente pending non è visibile al professionista;
- il backend genera e persiste per entrambi un token valido 24 ore e, dopo commit, affida un link con token nel fragment alla porta email; il default locale non invia e non esiste ancora un adapter SMTP reale;
- la futura pagina deve leggere il fragment, rimuoverlo immediatamente dall'URL prima di avviare analytics, monitoring o altre integrazioni, conservarlo solo in memoria e non inserirlo in `localStorage`; invia quindi il POST, mostra la CTA login in caso di successo e il messaggio neutro di reinvio in caso di 410;
- la pagina di verifica non deve usare `HashRouter`, perché il fragment è riservato al token, né trasmettere il token a strumenti di analytics o monitoring;
- l'azione “Invia di nuovo” usa l'email della registrazione, non mostra se l'account esiste e non invia email o token ad analytics; il frontend può disabilitare il pulsante per 60 secondi, ma il backend resta autoritativo e non espone il tempo residuo;
- il codice invito è monouso, non è legato a una specifica email destinataria e scade dopo 7 giorni.

## 5. Funzionalità private implementabili ora

### 5.1 Area comune autenticata

| Area | Endpoint | Stato MVP | Note UX |
|---|---|---|---|
| Bootstrap sessione | `GET /api/v1/me/account`, `GET /api/v1/me/profile` | Implementabile ora | Confermare ruolo, stato account, specializzazione e dati profilo dopo il login o il reload. Non ricavare il ruolo dal JWT: non è presente nei claim. |
| Profilo/account | stessi endpoint di lettura; `PATCH /api/v1/me/profile` | Implementabile ora | Un'unica pagina può avere sezioni “Profilo” e “Account”. Mostrare campi diversi per cliente e professionista. |
| Stato operativo | `PATCH /api/v1/me/profile/operational-status` | Implementabile ora | Cliente: `ATTIVO`, `INFORTUNATO`, `PAUSA`. Professionista: `DISPONIBILE`, `ASSENTE`, `FERIE`, `MALATTIA`. |
| Immagine profilo | Campo `profileImageUrl` in lettura | Solo visualizzazione se già valorizzato | Nessun upload o update immagine: usare iniziali/avatar di fallback e non mostrare un controllo file attivo. |

Nel `PATCH` profilo professionista, `instagramUrl` e `websiteUrl` seguono un contratto a tre stati: campo omesso o `null` = invariato; URL `http://`/`https://` = aggiornato; stringa vuota = rimosso. Il form deve conservare esplicitamente questa distinzione.

### 5.2 Cliente

| Area/pagina | Endpoint | Stato MVP | Note UX |
|---|---|---|---|
| Dashboard cliente | Composizione di `GET /api/v1/professionals/my` e `GET /api/v1/bookings/client` | Implementabile ora come composizione | Non esiste un endpoint dashboard. Mostrare riepiloghi derivati senza promettere statistiche avanzate. |
| Profilo/account | Endpoint `/api/v1/me/**` | Implementabile ora | Form per dati anagrafici e note pertinenti al cliente. |
| Professionisti collegati | `GET /api/v1/professionals/my` | Implementabile ora | Lista, non singolo professionista. Empty state se non emergono collegamenti leggibili. |
| Dettaglio professionista | `GET /api/v1/professionals/{professionalId}` | Implementabile ora | Accessibile soltanto con collegamento attivo. Un `404 PROFESSIONAL_NOT_FOUND` non permette di distinguere professionista inesistente e non accessibile: mostrare uno stato neutro e tornare alla lista. |
| Disponibilità professionista | `GET /api/v1/professionals/{professionalId}/availability` | Implementabile ora solo per personal trainer collegato | Mostrare solo slot restituiti dal server. Per un nutrizionista l'area non va offerta. Empty state distinto da errore. |
| Crea booking | `POST /api/v1/bookings` | Implementabile ora | Body: un solo `availabilitySlotId` e nota facoltativa fino a 1000 caratteri. Confermare data/ora e professionista prima dell'invio. |
| Lista booking | `GET /api/v1/bookings/client` | Implementabile ora | Stati: `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`. Nessun filtro server documentato. |
| Dettaglio booking | `GET /api/v1/bookings/{bookingRequestId}` | Implementabile ora | Solo utenti coinvolti. Mostrare intervallo storico restituito in `items`. |
| Cancella booking | `PATCH /api/v1/bookings/{bookingRequestId}/cancel` | Implementabile ora | Il cliente può cancellare `PENDING` o `CONFIRMED`; usare conferma esplicita e aggiornare lo stato dalla risposta. |

### 5.3 Professionista: funzionalità comuni

| Area/pagina | Endpoint | Stato MVP | Note UX |
|---|---|---|---|
| Dashboard professionista | Composizione di clienti, inviti e, solo per personal trainer, availability/booking | Implementabile ora come composizione | Nessun endpoint aggregato. I widget devono dipendere dalla specializzazione. |
| Profilo/account | Endpoint `/api/v1/me/**` | Implementabile ora | Campi professionista: contatti, bio, luogo di lavoro, città e link. |
| Clienti collegati | `GET /api/v1/clients/my` | Implementabile ora | Ogni elemento contiene soltanto `id`, `firstName`, `lastName` e `profileImageUrl`; non mostrare obiettivo o stato operativo. |
| Dettaglio cliente | `GET /api/v1/clients/{clientId}` | Implementabile ora | Solo con collegamento attivo. Contiene identità minima e `primaryGoal`. Un `404 CLIENT_NOT_FOUND` non permette di distinguere cliente inesistente e non accessibile: mostrare uno stato neutro e tornare alla lista. |
| Crea invito | `POST /api/v1/invites` | Implementabile ora | Nessun body. Dopo `201`, mostrare codice, scadenza e azione “Copia”. Non inventare invio email automatico. |
| Lista inviti | `GET /api/v1/invites` | Implementabile ora | Mostrare attivo/usato/scaduto derivando lo scaduto da `expiresAt`. Non esistono dettaglio o disattivazione manuale. |

### 5.4 Personal trainer

| Area/pagina | Endpoint | Stato MVP | Note UX |
|---|---|---|---|
| Lista slot | `GET /api/v1/availability/my` | Implementabile ora | Stati `AVAILABLE`, `BLOCKED`, `BOOKED`; lista ordinata per inizio. Nessun calendario avanzato necessario. |
| Crea slot | `POST /api/v1/availability` | Implementabile ora | Inizio e fine futuri, fine successiva all'inizio, nessuna sovrapposizione. |
| Modifica slot | `PATCH /api/v1/availability/{slotId}` | Implementabile ora con vincoli | Solo slot `AVAILABLE`, mai coinvolti in booking e senza booking `PENDING`. Il `PATCH` è parziale. |
| Blocca/sblocca slot | `PATCH .../{slotId}/block`, `PATCH .../{slotId}/unblock` | Implementabile ora | Azioni visibili solo negli stati coerenti. Uno slot con booking pending non è bloccabile. |
| Lista booking ricevuti | `GET /api/v1/bookings/professional` | Implementabile ora | Mostrare richieste in ordine recente e stato. Nessun filtro server documentato. |
| Dettaglio booking | `GET /api/v1/bookings/{bookingRequestId}` | Implementabile ora | Mostrare cliente, nota, stato e intervallo. |
| Conferma/rifiuta | `PATCH .../{id}/confirm`, `PATCH .../{id}/reject` | Implementabile ora | Solo da `PENDING`. Il rifiuto non accetta un motivo: non mostrare un campo motivo attivo. |
| Cancella | `PATCH .../{id}/cancel` | Implementabile ora | Il professionista può cancellare soltanto un booking `CONFIRMED`. |

### 5.5 Nutrizionista

Il nutrizionista usa le funzionalità comuni del professionista, ma non dispone di un modulo Nutrition attivo. Availability e Booking tramite slot sono bloccati nel service layer. Dashboard e navigazione devono quindi limitarsi a profilo/account, clienti e inviti. Una voce Nutrition può comparire solo in wireframe futuro o, se davvero utile alla comunicazione, disabilitata con badge “In arrivo”; nell'MVP operativo è preferibile nasconderla.

### 5.6 Contratto frontend del profilo cliente condiviso

- PT e nutrizionista usano temporaneamente lo stesso contratto minimo;
- la lista non deve mostrare o conservare `primaryGoal`, `operationalStatus` o altri campi assenti;
- il dettaglio aggiunge soltanto `primaryGoal` all'identità della lista;
- dati fisici e note non devono entrare nello state della pagina professionista, in cache persistenti o analytics;
- il profilo personale del cliente ottenuto da `/api/v1/me/**` è un contratto distinto e non va riutilizzato nelle schermate professionista;
- schermate anamnesi, infortuni o note professionali non fanno parte dell'MVP corrente.

## 6. Funzionalità future da prevedere ma non attivare

| Funzionalità | Posizione UX futura possibile | Come trattarla ora |
|---|---|---|
| Workout | Area personal trainer e area cliente | Nascosta nell'MVP operativo; placeholder solo nei wireframe, marcato “Futuro / non attivo”. |
| Nutrition | Area nutrizionista e area cliente | Nascosta; non usare l'assenza di booking per simulare piani alimentari. |
| Feedback | Dettaglio futuro di workout/nutrition o area progressi | Nascosto: non esistono endpoint né dati. |
| Measurements | Profilo/progressi cliente | Nascosta; niente grafici o inserimento misure. |
| Reset password | Login / recupero account | Non mostrare un link attivo. Può apparire disabilitato solo in prototipi esplicitamente futuri. |
| Logout backend | Menu utente | È possibile un'azione locale “Esci” che cancella la sessione frontend, ma non deve chiamare o promettere revoca server: l'endpoint non esiste. |
| Refresh automatico token | API client/auth state | Non attivare. Alla scadenza dell'access token richiedere un nuovo login. |
| Upload immagine profilo | Profilo | Mostrare immagine esistente o avatar fallback; nessun controllo upload attivo. |
| App mobile | Fuori dalla web app | Nessun elemento nell'MVP web. React Native + Expo resta un'evoluzione separata. |

Sono inoltre non presenti e da non esporre come attivi: cambio password autenticato, gestione manuale dei collegamenti, disattivazione inviti, motivo di rifiuto booking, notifiche, pagamenti, chat, amministrazione e statistiche avanzate.

## 7. Sitemap MVP

I path seguenti sono una convenzione frontend proposta: il repository non contiene ancora un router. È usato il plurale `professionals` perché il backend restituisce una lista di professionisti collegati.

### Pubblico

```text
/
/login
/register/professional
/invite/validate
/register/client
/verify-email#token=...
```

Flusso cliente consigliato: `/invite/validate` valida il codice e, in caso positivo, passa a `/register/client` conservando il codice. Non saltare la validazione lato server durante la registrazione: il backend la ripete correttamente.

Dopo entrambe le registrazioni il frontend mostra “Controlla la tua email”. Il futuro link apre `/verify-email`, la pagina legge e rimuove il token dall'URL, effettua il POST e presenta la CTA login. Un secondo utilizzo coerente resta un successo. La stessa schermata offre “Invia di nuovo”: invia l'email al POST di resend, mostra sempre il messaggio neutro e applica un blocco UX di 60 secondi, senza sostituire il cooldown del backend. Il frontend applicativo e la consegna email non sono implementati in questo step.

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

## 8. Protezione rotte frontend

### 8.1 Classificazione

- Rotte pubbliche: landing, login, registrazioni, validazione invito e verifica email.
- Rotte private comuni: profilo/account e bootstrap sessione.
- Rotte `CLIENT`: professionisti collegati, availability consultabile e booking cliente.
- Rotte `PROFESSIONAL`: clienti e inviti.
- Rotte `PROFESSIONAL + PERSONAL_TRAINER`: availability e booking professionista.

Le guard frontend migliorano navigazione e chiarezza, ma non sostituiscono l'autorizzazione backend.

### 8.2 Access token e bootstrap

1. Il login restituisce `accessToken`, `refreshToken`, `tokenType`, `userId`, `email` e `role`.
2. L'API client aggiunge `Authorization: Bearer <accessToken>` solo alle richieste protette.
3. Dopo login o ripristino sessione, il frontend legge `/me/account` e `/me/profile` prima di costruire navigazione e route specialistiche.
4. Il JWT contiene email e tipo token, ma non ruolo, user id o specializzazione: non usarlo come unica fonte dello stato utente.
5. Il refresh token è restituito ma non è utilizzabile: non inviarlo come Bearer e, nell'MVP, non è necessario conservarlo.

La persistenza del token non è definita dal backend. Raccomandazione MVP: isolare la scelta dietro un servizio auth e preferire persistenza di sessione limitata rispetto a persistenza indefinita. Qualunque storage JavaScript resta esposto a XSS; la scelta definitiva va riesaminata insieme al futuro lifecycle refresh.

### 8.3 Contratto CORS per il frontend

- l'origine effettiva del frontend deve comparire esattamente in `app.cors.allowed-origins`, inclusa l'eventuale porta;
- non sono ammesse wildcard, path, query string o fragment;
- le chiamate protette possono inviare `Authorization: Bearer <accessToken>` e i payload JSON possono usare `Content-Type`;
- il preflight `OPTIONS` è gestito dal backend, ma viene rifiutato se l'origine non è configurata;
- non usare richieste con credenziali browser: `allowCredentials` è deliberatamente disabilitato.

Il valore cambia per ambiente tramite configurazione Spring o `APP_CORS_ALLOWED_ORIGINS`. Il frontend non deve codificare un'origine backend o frontend di produzione nel sorgente.

### 8.4 Risposte 401, 403 e 404

- `401` durante il login: mostrare l'errore nel form, senza redirect ciclici.
- `401` su una rotta privata (`UNAUTHORIZED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`): cancellare la sessione locale, conservare se utile la destinazione, e reindirizzare a `/login` con messaggio “Sessione scaduta” o “Accesso richiesto”. Non tentare refresh automatici.
- `403 ACCESS_DENIED` da SecurityConfig: lasciare intatta la sessione, mostrare pagina “Non autorizzato” e offrire ritorno alla dashboard corretta.
- nei dettagli cliente e professionista, `404 CLIENT_NOT_FOUND` e `404 PROFESSIONAL_NOT_FOUND` coprono in modo indistinguibile ID inesistente, relazione assente o inattiva e profilo non leggibile; il frontend non deve tentare di dedurre quale caso si sia verificato;
- gli altri `403` business, per esempio specializzazione non consentita in flussi diversi, mantengono il comportamento contestuale esistente.

L'azione “Esci” dell'MVP è solo frontend: elimina token e stato utente e torna al login. Non esiste revoca backend.

## 9. Gestione errori frontend

Il contratto comune reale è `ErrorResponse`:

```json
{
  "timestamp": "2026-07-04T12:00:00",
  "status": 400,
  "error": "BAD_REQUEST",
  "errorCode": "VALIDATION_ERROR",
  "message": "Dati non validi",
  "validationErrors": {
    "email": "Formato email non valido"
  }
}
```

| Campo | Uso frontend |
|---|---|
| `timestamp` | Diagnostica; non necessario nel messaggio principale. |
| `status` | Comportamento HTTP generale. |
| `error` | Nome standard dello stato HTTP. |
| `errorCode` | Identificatore applicativo per comportamento e copy specifici. |
| `message` | Messaggio generale mostrabile, con fallback frontend. |
| `validationErrors` | Mappa `campo -> messaggio`; collegare gli errori ai controlli del form. Può essere `null`. |

| Status | Comportamento UX |
|---|---|
| `400 Bad Request` | Mostrare errori campo per `VALIDATION_ERROR`; per body, path o query malformati mostrare messaggio generale. Comprende anche alcune violazioni di stato/invito. |
| `401 Unauthorized` | Login: errore credenziali. Area privata: sessione assente/scaduta/non valida e nuovo login. |
| `403 Forbidden` | Utente autenticato ma il ruolo non consente l'endpoint, oppure un altro flusso business nega l'operazione. Non fare logout automatico. |
| `404 Not Found` | Stato neutro “Risorsa non trovata”; offrire ritorno alla lista. Nei dettagli cliente/professionista include anche la risorsa fuori dal perimetro del principal e non deve essere interpretato per distinguere il motivo. |
| `409 Conflict` | Dato già esistente o stato concorrente/obsoleto, per esempio email duplicata, slot sovrapposto o transizione booking non più valida. Mostrare messaggio e ricaricare la risorsa quando opportuno. |
| `500 Internal Server Error` | Messaggio neutro, possibilità di riprovare e nessun dettaglio tecnico. |

Il backend uniforma anche `405 Method Not Allowed` e `415 Unsupported Media Type`: normalmente indicano un errore d'integrazione del frontend e vanno loggati, presentando all'utente un fallback generico.

Regole pratiche:

- usare `errorCode` per decidere il comportamento, senza basarsi sul testo italiano;
- mostrare `validationErrors[field]` sotto il campo e un riepilogo accessibile a inizio form;
- se la risposta non rispetta `ErrorResponse` o la rete non risponde, usare un fallback come “Impossibile completare l'operazione. Riprova.”;
- dopo un `409` su slot o booking, invalidare i dati locali e ricaricare lista/dettaglio;
- non mostrare stack trace, payload tecnici o identificatori interni non necessari.

## 10. Stati UI necessari

| Tipo pagina/azione | Loading | Empty | Error/forbidden | Success e validazione |
|---|---|---|---|---|
| Login/registrazione | Disabilitare submit e mostrare progresso | Non applicabile | Errore generale e per campo; stato account distinto | Redirect o conferma; validazione client coerente ma il server resta autorevole |
| Verifica email/invito | Stato iniziale automatico o submit in corso | Token/codice mancante | Non valido, usato, scaduto o non trovato | Conferma e CTA al passo successivo |
| Dashboard | Skeleton dei widget | Messaggio utile senza dati inventati | Errore per singolo blocco, non pagina bianca se gli altri dati sono disponibili | Dati composti dagli endpoint esistenti |
| Liste clienti/professionisti/inviti | Skeleton righe/card | “Nessun … disponibile” con CTA solo se esiste un'azione reale | Retry; `403` dedicato | Lista aggiornata dopo mutazioni |
| Availability | Skeleton lista | Cliente: nessuno slot prenotabile; PT: nessuno slot creato | Conflitto, slot obsoleto, ruolo/specializzazione non valida | Slot creato/modificato/bloccato con feedback |
| Booking | Skeleton lista/dettaglio | Nessuna richiesta | `403`, `404`, conflitto di transizione, slot non più disponibile | Stato aggiornato dalla risposta server |
| Profilo/account | Skeleton form | Non applicabile | Errori campo, `401`, `403` | Conferma salvataggio senza perdere valori non modificati |
| Azioni distruttive o irreversibili | Stato pending sulla singola azione | Non applicabile | Ripristinare controlli e mostrare errore | Conferma per cancellazione, rifiuto e blocco; evitare doppi click |

Ogni pagina privata deve prevedere anche gli stati trasversali `unauthorized` e `forbidden`. L'interfaccia deve rispettare i requisiti minimi di accessibilità: focus sull'errore, messaggi associati ai campi, indicatori non affidati al solo colore e pulsanti disabilitati durante le mutazioni.

## 11. Priorità implementazione React

Ordine pragmatico suggerito per la futura fase di sviluppo:

1. setup React + Vite + TypeScript e configurazione ambiente API;
2. routing, layout pubblico/privato e pagine 404/403;
3. API client tipizzato e normalizzazione `ErrorResponse`;
4. auth state, persistenza sessione, guard per ruolo e specializzazione;
5. login e logout locale;
6. bootstrap con `/me/account` e `/me/profile`, poi profilo/account;
7. dashboard base composte, senza analytics;
8. flusso professionista comune: clienti e inviti;
9. flusso cliente: professionisti collegati e dettaglio;
10. availability e booking del personal trainer, poi booking cliente;
11. registrazione professionista, verifica email e registrazione cliente tramite invito;
12. hardening di errori, accessibilità, responsive e test dei flussi.

Login e bootstrap vanno completati prima delle aree business. Availability e Booking vanno sviluppati insieme sul piano UX perché le transizioni booking modificano la disponibilità degli slot.

## 12. Cosa NON implementare nella prima fase frontend

- un design system complesso o una libreria interna estesa;
- refresh automatico, rotazione o revoca token;
- un falso logout backend: nell'MVP è solo pulizia locale;
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
- trattare l'offset delle response Availability e degli item Booking come già autorevole, senza una seconda conversione silenziosa.

Il backend resta la fonte autoritativa e ripete tutte le validazioni. Gli audit `createdAt`/`updatedAt` fanno parte del contratto come `Instant` ISO-8601 UTC con `Z`.

## 14. Punti da chiarire prima dell'implementazione

Questi punti non impediscono la mappa funzionale, ma non sono determinabili come contratto frontend completo dal repository attuale:

1. **Consegna verifica email:** registrazione e reinvio creano e salvano il token e, dopo commit, producono una richiesta di consegna; non esiste ancora l'adapter SMTP reale e il default locale è disabilitato. La porta in-memory è solo per test/debug e non è esposta via endpoint.
2. **Contratto temporale:** audit e scadenze account/booking/inviti arrivano come `Instant` UTC con `Z`; gli orari degli slot conservano l'offset esplicito `Europe/Rome` e le date civili restano `LocalDate`. La UI deve distinguere questi tre tipi e non applicare una timezone globale ai payload.
3. **Storage auth:** il backend non prescrive dove conservare l'access token; la strategia va chiusa considerando sicurezza e assenza del refresh operativo.
4. **URL pubblico dei link:** `app.email.verification-page-url` configura la pagina di verifica, ma il valore pubblico definitivo di ciascun ambiente non è ancora definito; i link invito restano separati.
5. **Liste:** non esistono paginazione e filtri API per clienti, professionisti, inviti, slot o booking; la prima UI non deve dipenderne.
6. **Dashboard:** non esiste un contratto aggregato; contenuti e metriche devono restare una composizione minima dei dati già disponibili.
