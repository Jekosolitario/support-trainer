# Support Trainer

## 1. Descrizione

Support Trainer è un progetto backend per la gestione del rapporto tra professionisti del benessere e clienti. L'MVP attuale copre autenticazione, profili, inviti, collegamenti professionista-cliente, disponibilità e richieste di prenotazione.

Il repository contiene il backend Spring Boot e la documentazione funzionale e tecnica. Il frontend è pianificato, ma non è ancora implementato.

## 2. Stato attuale del progetto

Il backend MVP è implementato e comprende API REST protette tramite JWT, persistenza JPA e test automatici con database H2 in memoria.

Stato sintetico:

- backend Spring Boot presente;
- API per i flussi principali implementate;
- autenticazione e autorizzazione per ruolo presenti;
- test di integrazione per auth, inviti, access control, profili, availability, booking e Security / Common presenti;
- database applicativo previsto: MySQL;
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
- generazione e verifica del token di verifica email;
- login con JWT;
- generazione di access token e refresh token;
- distinzione interna tra access token e refresh token tramite claim JWT;
- accettazione dei soli access token come Bearer sugli endpoint protetti;
- autorizzazione per ruolo `PROFESSIONAL` e `CLIENT`;
- controlli applicativi su stato account, specializzazione e proprietà delle risorse;
- gestione uniforme degli errori API.

Il refresh token viene generato durante il login, ma non è accettato come Bearer sugli endpoint protetti. Non sono ancora presenti endpoint di refresh né lifecycle completo di rinnovo, persistenza, rotazione o revoca.

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

### Bookings

- creazione di una richiesta di prenotazione;
- consultazione delle richieste lato cliente e professionista;
- dettaglio della prenotazione con controllo di accesso;
- conferma e rifiuto da parte del professionista;
- cancellazione secondo le transizioni consentite;
- aggiornamento coerente dello stato dello slot.

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
   | `spring.datasource.url` | `SPRING_DATASOURCE_URL` | URL JDBC MySQL |
   | `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | utenza database |
   | `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | password database |
   | `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | lista separata da virgole di origini esatte `http`/`https` |
   | `app.security.jwt.secret` | `APP_SECURITY_JWT_SECRET` | Base64 di almeno 32 byte casuali |
   | `app.security.jwt.expiration` | `APP_SECURITY_JWT_EXPIRATION` | durata positiva |
   | `app.security.jwt.refresh-expiration` | `APP_SECURITY_JWT_REFRESH_EXPIRATION` | durata positiva e maggiore dell'access token |

Le durate accettano millisecondi senza suffisso, per compatibilità con i valori attuali, oppure unità esplicite come `1h` e `7d`. Il secret JWT non ha default, non deve essere versionato e non va riutilizzato tra ambienti.

Gli origin CORS non ammettono wildcard, path, query string o fragment: va indicata l'origine esatta del frontend, inclusa l'eventuale porta. Spazi e duplicati vengono normalizzati; `Authorization` e `Content-Type` sono consentiti, mentre le credenziali browser restano disabilitate perché l'autenticazione usa Bearer JWT.

La configurazione JWT e CORS è tipizzata e validata all'avvio. Proprietà assenti, valori non validi, secret troppo corto o origin non sicuri impediscono l'avvio senza stampare i valori sensibili. Il file `application.properties` resta escluso da Git.

La configurazione di esempio usa `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida il contratto JPA, mentre Flyway governa la creazione e l'evoluzione delle nove tabelle runtime tramite `classpath:db/migration`.

Flyway è configurato con `baseline-on-migrate=false` e `clean-disabled=true`. Su un database nuovo e vuoto applica in ordine la V1 legacy-compatible e la V2 di convergenza. Un database esistente non deve essere avviato direttamente con le migrazioni abilitate: prima sono obbligatori backup, clone di verifica, confronto dello schema e baseline manuale esplicitamente approvata. Il comando Flyway `clean` è vietato sugli ambienti persistenti.

Le tredici tabelle legacy relative a refresh/reset token, workout, nutrition, feedback e misurazioni non sono governate dalle migrazioni correnti e non vengono create, modificate o eliminate. Il perimetro completo è descritto nella [documentazione del database](docs/10-database-schema.md).

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
- JWT, ruoli, risposte 400/401/403 e gestione degli errori HTTP 404/405/415.

L’ultima suite completa verificata contiene 98 test, senza failure, errori o test ignorati: 13 in più rispetto alla baseline precedente di 85 grazie ai test fail-fast sulle proprietà e ai test CORS.

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
- logging SQL disabilitato.

Le classi di test principali attivano il profilo con `@ActiveProfiles("test")`.

Le proprietà JWT e `app.cors.allowed-origins` sono definite direttamente nel profilo `test`; la suite non dipende quindi dall’`application.properties` locale, escluso da Git, da MySQL o da segreti reali. H2 resta la suite applicativa rapida, ma non certifica la sintassi DDL MySQL, i lock o l'esecuzione reale delle migrazioni: questi controlli richiedono un ambiente MySQL 8 isolato.

Non sono attualmente presenti profili Spring dedicati a sviluppo, staging o produzione. La configurazione locale usa il file ignorato; i test usano il profilo tracciato `test`; un futuro ambiente di produzione deve fornire i valori esternamente tramite environment o altra sorgente di configurazione Spring, senza versionare segreti.

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
- [Sprint Availability](docs/16-sprint-04-availability.md)
- [Sprint Bookings](docs/17-sprint-05-bookings.md)
- [Copertura test backend MVP](docs/codex/2026-06-28-codex-auth-test-coverage.md)

## 13. Roadmap sintetica

1. consolidare test e configurazione riproducibile;
2. completare il lifecycle account e il flusso refresh token;
3. integrare il frontend con le API esistenti;
4. implementare le schede di allenamento;
5. implementare i piani alimentari;
6. aggiungere feedback, misurazioni e progressi;
7. completare la preparazione tecnica al deploy.

La roadmap dettagliata è disponibile in [docs/11-backend-implementation-roadmap.md](docs/11-backend-implementation-roadmap.md) e [docs/15-planned-endpoints-roadmap.md](docs/15-planned-endpoints-roadmap.md).

## 14. Frontend pianificato

La cartella `frontend` è attualmente vuota. Il frontend web è pianificato con:

- React;
- TypeScript;
- Vite;
- interfaccia responsive e mobile-first;
- comunicazione con il backend tramite API REST JSON.

Non sono ancora presenti componenti UI, configurazione npm, comandi frontend o integrazione con le API. Una futura evoluzione mobile con React Native ed Expo è solo un'ipotesi progettuale e non fa parte dell'MVP attuale.
