# Support Trainer

## 1. Descrizione

Support Trainer è un progetto full stack per la gestione del rapporto tra professionisti del benessere e clienti. Il backend MVP copre autenticazione, profili, inviti, collegamenti professionista-cliente, disponibilità e richieste di prenotazione. Il frontend dispone di una fondazione React, home pubblica (direzione visuale dark-tech), autenticazione session-based (login/logout/CSRF/guards) e foundation API client, oltre alla pagina Profilo autenticata con Account in sola lettura e Operational Status.

Il repository contiene il backend Spring Boot, il frontend separato e la documentazione funzionale e tecnica. Login, logout, routing protetto, foundation auth, registrazione pubblica PROFESSIONAL, verifica email con resend e Profilo/Account/Operational Status sono implementati. Restano prevalentemente placeholder le altre pagine business private (dashboard con dati, clients, professionals, availability, bookings) e i flussi pubblici di validazione invito e registrazione CLIENT.

## 2. Stato attuale del progetto

Il backend MVP è implementato e validato: comprende API REST protette tramite sessione server-side (Spring Session JDBC + Spring Security 7), persistenza JPA, migrazioni Flyway applicate al database MySQL locale e test automatici standard con database H2 in memoria.

Stato sintetico:

- backend Spring Boot presente;
- API per i flussi principali implementate;
- 31 endpoint applicativi documentati; `/error` resta un fallback tecnico separato;
- autenticazione session-based, CSRF e autorizzazione per ruolo presenti;
- test di integrazione per auth, sessione, inviti, access control, profili, availability, booking e Security / Common presenti;
- database applicativo MySQL con migrazioni Flyway (schema di dominio e infrastruttura Spring Session JDBC);
- fondazione frontend con routing, layout, navigazione per ruolo, pagine di errore e test automatici presente;
- home pubblica responsive/mobile-first implementata sulla route `/` (direzione visuale dark-tech);
- autenticazione session-based frontend implementata (httpClient, CSRF, AuthProvider, login, logout, guards, bootstrap `/me`);
- pagina Profilo autenticata role-aware implementata (CLIENT e PROFESSIONAL), con Account in sola lettura e Operational Status modificabile;
- registrazione pubblica PROFESSIONAL e verifica email (confirm + resend) implementate; validazione invito e registrazione CLIENT ancora placeholder;
- altre pagine business private ancora prevalentemente placeholder;
- pipeline CI GitHub Actions per il backend presente; i gate frontend (lint/test/build) restano locali; deploy non configurato;
- progetto non ancora considerato production-ready.

## 3. Stack tecnico

### Backend

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Spring Security 7
- Spring Session JDBC
- Spring Data JPA / Hibernate
- Flyway 11.14.1, gestito dal BOM Spring Boot
- Jakarta Validation
- Lombok
- script Maven Wrapper 3.3.4 con distribuzione Apache Maven 3.9.12

### Persistenza e test

- MySQL per l'ambiente applicativo
- H2 in memoria per i test
- migrazioni Flyway versionate per le nove tabelle di dominio MySQL e per lo schema Spring Session introdotto da V7
- JUnit
- Spring Test e MockMvc
- AssertJ

### Frontend

- React 19
- TypeScript
- Vite
- React Router
- CSS Modules
- Vitest e React Testing Library
- ESLint / Prettier
- npm (`package-lock.json`)
- Fontsource per gli asset tipografici locali della home

## 4. Funzionalità backend implementate

### Autenticazione e sicurezza

- registrazione professionista;
- registrazione cliente tramite codice invito valido;
- generazione del token di verifica email per professionisti e clienti;
- conferma email uniforme e idempotente tramite `POST /api/v1/auth/email-verification/confirm`;
- reinvio uniforme tramite `POST /api/v1/auth/email-verification/resend`, senza enumerazione degli account;
- richiesta di consegna della verifica email eseguita in modo sincrono soltanto dopo il commit;
- autenticazione server-side con Spring Session JDBC e Spring Security 7;
- `GET /api/v1/auth/csrf` espone `{token, headerName}` con `Cache-Control: no-store`;
- login `POST /api/v1/auth/login` con CSRF → `204 No Content`, cookie di sessione HttpOnly, senza `accessToken`/`refreshToken`/`Authorization`;
- dopo il login il client deve richiedere di nuovo il CSRF (token ruotato) e fare bootstrap con `GET /api/v1/me/account` e `GET /api/v1/me/profile`;
- logout `POST /api/v1/auth/logout` con CSRF → `204`, sessione invalidata;
- cookie produzione `__Host-STSESSION` (`Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/`, senza `Domain`); locale/test `STSESSION` con `Secure=false`;
- timeout 30 minuti di inattività (Spring Session) e 8 ore assolute da `authenticatedAt`;
- eligibilità login: `ACTIVE` + `emailVerified`; `profile.active=false` non blocca il login;
- readiness dinamica su ogni richiesta autenticata tramite `SessionAuthenticationStateFilter`;
- topologia produzione same-origin dietro reverse proxy (`/` frontend, `/api/v1/**` backend); CORS browser non richiesto in produzione;
- nessun JWT runtime, nessun Bearer auth, nessun refresh token;
- autorizzazione per ruolo `PROFESSIONAL` e `CLIENT`;
- controlli applicativi su stato account, specializzazione e proprietà delle risorse;
- gestione uniforme degli errori API.

Entrambi i ruoli nascono con account `PENDING_VERIFICATION` ed `emailVerified=false`, ricevono un token valido 24 ore e possono effettuare login soltanto dopo la conferma. Le due registrazioni pubbliche restituiscono sempre lo stesso `202 Accepted` neutro e non espongono l'esistenza dell'email, ruoli, identificativi o token. Per il cliente l'invito viene validato prima del controllo neutro dell'email: solo per una nuova email il link professionale viene creato e l'invito consumato nella stessa transazione; per un'email già presente l'invito resta inutilizzato. Il resend è l'unico percorso pubblico per richiedere un nuovo invio su un account esistente. Il precedente GET mutante è stato rimosso; token scaduti producono `410 Gone` e un secondo POST sul token già consumato restituisce successo soltanto se lo stato finale dell'utente è coerente.

Il reinvio accetta l'email nel body, risponde sempre `202 Accepted` con lo stesso messaggio per ogni indirizzo sintatticamente valido e crea un token solo per profili attivi ancora pending. Il cooldown è di 60 secondi dal token più recente, con reinvio consentito al boundary esatto. Quando il reinvio è consentito, i precedenti token non usati vengono invalidati tecnicamente tramite `used=true` e `usedAt`, lasciando un solo token utilizzabile da 24 ore. Token, email, stato account e tempo residuo non sono esposti. Invito e link cliente-professionista restano invariati. Dopo il commit di registrazione o reinvio, un listener applicativo costruisce il link `{verification-page-url}#token={tokenEncoded}` e lo affida a una porta indipendente dal provider. Il default locale è `DISABLED`; i test usano il sender `IN_MEMORY` senza rete. In modalità `SMTP`, l'adapter invia un messaggio MIME `text/plain` UTF-8 in italiano con subject comune, URL visibile e scadenza nella zona business. Un errore del sender è assorbito dopo il commit e non cambia le risposte `202`, ma senza outbox o retry la consegna non è garantita. La neutralizzazione non elimina un possibile side-channel di timing; l'MVP non introduce ritardi artificiali o rate limiting. I clienti già persistiti non vengono migrati.

### Profilo e account

- lettura del proprio profilo;
- lettura dei dati account;
- aggiornamento del profilo (campi consentiti per ruolo);
- aggiornamento dello stato operativo, indipendente dall’editing del profilo;
- distinzione tra professionista e cliente;
- specializzazioni professionali `PERSONAL_TRAINER` e `NUTRITIONIST`.

Lo stato operativo è un’informazione di profilo: nell’MVP non implica automaticamente blocco di booking, availability o altre policy applicative.

### Inviti e collegamenti

- generazione e consultazione dei codici invito;
- validazione preventiva del codice;
- registrazione cliente vincolata a un invito valido;
- creazione automatica del collegamento professionista-cliente;
- lettura dei clienti o professionisti collegati.

### Availability

- creazione e aggiornamento degli slot;
- blocco e sblocco degli slot;
- controllo di date e sovrapposizioni;
- gestione degli stati degli slot;
- consultazione degli slot disponibili da parte dei clienti collegati.

Il modulo Availability è attualmente riservato ai professionisti con specializzazione `PERSONAL_TRAINER`.

Gli orari business degli slot usano un contratto ISO-8601 con offset obbligatorio e zona server `Europe/Rome`: per esempio `2026-07-13T17:30:00+02:00` in estate e `2026-01-13T17:30:00+01:00` in inverno. Il backend rifiuta valori senza offset, `Z` o offset incoerenti, orari nel gap primaverile, orari ambigui nell'overlap autunnale e frazioni di secondo non nulle. Le response Booking espongono gli stessi orari con offset, ma li leggono dallo snapshot storico e non dallo slot live.

### Bookings

- creazione di una richiesta di prenotazione;
- consultazione delle richieste lato cliente e professionista;
- dettaglio della prenotazione con controllo di accesso;
- conferma e rifiuto da parte del professionista;
- cancellazione secondo le transizioni consentite;
- aggiornamento coerente dello stato dello slot.

Le liste usano un riepilogo autosufficiente e create, dettaglio e transizioni restituiscono il dettaglio completo. Nome delle parti e orari sono snapshot storici persistiti; immagini profilo e specializzazione del professionista sono invece valori correnti e opzionali. Le response non espongono `primaryGoal`, dati sanitari o `slotStatus` live. Uno storico già creato resta leggibile dai partecipanti originari anche dopo la disattivazione del collegamento, mentre un collegamento attivo resta necessario per creare una nuova prenotazione. Per risorse identificabili, un booking inesistente o non appartenente al principal e uno slot non accessibile al Client restituiscono lo stesso `404`; ruoli e stati account/profilo non idonei restano invece `403`. Le query mutate sono scoperte al principal prima di acquisire il lock pessimista. Le liste sono ordinate per creazione decrescente e id decrescente; paginazione, filtri e motivazioni di rifiuto/annullamento sono rinviati.

## 5. Funzionalità pianificate / non ancora implementate

- schede di allenamento;
- piani alimentari;
- feedback del cliente;
- misurazioni e monitoraggio dei progressi;
- recupero e reset della password;
- upload dell'immagine profilo;
- editing account (email, password, cancellazione) e gestione dispositivi/sessioni;
- limite di sessioni concorrenti;
- API dedicate alla gestione manuale dei collegamenti;
- altre pagine frontend business ancora placeholder (dashboard con dati, clients, professionals, availability, bookings);
- flussi frontend pubblici ancora placeholder: validazione invito e registrazione CLIENT (registrazione PROFESSIONAL e verifica email sono implementate);
- configurazione completa per il deploy.

Follow-up non bloccanti aperti (dettaglio in [Functional Scope](docs/01-functional-scope.md)):

- **E2E-1** — dopo login, un target autenticato ricordato ma incompatibile col ruolo corrente può portare a `/forbidden` (nessun bypass autorizzativo);
- **M1-R** — eventuali errori di campo del form profilo possono restare visibili dopo un aggiornamento stato operativo riuscito e indipendente.

Chat in tempo reale, pagamenti, notifiche push e statistiche avanzate non fanno parte del perimetro attuale.

## 6. Struttura del progetto

```text
support_trainer/
├── backend/
│   ├── src/main/java/       # Codice applicativo
│   ├── src/main/resources/  # Configurazione Spring
│   │   └── db/migration/    # Migrazioni Flyway dello schema runtime
│   ├── src/test/java/       # Test automatici
│   ├── src/test/resources/  # Configurazione del profilo test
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── docs/                    # Analisi, API, modello, frontend e roadmap
├── frontend/
│   ├── src/                 # Routing, layout, componenti, pagine, stili e test
│   ├── package.json
│   └── package-lock.json
├── LICENSE
└── README.md
```

Il backend è organizzato per dominio nei package `auth`, `profile`, `client`, `professional`, `invite`, `availability`, `booking`, `security` e `common`.

## 7. Requisiti

- JDK 21
- MySQL
- Node.js
- npm
- Git
- connessione Internet al primo utilizzo del Maven Wrapper e durante `npm ci`, necessaria per scaricare gli strumenti e le dipendenze

Non è richiesta un'installazione globale di Maven. Il progetto frontend non dichiara attualmente una versione minima specifica di Node.js o npm: l'ambiente usato deve essere compatibile con le dipendenze definite in `frontend/package.json` e bloccate in `frontend/package-lock.json`.

## 8. Configurazione ambiente

1. Entrare nella cartella del backend:

   ```powershell
   cd backend
   ```

2. Copiare il file di esempio:

   ```powershell
   Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties
   ```

   Su Linux o macOS:

   ```bash
   cp src/main/resources/application-example.properties src/main/resources/application.properties
   ```

3. Creare il database MySQL `support_trainer` e valorizzare le proprietà obbligatorie nel file locale ignorato oppure tramite environment:

   | Proprietà                                                           | Variabile d'ambiente                                                                              | Formato                                                                                    |
   | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
   | `spring.datasource.url`                                             | `SPRING_DATASOURCE_URL`                                                                           | URL JDBC MySQL con `connectionTimeZone=%2B00:00` e `forceConnectionTimeZoneToSession=true` |
   | `spring.datasource.username`                                        | `SPRING_DATASOURCE_USERNAME`                                                                      | utenza database                                                                            |
   | `spring.datasource.password`                                        | `SPRING_DATASOURCE_PASSWORD`                                                                      | password database                                                                          |
   | `spring.session.timeout`                                            | `SPRING_SESSION_TIMEOUT`                                                                          | durata positiva; default tracciato `30m`                                                   |
   | `spring.session.jdbc.initialize-schema`                             | —                                                                                                 | `never` (schema sessioni da Flyway V7)                                                     |
   | `server.servlet.session.cookie.name`                                | —                                                                                                 | produzione `__Host-STSESSION`; locale/test `STSESSION`                                     |
   | `server.servlet.session.cookie.http-only`                           | —                                                                                                 | `true`                                                                                     |
   | `server.servlet.session.cookie.secure`                              | —                                                                                                 | produzione `true`; locale/test `false`                                                     |
   | `server.servlet.session.cookie.same-site`                           | —                                                                                                 | `strict`                                                                                   |
   | `server.servlet.session.cookie.path`                                | —                                                                                                 | `/` (non impostare `Domain`)                                                               |
   | `app.time.business-zone`                                            | `APP_TIME_BUSINESS_ZONE`                                                                          | `ZoneId` business; default `Europe/Rome`                                                   |
   | `app.time.clock-zone`                                               | `APP_TIME_CLOCK_ZONE`                                                                             | zona tecnica, deve rappresentare UTC                                                       |
   | `app.email.mode`                                                    | `APP_EMAIL_MODE`                                                                                  | `DISABLED` (default locale sicuro), `IN_MEMORY` oppure `SMTP`                              |
   | `app.email.verification-page-url`                                   | `APP_EMAIL_VERIFICATION_PAGE_URL`                                                                 | URL assoluto senza query/fragment; HTTPS per host remoti, HTTP solo loopback               |
   | `app.email.sender.address`                                          | `APP_EMAIL_SENDER_ADDRESS`                                                                        | indirizzo mittente obbligatorio in `SMTP`                                                  |
   | `app.email.sender.name`                                             | `APP_EMAIL_SENDER_NAME`                                                                           | nome visualizzato obbligatorio in `SMTP`                                                   |
   | `app.email.sender.reply-to`                                         | `APP_EMAIL_SENDER_REPLY_TO`                                                                       | indirizzo Reply-To SMTP facoltativo                                                        |
   | `app.email.smtp.host` / `port`                                      | `APP_EMAIL_SMTP_HOST` / `APP_EMAIL_SMTP_PORT`                                                     | host e porta (1–65535) obbligatori in `SMTP`                                               |
   | `app.email.smtp.username` / `password`                              | `APP_EMAIL_SMTP_USERNAME` / `APP_EMAIL_SMTP_PASSWORD`                                             | obbligatori solo con `APP_EMAIL_SMTP_AUTH=true`; fornire solo tramite environment          |
   | `app.email.smtp.auth` / `start-tls`                                 | `APP_EMAIL_SMTP_AUTH` / `APP_EMAIL_SMTP_START_TLS`                                                | autenticazione e STARTTLS configurabili                                                    |
   | `app.email.smtp.connect-timeout` / `read-timeout` / `write-timeout` | `APP_EMAIL_SMTP_CONNECT_TIMEOUT` / `APP_EMAIL_SMTP_READ_TIMEOUT` / `APP_EMAIL_SMTP_WRITE_TIMEOUT` | durate positive, ad esempio `5s`                                                           |

Non sono più richieste proprietà `app.security.jwt.*` né `app.cors.allowed-origins`: non esiste JWT runtime e CORS applicativo è disabilitato. In produzione l’autenticazione browser assume topologia same-origin dietro reverse proxy. Il file `application.properties` resta escluso da Git.

Anche `app.email` è tipizzata e fail-fast. `verification-page-url` punta alla pagina frontend di verifica email (`/verify-email`, già implementata) e può includere un base path; il backend aggiunge il token codificato nel fragment `#token=...`. `DISABLED` è soltanto un default locale sicuro e non rende la configurazione pronta per produzione. `IN_MEMORY` conserva messaggi esclusivamente nel processo, non espone inbox HTTP e viene usato dal profilo `test`; non sono configurati host SMTP, credenziali o accessi di rete. `SMTP` richiede mittente valido, host, porta, tre timeout positivi e, quando `auth=true`, username e password; applica UTF-8, `mail.smtp.auth`, STARTTLS e i timeout JavaMail in millisecondi. Nessuna connessione viene eseguita durante la validazione. Le combinazioni incoerenti impediscono l'avvio e password o credenziali non sono incluse nei `toString` o nei log.

La configurazione locale validata usa `app.email.verification-page-url=http://localhost:5173/verify-email`. La pagina frontend `/verify-email` è implementata: legge e rimuove il token dal fragment, conferma via API e offre il resend neutro. Lo startup controllato è stato eseguito con email `DISABLED`, senza invii SMTP reali.

Anche la configurazione temporale è tipizzata e validata all'avvio. L'applicazione usa un unico `Clock` tecnico UTC; `ApplicationTimeProvider.nowInstant()` tronca, senza arrotondare, alla precisione canonica di sei cifre. Gli istanti persistiti e gli audit applicativi sono `Instant` su colonne `DATETIME(6)`, con `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`; `Europe/Rome` resta soltanto la zona business. Spring Data JPA valorizza `createdAt` e `updatedAt`. Le scadenze email e invito sono rispettivamente 24 e 168 ore reali e sono esposte con `Z`. Sul confine HTTP gli orari degli slot restano `OffsetDateTime` al secondo, validati contro gap, overlap e offset di `Europe/Rome`. Anche il timestamp di `ErrorResponse` è un `Instant` UTC serializzato con `Z`.

La configurazione di esempio usa `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida il contratto JPA, mentre Flyway governa la creazione e l'evoluzione delle nove tabelle runtime e dello schema Spring Session tramite `classpath:db/migration`.

Flyway è configurato con `baseline-on-migrate=false` e `clean-disabled=true` e governa lo schema di dominio insieme all'infrastruttura Spring Session. V4 prepara e verifica atomicamente la conversione delle 23 colonne temporali legacy da `Europe/Rome` a UTC; V5 trasferisce l'auditing all'applicazione; V6 aggiunge e verifica il backfill degli snapshot storici Booking; V7 ha introdotto lo schema Spring Session JDBC (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`) senza auto-init di Boot. L'elenco aggiornato delle migrazioni e i dettagli di schema restano in [docs/10-database-schema.md](docs/10-database-schema.md). Tutti gli istanti runtime usano `DATETIME(6)` e Hibernate valida il contratto con `ddl-auto=validate`.

La validazione conclusiva del 16 luglio 2026 su MySQL 8.0.44 ha prodotto il verdetto **MYSQL VALIDATION PASSED WITH WARNINGS**. Gli schemi isolati `support_trainer_audit_empty_20260716_101232` e `support_trainer_audit_legacy_20260716_101232` hanno certificato i percorsi da schema vuoto e legacy simulato. Sono rimasti presenti e non devono essere eliminati senza autorizzazione.

Successivamente è stato completato il rehearsal sul clone reale `support_trainer_rehearsal_legacy_20260716_105457`, con verdetto **DATABASE MIGRATION REHEARSAL PASSED WITH WARNINGS**. Il backup usato dal rehearsal è stato verificato prima e dopo l'importazione senza variazioni; il dump conteneva 22 `CREATE TABLE` e 9 `INSERT INTO`, senza selezione o creazione di database, trigger, routine, eventi, `LOAD DATA` o comandi esterni, ed è stato importato esclusivamente nel clone.

Il clone ripristinato coincideva con la fotografia originale V1: 22 tabelle, 181 colonne, 13 tabelle legacy/future vuote e `flyway_schema_history` assente. Flyway Maven Plugin 11.14.1 ha registrato una baseline esplicita versione 1, tipo `BASELINE`, senza rieseguire V1 e senza modifiche manuali della history; ha poi applicato le 21 migrazioni V2 → V6. La history finale contiene 22 righe, 22 successi, 0 failed e V6 finale; V2 ha checksum `-602898647` e V6 `-840301506`. Il secondo `migrate` non ha applicato operazioni e `validate` è riuscito.

V4 ha convertito 70 valori legacy `Europe/Rome` in UTC, preservando 2 null e 5 frazioni microsecondo, con digest e conteggi pre/post coincidenti. V6, non transazionale, ha completato il backfill di 5 Booking e 5 item senza stato parziale. Il clone finale conserva 22 tabelle applicative, aggiunge soltanto la history, contiene 188 colonne applicative e 198 totali; le nove tabelle runtime sono identiche allo schema pulito certificato. Hibernate `ddl-auto=validate`, l'avvio web controllato e la sessione JDBC UTC sono riusciti senza DDL Hibernate né processi residui.

Il database originale `support_trainer`, inizialmente legacy compatibile con V1 e privo di history, è stato migrato nella finestra controllata. Dopo il backup pre-migrazione verificato, Flyway ha registrato la baseline V1 e applicato esattamente 21 migrazioni da V2 a V6. Lo stato finale contiene 22 righe history tutte riuscite, V6 come ultima versione, 27 record applicativi preservati e zero record orfani. `Flyway validate`, il secondo `migrate` idempotente, Hibernate `ddl-auto=validate` e lo startup controllato sono riusciti. Restano vietati `flyway repair`, modifiche manuali della history e Flyway `clean` sugli ambienti persistenti.

Le tredici tabelle legacy relative a refresh/reset token, workout, nutrition, feedback e misurazioni non sono governate dalle migrazioni correnti e non vengono create, modificate o eliminate. Il perimetro completo è descritto nella [documentazione del database](docs/10-database-schema.md).

Le password impostate durante la registrazione hanno un massimo di 72 byte in codifica UTF-8, che può corrispondere a meno di 72 caratteri quando sono presenti caratteri Unicode. Il backend rifiuta i valori oltre soglia e non li tronca né li normalizza. Nel login, il superamento del limite restituisce lo stesso errore generico `401` delle credenziali non valide, senza rivelare l’esistenza dell’account.

## 9. Avvio del backend

Dalla cartella `backend`:

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### Linux e macOS

```bash
./mvnw spring-boot:run
```

Il server utilizza per impostazione predefinita la porta `8080`.

### Profilo locale Mailpit

Quando Mailpit è già disponibile localmente, è possibile avviare il backend senza modificare il file locale ignorato:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=mailpit"
```

Il profilo tracciato usa SMTP `localhost:1025`, mittente non reale, `auth=false`, `start-tls=false` e timeout brevi. L'interfaccia web tipica di Mailpit è `http://localhost:8025`. Mailpit non viene avviato né richiesto dalla suite di test.

Per creare il package applicativo:

```powershell
.\mvnw.cmd clean package
```

Su Linux o macOS usare `./mvnw clean package`.

Il JAR generato può essere avviato con:

```powershell
java -jar target/support_trainer-0.0.1-SNAPSHOT.jar
```

## 10. Installazione e comandi frontend

Dalla cartella `frontend`, installare le dipendenze esattamente come registrate nel lockfile:

```powershell
npm ci
```

Avviare il server di sviluppo Vite:

```powershell
npm run dev
```

Eseguire i controlli statici e di formattazione:

```powershell
npm run lint
npm run format:check
```

Eseguire i test frontend con Vitest e ambiente `jsdom`:

```powershell
npm run test
```

Creare la build frontend di produzione, comprensiva del controllo TypeScript:

```powershell
npm run build
```

L'output della build viene generato in `frontend/dist`. I comandi verificano foundation, home, auth session-based, onboarding PROFESSIONAL/verifica email e Profilo/Account/Operational Status; non implicano che le altre pagine business placeholder o i flussi pubblici di invito/registrazione CLIENT siano già integrati con le API di dominio.

### Sviluppo locale frontend + backend

Avviare il backend su `http://localhost:8080` e il frontend Vite su `http://localhost:5173`.

Il client usa path relativi `/api/v1/...` con `credentials: 'same-origin'`. In sviluppo Vite inoltra `/api` verso `http://localhost:8080`, così le richieste restano same-origin sul browser anche con processi separati.

## 11. Esecuzione dei test backend

Dalla cartella `backend`:

### Windows

```powershell
.\mvnw.cmd test
```

### Linux e macOS

```bash
./mvnw test
```

La suite include test relativi a:

- caricamento del contesto Spring;
- persistenza JPA degli utenti;
- registrazione, login e verifica email;
- inviti, validazione preventiva e controllo accessi;
- relazioni e ownership Client / Professional;
- lettura e aggiornamento del profilo;
- disponibilità e relative regole business;
- richieste di prenotazione e relative transizioni;
- sessione, CSRF, ruoli, risposte 400/401/403 e gestione uniforme degli errori HTTP 404/405/406/409/410/415/500.

### Baseline certificata

L'ultimo `clean verify` certificato ha prodotto il JAR e completato **50 suite, 312 test, 0 failure, 0 error e 1 skipped previsto**: `BookingHistoricalSnapshotMySqlIntegrationTest`, opt-in tramite `it.mysql.enabled=true`. Lo stesso test è stato eseguito separatamente contro MySQL 8.0.44 con **1 test, 0 failure, 0 error, 0 skipped e BUILD SUCCESS**.

**CI automatica attuale** ([`backend-ci.yml`](.github/workflows/backend-ci.yml)):

- backend su Ubuntu e Windows;
- Temurin JDK 21 e Maven Wrapper;
- `clean verify` + verifica del JAR;
- job dedicato MySQL 8.4 per certificare Flyway V7 / Spring Session JDBC;
- nessuna esecuzione automatica di lint, test o build frontend.

**Gate locali frontend** (non CI GitHub): `npm run lint`, `npm run format:check`, `npm run test`, `npm run build`.

Alla baseline `99fa1d4` (`feat: realizza profilo e stato operativo autenticati`), la suite frontend completa conta **444 test** su **32 file**, tutti verdi, insieme a type-check, lint, format/check e build. Sullo stesso vertical slice Profilo/Account/Operational Status è stato eseguito un Browser E2E reale CLIENT + PROFESSIONAL, certificato con minor (follow-up non bloccanti già richiamati sopra). Questi numeri e l’esito E2E sono uno snapshot della baseline, non una garanzia permanente di conteggio.

La correzione conclusiva ha reso deterministico `EmailVerificationTransactionIntegrationTest`: la precedente scadenza assoluta è stata sostituita con scadenze relative al `MutableTestClock` fornito da `EmailTestClockConfiguration`. Il codice di produzione non è cambiato e il test non dipende più da data corrente, timezone host o orologio reale.

Le risposte di errore usano il contratto `ErrorResponse`: `timestamp` UTC, `status`, `code`, `message` e `path` senza query; `fieldErrors` è una lista presente solo per `VALIDATION_ERROR`. Il client deve decidere il comportamento tramite `code`, non tramite `message`. La configurazione Java centralizzata `JacksonConfiguration` rende il parser JSON stretto: rifiuta proprietà sconosciute, contenuto trailing e chiavi duplicate. Le risposte 401 **non** espongono `WWW-Authenticate: Bearer` (non esiste Bearer JWT); le 405 preservano `Allow`; le 415 preservano i media type supportati quando Spring li fornisce. Un CSRF non valido produce `403` con codice `CSRF_VALIDATION_FAILED`. Gli errori 500 sono sanitizzati e anche `/error` restituisce lo stesso formato. Non sono ancora previsti correlation ID, request ID o header proprietari.

## 12. Profili Spring

### Profilo predefinito

Usa la configurazione locale in `src/main/resources/application.properties` e il database MySQL.

### Profilo `test`

Usa `src/test/resources/application-test.properties` con:

- database H2 in memoria;
- schema ricreato tramite `create-drop`;
- Flyway disabilitato;
- `open-in-view` disabilitato;
- Spring Session JDBC con schema H2 di test applicato esplicitamente (non auto-init);
- cookie di sessione `STSESSION` (`Secure=false`, `HttpOnly`, `SameSite=Strict`, `Path=/`);
- timeout sessione `30m`;
- Clock UTC e zona business `Europe/Rome` espliciti;
- sender email `IN_MEMORY` e pagina fittizia assoluta, senza rete o credenziali;
- logging SQL disabilitato.

Le classi di test principali attivano il profilo con `@ActiveProfiles("test")`.

Le proprietà di sessione/cookie sono definite direttamente nel profilo `test`; la suite non dipende quindi dall’`application.properties` locale, escluso da Git, da MySQL o da segreti reali. H2 resta la suite applicativa rapida, ma non certifica la sintassi DDL MySQL, i lock o l'esecuzione reale delle migrazioni: questi controlli richiedono un ambiente MySQL 8 isolato.

Non sono attualmente presenti profili Spring dedicati a sviluppo, staging o produzione. La configurazione locale usa il file ignorato; i test usano il profilo tracciato `test`; un futuro ambiente di produzione deve fornire i valori esternamente tramite environment o altra sorgente di configurazione Spring, senza versionare segreti.

### Profilo `mailpit`

Il profilo tracciato `mailpit` è un aiuto manuale locale, non un profilo di produzione: abilita `SMTP` su `localhost:1025` e usa la pagina `http://localhost:5173/verify-email`. Si attiva soltanto in modo esplicito con `--spring.profiles.active=mailpit`; non contiene username, password o indirizzi personali reali.

## 13. Documentazione disponibile

### Fonti correnti backend

- [Endpoint Map](docs/08-endpoint-map.md)
- [Security Flow](docs/09-security-flow.md)
- [Database Schema](docs/10-database-schema.md)
- [API Modules Overview](docs/07-api-modules-overview.md)
- [Functional Scope](docs/01-functional-scope.md)
- [Project Brief](docs/00-project-brief.md)

### Frontend

- [Frontend Functional Map MVP](docs/frontend/01-frontend-functional-map-mvp.md)
- [Public Home Implementation](docs/frontend/02-public-home-implementation.md)
- [Authentication Session Flow](docs/frontend/03-authentication-session-flow.md)

### Roadmap

- [Planned Endpoints Roadmap](docs/15-planned-endpoints-roadmap.md)

### Dominio e regole

- [Domain Model](docs/02-domain-model.md)
- [Entity Fields](docs/03-entity-fields.md)
- [Relationship Rules](docs/04-relationship-rules.md)
- [JPA Strategy](docs/05-jpa-strategy.md)
- [Validation Rules](docs/06-validation-rules.md)

### Storico / audit

- [Backend implementation roadmap — storico](docs/11-backend-implementation-roadmap.md)
- [Certificazione tecnica finale](docs/final-audit-mvp.md)
- [Sprint Availability](docs/16-sprint-04-availability.md)
- [Sprint Bookings](docs/17-sprint-05-bookings.md)
- [Copertura test backend MVP](docs/codex/2026-06-28-codex-auth-test-coverage.md)

## 14. Roadmap sintetica

1. altre pagine frontend business ancora placeholder (clients, professionals, availability, bookings, dashboard con dati);
2. flussi pubblici frontend ancora placeholder (validazione invito, registrazione CLIENT);
3. completare il lifecycle account (recupero/reset password, editing account, upload immagine profilo);
4. implementare le schede di allenamento;
5. implementare i piani alimentari;
6. aggiungere feedback, misurazioni e progressi;
7. completare hardening, osservabilità e preparazione tecnica al deploy, con evoluzioni schema esclusivamente forward-only.

Dettaglio endpoint futuri: [docs/15-planned-endpoints-roadmap.md](docs/15-planned-endpoints-roadmap.md).

## 15. Stato del frontend

La cartella `frontend` contiene un'applicazione React/TypeScript/Vite con:

- routing, layout, navigazione per ruolo e pagine di errore;
- home pubblica completa sulla route `/` (direzione visuale dark-tech);
- autenticazione session-based: httpClient, CSRF memory-only, AuthProvider, login, logout, guards, bootstrap `/me`;
- pagina Profilo autenticata role-aware per CLIENT e PROFESSIONAL, con Account in sola lettura e Operational Status modificabile indipendentemente dal form profilo;
- proxy Vite `/api` → `http://localhost:8080` in sviluppo;
- test con Vitest / React Testing Library; gate locali lint/format/build.

Auth foundation, login/logout, registrazione PROFESSIONAL, verifica email con resend e Profilo/Account/Operational Status sono implementati. Restano placeholder i flussi pubblici di validazione invito e registrazione CLIENT, oltre alle altre pagine business private (dashboard con dati, clients, professionals, availability, bookings). Nessun JWT/Bearer né storage di token nel client.

Riferimenti: [Authentication Session Flow](docs/frontend/03-authentication-session-flow.md), [Frontend Functional Map](docs/frontend/01-frontend-functional-map-mvp.md), [Public Home](docs/frontend/02-public-home-implementation.md), [Security Flow](docs/09-security-flow.md), [Functional Scope](docs/01-functional-scope.md).
