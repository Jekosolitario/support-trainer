# Support Trainer

## 1. Descrizione

Support Trainer è un progetto backend per la gestione del rapporto tra professionisti del benessere e clienti. L'MVP attuale copre autenticazione, profili, inviti, collegamenti professionista-cliente, disponibilità e richieste di prenotazione.

Il repository contiene il backend Spring Boot e la documentazione funzionale e tecnica. Il frontend è pianificato, ma non è ancora implementato.

## 2. Stato attuale del progetto

Il backend MVP è implementato e validato: comprende API REST protette tramite JWT, persistenza JPA, migrazioni Flyway applicate al database MySQL locale e test automatici standard con database H2 in memoria.

Stato sintetico:

- backend Spring Boot presente;
- API per i flussi principali implementate;
- 29 endpoint applicativi documentati; `/error` resta un fallback tecnico separato;
- autenticazione e autorizzazione per ruolo presenti;
- test di integrazione per auth, inviti, access control, profili, availability, booking e Security / Common presenti;
- database applicativo MySQL migrato e validato a Flyway V6;
- frontend non ancora implementato;
- pipeline CI GitHub Actions per build, test e package del backend presente; deploy non configurato;
- progetto non ancora considerato production-ready.

## 3. Stack tecnico

### Backend

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Spring Security
- Spring Data JPA / Hibernate
- Flyway 11.14.1, gestito dal BOM Spring Boot
- Jakarta Validation
- JJWT 0.13.0
- Lombok
- script Maven Wrapper 3.3.4 con distribuzione Apache Maven 3.9.12

### Persistenza e test

- MySQL per l'ambiente applicativo
- H2 in memoria per i test
- migrazioni Flyway versionate per le nove tabelle runtime MySQL
- JUnit
- Spring Test e MockMvc
- AssertJ

### Frontend pianificato

- React
- TypeScript
- Vite

## 4. Funzionalità backend implementate

### Autenticazione e sicurezza

- registrazione professionista;
- registrazione cliente tramite codice invito valido;
- generazione del token di verifica email per professionisti e clienti;
- conferma email uniforme e idempotente tramite `POST /api/v1/auth/email-verification/confirm`;
- reinvio uniforme tramite `POST /api/v1/auth/email-verification/resend`, senza enumerazione degli account;
- richiesta di consegna della verifica email eseguita in modo sincrono soltanto dopo il commit;
- login con JWT;
- generazione di access token e refresh token;
- distinzione interna tra access token e refresh token tramite claim JWT;
- accettazione dei soli access token come Bearer sugli endpoint protetti;
- autorizzazione per ruolo `PROFESSIONAL` e `CLIENT`;
- controlli applicativi su stato account, specializzazione e proprietà delle risorse;
- gestione uniforme degli errori API.

Il refresh token viene generato durante il login, ma non è accettato come Bearer sugli endpoint protetti. Non sono ancora presenti endpoint di refresh né lifecycle completo di rinnovo, persistenza, rotazione o revoca.

Entrambi i ruoli nascono con account `PENDING_VERIFICATION` ed `emailVerified=false`, ricevono un token valido 24 ore e possono effettuare login soltanto dopo la conferma. Le due registrazioni pubbliche restituiscono sempre lo stesso `202 Accepted` neutro e non espongono l'esistenza dell'email, ruoli, identificativi o token. Per il cliente l'invito viene validato prima del controllo neutro dell'email: solo per una nuova email il link professionale viene creato e l'invito consumato nella stessa transazione; per un'email già presente l'invito resta inutilizzato. Il resend è l'unico percorso pubblico per richiedere un nuovo invio su un account esistente. Il precedente GET mutante è stato rimosso; token scaduti producono `410 Gone` e un secondo POST sul token già consumato restituisce successo soltanto se lo stato finale dell'utente è coerente.

Il reinvio accetta l'email nel body, risponde sempre `202 Accepted` con lo stesso messaggio per ogni indirizzo sintatticamente valido e crea un token solo per profili attivi ancora pending. Il cooldown è di 60 secondi dal token più recente, con reinvio consentito al boundary esatto. Quando il reinvio è consentito, i precedenti token non usati vengono invalidati tecnicamente tramite `used=true` e `usedAt`, lasciando un solo token utilizzabile da 24 ore. Token, email, stato account e tempo residuo non sono esposti. Invito e link cliente-professionista restano invariati. Dopo il commit di registrazione o reinvio, un listener applicativo costruisce il link `{verification-page-url}#token={tokenEncoded}` e lo affida a una porta indipendente dal provider. Il default locale è `DISABLED`; i test usano il sender `IN_MEMORY` senza rete. In modalità `SMTP`, l'adapter invia un messaggio MIME `text/plain` UTF-8 in italiano con subject comune, URL visibile e scadenza nella zona business. Un errore del sender è assorbito dopo il commit e non cambia le risposte `202`, ma senza outbox o retry la consegna non è garantita. La neutralizzazione non elimina un possibile side-channel di timing; l'MVP non introduce ritardi artificiali o rate limiting. I clienti già persistiti non vengono migrati.

### Profilo e account

- lettura del proprio profilo;
- lettura dei dati account;
- aggiornamento del profilo;
- aggiornamento dello stato operativo;
- distinzione tra professionista e cliente;
- specializzazioni professionali `PERSONAL_TRAINER` e `NUTRITIONIST`.

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
- endpoint completo per il refresh token;
- persistenza, rotazione e revoca dei refresh token;
- logout applicativo;
- recupero e reset della password;
- upload dell'immagine profilo;
- API dedicate alla gestione manuale dei collegamenti;
- frontend integrato con il backend;
- configurazione completa per il deploy.

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
├── docs/                    # Analisi, API, modello e roadmap
├── frontend/                # Pianificato, non ancora implementato
├── LICENSE
└── README.md
```

Il backend è organizzato per dominio nei package `auth`, `profile`, `client`, `professional`, `invite`, `availability`, `booking`, `security` e `common`.

## 7. Requisiti

- JDK 21
- MySQL
- Git
- connessione Internet al primo utilizzo del Maven Wrapper, necessaria per scaricare Maven e le dipendenze

Non è richiesta un'installazione globale di Maven. Node.js non è ancora necessario perché il frontend non è stato implementato.

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

   | Proprietà | Variabile d'ambiente | Formato |
   |---|---|---|
   | `spring.datasource.url` | `SPRING_DATASOURCE_URL` | URL JDBC MySQL con `connectionTimeZone=%2B00:00` e `forceConnectionTimeZoneToSession=true` |
   | `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | utenza database |
   | `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | password database |
   | `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | lista separata da virgole di origini esatte `http`/`https` |
   | `app.time.business-zone` | `APP_TIME_BUSINESS_ZONE` | `ZoneId` business; default `Europe/Rome` |
   | `app.time.clock-zone` | `APP_TIME_CLOCK_ZONE` | zona tecnica, deve rappresentare UTC |
   | `app.email.mode` | `APP_EMAIL_MODE` | `DISABLED` (default locale sicuro), `IN_MEMORY` oppure `SMTP` |
   | `app.email.verification-page-url` | `APP_EMAIL_VERIFICATION_PAGE_URL` | URL assoluto senza query/fragment; HTTPS per host remoti, HTTP solo loopback |
   | `app.email.sender.address` | `APP_EMAIL_SENDER_ADDRESS` | indirizzo mittente obbligatorio in `SMTP` |
   | `app.email.sender.name` | `APP_EMAIL_SENDER_NAME` | nome visualizzato obbligatorio in `SMTP` |
   | `app.email.sender.reply-to` | `APP_EMAIL_SENDER_REPLY_TO` | indirizzo Reply-To SMTP facoltativo |
   | `app.email.smtp.host` / `port` | `APP_EMAIL_SMTP_HOST` / `APP_EMAIL_SMTP_PORT` | host e porta (1–65535) obbligatori in `SMTP` |
   | `app.email.smtp.username` / `password` | `APP_EMAIL_SMTP_USERNAME` / `APP_EMAIL_SMTP_PASSWORD` | obbligatori solo con `APP_EMAIL_SMTP_AUTH=true`; fornire solo tramite environment |
   | `app.email.smtp.auth` / `start-tls` | `APP_EMAIL_SMTP_AUTH` / `APP_EMAIL_SMTP_START_TLS` | autenticazione e STARTTLS configurabili |
   | `app.email.smtp.connect-timeout` / `read-timeout` / `write-timeout` | `APP_EMAIL_SMTP_CONNECT_TIMEOUT` / `APP_EMAIL_SMTP_READ_TIMEOUT` / `APP_EMAIL_SMTP_WRITE_TIMEOUT` | durate positive, ad esempio `5s` |
   | `app.security.jwt.secret` | `APP_SECURITY_JWT_SECRET` | Base64 di almeno 32 byte casuali |
   | `app.security.jwt.expiration` | `APP_SECURITY_JWT_EXPIRATION` | durata positiva |
   | `app.security.jwt.refresh-expiration` | `APP_SECURITY_JWT_REFRESH_EXPIRATION` | durata positiva e maggiore dell'access token |

Le durate accettano millisecondi senza suffisso, per compatibilità con i valori attuali, oppure unità esplicite come `1h` e `7d`. Il secret JWT non ha default, non deve essere versionato e non va riutilizzato tra ambienti.

Gli origin CORS non ammettono wildcard, path, query string o fragment: va indicata l'origine esatta del frontend, inclusa l'eventuale porta. Spazi e duplicati vengono normalizzati; `Authorization` e `Content-Type` sono consentiti, mentre le credenziali browser restano disabilitate perché l'autenticazione usa Bearer JWT.

La configurazione JWT e CORS è tipizzata e validata all'avvio. Proprietà assenti, valori non validi, secret troppo corto o origin non sicuri impediscono l'avvio senza stampare i valori sensibili. Il file `application.properties` resta escluso da Git.

Anche `app.email` è tipizzata e fail-fast. `verification-page-url` rappresenta direttamente la futura pagina frontend e può includere un base path; il backend aggiunge il token codificato nel fragment `#token=...`. `DISABLED` è soltanto un default locale sicuro e non rende la configurazione pronta per produzione. `IN_MEMORY` conserva messaggi esclusivamente nel processo, non espone inbox HTTP e viene usato dal profilo `test`; non sono configurati host SMTP, credenziali o accessi di rete. `SMTP` richiede mittente valido, host, porta, tre timeout positivi e, quando `auth=true`, username e password; applica UTF-8, `mail.smtp.auth`, STARTTLS e i timeout JavaMail in millisecondi. Nessuna connessione viene eseguita durante la validazione. Le combinazioni incoerenti impediscono l'avvio e password o credenziali non sono incluse nei `toString` o nei log.

La configurazione locale validata usa `app.email.verification-page-url=http://localhost:5173/verify-email`. La pagina frontend prevista è `/verify-email`; il backend aggiunge il token nel fragment e il frontend deve leggerlo e rimuoverlo prima di inviare la conferma al backend. Lo startup controllato è stato eseguito con email `DISABLED`, senza invii SMTP reali.

Anche la configurazione temporale è tipizzata e validata all'avvio. L'applicazione usa un unico `Clock` tecnico UTC; `ApplicationTimeProvider.nowInstant()` tronca, senza arrotondare, alla precisione canonica di sei cifre. Gli istanti persistiti e gli audit applicativi sono `Instant` su colonne `DATETIME(6)`, con `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`; `Europe/Rome` resta soltanto la zona business. Spring Data JPA valorizza `createdAt` e `updatedAt`. Le scadenze email e invito sono rispettivamente 24 e 168 ore reali e sono esposte con `Z`. Sul confine HTTP gli orari degli slot restano `OffsetDateTime` al secondo, validati contro gap, overlap e offset di `Europe/Rome`. Anche il timestamp di `ErrorResponse` è un `Instant` UTC serializzato con `Z`.

La configurazione di esempio usa `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida il contratto JPA, mentre Flyway governa la creazione e l'evoluzione delle nove tabelle runtime tramite `classpath:db/migration`.

Flyway è configurato con `baseline-on-migrate=false` e `clean-disabled=true`. Le 22 migrazioni runtime sono V1, V2, `V3_1`–`V3_9`, V4, `V5_1`–`V5_9` e V6. V4 prepara e verifica atomicamente la conversione delle 23 colonne temporali legacy da `Europe/Rome` a UTC; V5 trasferisce l'auditing all'applicazione; V6 aggiunge e verifica il backfill degli snapshot storici Booking. Tutti gli istanti runtime usano `DATETIME(6)` e Hibernate valida il contratto con `ddl-auto=validate`.

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

## 10. Esecuzione dei test

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
- JWT, ruoli, risposte 400/401/403 e gestione uniforme degli errori HTTP 404/405/406/409/410/415/500.

### Baseline certificata

L'ultimo `clean verify` certificato ha prodotto il JAR e completato **50 suite, 312 test, 0 failure, 0 error e 1 skipped previsto**: `BookingHistoricalSnapshotMySqlIntegrationTest`, opt-in tramite `it.mysql.enabled=true`. Lo stesso test è stato eseguito separatamente contro MySQL 8.0.44 con **1 test, 0 failure, 0 error, 0 skipped e BUILD SUCCESS**. La pipeline [`backend-ci.yml`](.github/workflows/backend-ci.yml) usa il Maven Wrapper con Temurin 21 su Ubuntu e Windows, esegue `clean verify`, verifica il JAR, usa `contents: read`, concurrency e timeout di 20 minuti; non richiede MySQL locale né segreti reali.

La correzione conclusiva ha reso deterministico `EmailVerificationTransactionIntegrationTest`: la precedente scadenza assoluta è stata sostituita con scadenze relative al `MutableTestClock` fornito da `EmailTestClockConfiguration`. Il codice di produzione non è cambiato e il test non dipende più da data corrente, timezone host o orologio reale.

Le risposte di errore usano il contratto `ErrorResponse`: `timestamp` UTC, `status`, `code`, `message` e `path` senza query; `fieldErrors` è una lista presente solo per `VALIDATION_ERROR`. Il client deve decidere il comportamento tramite `code`, non tramite `message`. La configurazione Java centralizzata `JacksonConfiguration` rende il parser JSON stretto: rifiuta proprietà sconosciute, contenuto trailing e chiavi duplicate. Le risposte 401 espongono `WWW-Authenticate: Bearer`; le 405 preservano `Allow`; le 415 preservano i media type supportati quando Spring li fornisce. Gli errori 500 sono sanitizzati e anche `/error` restituisce lo stesso formato. Non sono ancora previsti correlation ID, request ID o header proprietari. I rifiuti CORS che avvengono prima del controller non costituiscono invece un contratto JSON consumabile dal browser.

## 11. Profili Spring

### Profilo predefinito

Usa la configurazione locale in `src/main/resources/application.properties` e il database MySQL.

### Profilo `test`

Usa `src/test/resources/application-test.properties` con:

- database H2 in memoria;
- schema ricreato tramite `create-drop`;
- Flyway disabilitato;
- `open-in-view` disabilitato;
- JWT con valori dedicati ai test;
- origin CORS locale dedicato ai test;
- Clock UTC e zona business `Europe/Rome` espliciti;
- sender email `IN_MEMORY` e pagina fittizia assoluta, senza rete o credenziali;
- logging SQL disabilitato.

Le classi di test principali attivano il profilo con `@ActiveProfiles("test")`.

Le proprietà JWT e `app.cors.allowed-origins` sono definite direttamente nel profilo `test`; la suite non dipende quindi dall’`application.properties` locale, escluso da Git, da MySQL o da segreti reali. H2 resta la suite applicativa rapida, ma non certifica la sintassi DDL MySQL, i lock o l'esecuzione reale delle migrazioni: questi controlli richiedono un ambiente MySQL 8 isolato.

Non sono attualmente presenti profili Spring dedicati a sviluppo, staging o produzione. La configurazione locale usa il file ignorato; i test usano il profilo tracciato `test`; un futuro ambiente di produzione deve fornire i valori esternamente tramite environment o altra sorgente di configurazione Spring, senza versionare segreti.

### Profilo `mailpit`

Il profilo tracciato `mailpit` è un aiuto manuale locale, non un profilo di produzione: abilita `SMTP` su `localhost:1025` e usa la pagina `http://localhost:5173/verify-email`. Si attiva soltanto in modo esplicito con `--spring.profiles.active=mailpit`; non contiene username, password o indirizzi personali reali.

## 12. Documentazione disponibile

La cartella `docs` contiene la documentazione funzionale e tecnica. I riferimenti principali sono:

- [Project Brief](docs/00-project-brief.md)
- [Functional Scope](docs/01-functional-scope.md)
- [Domain Model](docs/02-domain-model.md)
- [Entity Fields](docs/03-entity-fields.md)
- [Relationship Rules](docs/04-relationship-rules.md)
- [JPA Strategy](docs/05-jpa-strategy.md)
- [Validation Rules](docs/06-validation-rules.md)
- [API Modules Overview](docs/07-api-modules-overview.md)
- [Endpoint Map](docs/08-endpoint-map.md)
- [Security Flow](docs/09-security-flow.md)
- [Database Schema](docs/10-database-schema.md)
- [Backend Implementation Roadmap](docs/11-backend-implementation-roadmap.md)
- [Certificazione tecnica finale](docs/final-audit-mvp.md)
- [Sprint Availability](docs/16-sprint-04-availability.md)
- [Sprint Bookings](docs/17-sprint-05-bookings.md)
- [Copertura test backend MVP](docs/codex/2026-06-28-codex-auth-test-coverage.md)

## 13. Roadmap sintetica

1. integrare il frontend con i 29 endpoint applicativi esistenti;
2. completare il lifecycle account e il flusso refresh token;
3. implementare le schede di allenamento;
4. implementare i piani alimentari;
5. aggiungere feedback, misurazioni e progressi;
6. completare hardening, osservabilità e preparazione tecnica al deploy, mantenendo le future evoluzioni dello schema esclusivamente forward-only.

La roadmap dettagliata è disponibile in [docs/11-backend-implementation-roadmap.md](docs/11-backend-implementation-roadmap.md) e [docs/15-planned-endpoints-roadmap.md](docs/15-planned-endpoints-roadmap.md).

## 14. Frontend pianificato

La cartella `frontend` è attualmente vuota. Il frontend web è pianificato con:

- React;
- TypeScript;
- Vite;
- interfaccia responsive e mobile-first;
- comunicazione con il backend tramite API REST JSON.

Non sono ancora presenti componenti UI, configurazione npm, comandi frontend o integrazione con le API. Una futura evoluzione mobile con React Native ed Expo è solo un'ipotesi progettuale e non fa parte dell'MVP attuale.
