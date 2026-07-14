# Final Audit MVP - Support Trainer Backend

Data dell'audit: 4 luglio 2026  
Branch verificato: `audit`  
Commit di riferimento: `7df9d0c` (`Merge remote-tracking branch 'origin/prepara-final-review-mvp' into audit`)

## 1. Sintesi esecutiva

Il backend MVP di Support Trainer è chiudibile nel perimetro funzionale dichiarato.

L'audit ha verificato nel repository:

- i moduli Auth, Invite, Client / Professional, Profile, Availability e Booking;
- la configurazione stateless basata su Spring Security e JWT;
- la gestione comune degli errori applicativi, di autenticazione/autorizzazione e dei principali errori HTTP framework;
- 28 endpoint REST documentati e presenti nei controller;
- 12 classi test e 85 metodi annotati con `@Test`;
- una suite completa eseguita il 4 luglio 2026 con 85 test superati, 0 failure, 0 errori e 0 test ignorati;
- la coerenza dei principali documenti tecnici con il codice corrente.

Il verdetto riguarda il **backend MVP**, non un'applicazione completa. Il frontend non è implementato e non sono state verificate configurazioni di deploy, pipeline CI/CD o condizioni di esercizio in produzione. Il progetto non può quindi essere dichiarato production-ready o deploy-ready sulla base di questo audit.

## 2. Perimetro MVP verificato

Il perimetro chiuso comprende:

- **Auth**: registrazione professionista, registrazione cliente tramite invito, verifica email e login;
- **Invite**: generazione, elenco, validazione preventiva e consumo dell'invito nel flusso di registrazione cliente;
- **Client / Professional**: collegamento creato durante la registrazione cliente, lettura delle relazioni e controllo di ownership;
- **Profile**: lettura di profilo e account, aggiornamento del profilo e dello stato operativo;
- **Availability**: creazione, lettura e gestione degli slot, con regole temporali, di ownership, stato e sovrapposizione;
- **Booking**: creazione, consultazione, conferma, rifiuto e cancellazione delle richieste, con sincronizzazione dello stato dello slot;
- **Security / Common**: autenticazione e autorizzazione stateless, filtri JWT, risposte 401/403, eccezioni applicative e contratto comune degli errori.

`ProfessionalClientLink` è presente nel dominio e supporta i controlli di relazione, ma non costituisce un modulo API autonomo.

## 3. Stack tecnico verificato

Lo stack riscontrato nel repository è:

- Java 21;
- Spring Boot 4.0.5;
- Spring Web MVC;
- Spring Security;
- JWT con JJWT 0.13.0;
- Spring Data JPA / Hibernate;
- Jakarta Validation;
- MySQL come database applicativo;
- H2 in memoria per i test;
- Lombok;
- Maven Wrapper 3.3.4, configurato per Apache Maven 3.9.12;
- JUnit, Spring Test, MockMvc, Spring Security Test e AssertJ per i test.

La configurazione applicativa di esempio usa MySQL e `spring.jpa.hibernate.ddl-auto=none`. Il profilo `test` usa H2 e `create-drop`.

## 4. Endpoint MVP

La fonte funzionale principale è `docs/08-endpoint-map.md`. Il conteggio è stato verificato anche sui metodi annotati nei sette controller presenti: entrambe le fonti riportano **28 endpoint**.

| Area | Endpoint verificati |
|---|---:|
| Auth | 5 |
| Profile / Me | 4 |
| Clients | 2 |
| Professionals | 3 |
| Invites | 2 |
| Availability | 5 |
| Bookings | 7 |
| **Totale** | **28** |

L'endpoint `GET /api/v1/professionals/{professionalId}/availability` è conteggiato nell'area Professionals in base al controller e al path, pur appartenendo funzionalmente ad Availability.

## 5. Stato test

Nel repository sono presenti:

- **12 classi test** sotto `backend/src/test/java`;
- **85 metodi `@Test`**;
- report Surefire per tutte le 12 classi;
- profilo Spring `test` autosufficiente rispetto alla configurazione applicativa locale, con H2 e configurazione CORS dedicata.

La suite completa è stata rieseguita durante questo audit il 4 luglio 2026 con Java 21 e Maven 3.9.12:

```text
Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Comando canonico dalla directory `backend`:

```bash
./mvnw test
```

Su Windows l'equivalente è `.\mvnw.cmd test`. Durante l'audit la distribuzione Maven 3.9.12 già installata dalla configurazione del wrapper è stata invocata direttamente perché il bootstrap PowerShell locale di `mvnw.cmd` terminava prima dell'avvio di Maven; la compilazione e la suite sono state comunque eseguite integralmente sul progetto corrente.

Distribuzione dei test verificata:

| Classe test | Test | Copertura principale |
|---|---:|---|
| `AuthControllerEmailVerificationIntegrationTest` | 5 | verifica email e token di verifica |
| `AuthControllerInviteValidationIntegrationTest` | 8 | contratto pubblico e stati dell'invito |
| `AuthControllerLoginIntegrationTest` | 3 | login, credenziali, verifica email e token restituiti |
| `AuthControllerRegistrationIntegrationTest` | 4 | registrazione, email duplicata e consumo invito |
| `AvailabilityServiceIntegrationTest` | 14 | slot, ownership, sovrapposizioni, stati e interazione con booking |
| `BookingServiceIntegrationTest` | 17 | creazione, visibilità, transizioni e coerenza slot |
| `ClientProfessionalAuthorizationIntegrationTest` | 9 | ruoli, link attivi e ownership |
| `InviteControllerAuthorizationIntegrationTest` | 4 | autorizzazione e ownership degli inviti |
| `MeServiceIntegrationTest` | 9 | profili, account, aggiornamenti e stati operativi |
| `SecurityCommonIntegrationTest` | 9 | JWT, 400/401/403/404/405/415 e contratto errori |
| `SupportTrainerApplicationTests` | 1 | caricamento del contesto Spring |
| `UserPersistenceTest` | 2 | persistenza profili professionista e cliente |
| **Totale** | **85** | |

Questi test forniscono una copertura funzionale significativa dell'MVP, ma non equivalgono a una misura percentuale di code coverage: nel repository non è presente un report JaCoCo verificato.

### 5.1 Stato aggiornato dopo STEP 7B-A

Il 14 luglio 2026 `./mvnw.cmd clean verify` è stato rieseguito con successo tramite il Wrapper del repository: 31 suite, 218 test, 0 failure, 0 errori e 0 skipped, cioè 11 test in più rispetto alla baseline approvata di 207. La durata Maven è stata 51,664 secondi e la somma dei tempi Surefire 34,123 secondi.

Le suite direttamente aggiornate o introdotte per il flusso uniforme sono:

| Classe test | Test | Copertura principale |
|---|---:|---|
| `AuthControllerEmailVerificationIntegrationTest` | 13 | conferma POST, validazione body e media type, idempotenza, scadenza 410 e rimozione del GET mutante |
| `AuthControllerLoginIntegrationTest` | 5 | rifiuto degli account pending e login dopo conferma |
| `AuthControllerRegistrationIntegrationTest` | 8 | registrazione, stato pending, token da 24 ore, link e consumo invito |
| `ClientEmailVerificationIntegrationTest` | 3 | client pending, link filtrato, conferma, invito e rollback atomico |

Il flusso è ora comune a professionista e cliente: entrambe le nuove registrazioni restano pending fino a `POST /api/v1/auth/email-verification/confirm`. Il primo consumo coerente attiva l'account; la ripetizione è idempotente senza modificare `usedAt`; il token scaduto restituisce `410 Gone`. Il link cliente-professionista e il consumo dell'invito restano nella transazione di registrazione, mentre il cliente pending non è esposto dai flussi operativi. Il GET mutante non è più disponibile. Invio email reale, resend e migrazione retroattiva dei clienti esistenti restano fuori perimetro.

## 6. Pacchetti chiusi

### Auth

- **Responsabilità**: registrazione pending dei due ruoli, conferma email uniforme tramite POST, login e generazione dei token JWT dopo l'attivazione.
- **Test principali**: `AuthControllerEmailVerificationIntegrationTest`, `ClientEmailVerificationIntegrationTest`, `AuthControllerLoginIntegrationTest`, `AuthControllerRegistrationIntegrationTest`.
- **Stato finale MVP**: il GET mutante è rimosso; la conferma è idempotente sullo stato coerente e la scadenza produce 410. Invio email reale, resend, reset password, logout e lifecycle completo del refresh token restano esclusi. I clienti già persistiti non sono migrati.

### Invite

- **Responsabilità**: creazione ed elenco degli inviti, validazione pubblica, uso dell'invito nella registrazione cliente e creazione del collegamento.
- **Test principali**: `AuthControllerInviteValidationIntegrationTest`, `InviteControllerAuthorizationIntegrationTest` e scenari di registrazione cliente in `AuthControllerRegistrationIntegrationTest`.
- **Stato finale MVP**: chiuso per generazione, consultazione, validazione e consumo. Non esistono API dedicate per disattivazione o dettaglio del singolo invito.

### Client / Professional

- **Responsabilità**: lettura degli utenti collegati e dei relativi dettagli, limitata per ruolo, link attivo e ownership.
- **Test principali**: `ClientProfessionalAuthorizationIntegrationTest` e `ClientDataMinimizationIntegrationTest`.
- **Stato finale MVP**: chiuso per la consultazione delle relazioni. I professionisti ricevono un riepilogo cliente con identità minima e un dettaglio che aggiunge soltanto `primaryGoal`; dati fisici, note, stati tecnici e audit restano fuori dai DTO Clients. Non esiste un controller autonomo per la gestione manuale dei link.

### Profile

- **Responsabilità**: lettura di profilo e account dell'utente autenticato, aggiornamento dei dati consentiti e dello stato operativo.
- **Test principali**: `MeServiceIntegrationTest`.
- **Stato finale MVP**: chiuso per le operazioni esposte. Upload immagine e cambio password non sono implementati.

### Availability

- **Responsabilità**: gestione degli slot dei professionisti `PERSONAL_TRAINER` e consultazione degli slot prenotabili da parte dei clienti collegati.
- **Test principali**: `AvailabilityServiceIntegrationTest`.
- **Stato finale MVP**: chiuso per creazione, aggiornamento, blocco, sblocco e lettura, incluse le principali regole temporali e l'interazione con booking pending.

### Booking

- **Responsabilità**: ciclo delle richieste di prenotazione e sincronizzazione con gli slot Availability.
- **Test principali**: `BookingServiceIntegrationTest`.
- **Stato finale MVP**: chiuso per creazione, liste, dettaglio, conferma, rifiuto e cancellazione secondo le transizioni implementate.

### Security / Common

- **Responsabilità**: autenticazione, autorizzazione per ruolo, filtro JWT, risposte di sicurezza, eccezioni applicative e formato comune degli errori.
- **Test principali**: `SecurityCommonIntegrationTest`, con ulteriore verifica access/refresh in `AuthControllerLoginIntegrationTest`.
- **Stato finale MVP**: chiuso per il contratto corrente, inclusi i principali flussi 400, 401, 403, 404, 405 e 415.

## 7. Security / Common

La configurazione verificata è stateless: CSRF è disabilitato, non vengono create sessioni applicative e gli endpoint protetti richiedono un Bearer JWT valido.

Il modello dei token distingue:

- access token con claim interno `token_type = access`;
- refresh token con claim interno `token_type = refresh`.

Solo l'access token è accettato dal filtro JWT come Bearer. Il refresh token viene generato e restituito al login, ma non esistono endpoint di refresh né persistenza, rotazione, rinnovo o revoca; il lifecycle refresh è quindi incompleto e fuori dal perimetro chiuso.

La separazione delle risposte di sicurezza è:

- **401 Unauthorized** per autenticazione assente o non valida, token scaduto/alterato, refresh token usato come Bearer o utente del token non trovato;
- **403 Forbidden** per utente autenticato privo dell'autorità richiesta.

`ErrorResponse` espone timestamp, status, tipo di errore, `errorCode`, messaggio ed eventuali errori di validazione. `AppException` trasporta stato HTTP, codice applicativo e messaggio. `GlobalExceptionHandler` gestisce le eccezioni applicative e i principali errori framework verificati:

- body malformato e validazione DTO;
- parametro obbligatorio mancante;
- parametro di path di tipo errato;
- risorsa HTTP inesistente;
- metodo HTTP non supportato;
- media type non supportato;
- errore interno inatteso con messaggio non sensibile.

I controlli di ruolo sono definiti in `SecurityConfig`; ownership, stato del profilo, specializzazione e coerenza delle risorse sono ulteriormente verificati nel service layer.

## 8. Documentazione aggiornata

I seguenti documenti risultano presenti e coerenti con il perimetro verificato:

- `README.md`: stato del backend, stack, test, configurazione e limiti;
- `docs/07-api-modules-overview.md`: moduli implementati, componenti di dominio e moduli futuri;
- `docs/08-endpoint-map.md`: mappa dei 28 endpoint realmente implementati;
- `docs/09-security-flow.md`: flussi di autenticazione, autorizzazione, token ed errori;
- `docs/codex/2026-06-28-codex-auth-test-coverage.md`: cronologia e copertura dei test di consolidamento;
- `docs/14-sprint-03-profile-clients-professionals-read.md`: Profile e letture Client / Professional;
- `docs/16-sprint-04-availability.md`: stato consolidato di Availability;
- `docs/17-sprint-05-bookings.md`: stato consolidato di Booking;
- `docs/final-audit-mvp.md`: fotografia finale del backend MVP prodotta da questo audit.

I documenti di roadmap restano riferimenti progettuali e non devono essere interpretati come prova di funzionalità già implementate.

## 9. Esclusioni esplicite dal MVP

Non risultano implementati nel repository corrente:

- frontend React, TypeScript e Vite; la directory `frontend/` è vuota;
- applicazione React Native / Expo, indicata soltanto come possibile evoluzione futura;
- Workout;
- Nutrition;
- Feedback;
- Measurements;
- recupero e reset password;
- endpoint di refresh e lifecycle completo dei refresh token;
- logout applicativo;
- revoca o rotazione dei token;
- upload dell'immagine profilo;
- migrazioni automatiche del database con Flyway o Liquibase;
- pipeline CI/CD e configurazione completa di deploy.

La presenza del campo `profileImageUrl`, delle entità di supporto o di documentazione futura non equivale all'implementazione delle relative feature.

## 10. Lacune residue non bloccanti

Le seguenti lacune non bloccano la chiusura del backend MVP, ma restano rilevanti per una fase successiva:

- H2 nei test non riproduce integralmente semantica, dialetto, locking e comportamento di MySQL;
- i lock applicativi sono presenti nei flussi critici, ma i test di concorrenza attuali non equivalgono a prove su un database e un carico di produzione reali;
- hardening JWT futuro: issuer, audience, rotazione delle chiavi e gestione completa del refresh token;
- assenza di rate limiting;
- assenza di logging strutturato e osservabilità operativa completa;
- assenza di migrazioni DB versionate, mentre la configurazione applicativa usa `ddl-auto=none`;
- matrici HTTP, casi limite temporali, stati anomali e input malformati ancora ampliabili per singolo modulo;
- assenza del frontend, coerente con il perimetro di questo audit backend;
- nessuna verifica di deploy, CI/CD, gestione dei secret, backup, monitoraggio o comportamento sotto carico.

Questi punti impediscono di estendere il verdetto a production readiness, ma non invalidano i flussi MVP verificati.

## 11. Raccomandazione finale

**Backend MVP chiudibile: sì.**

Il repository può passare alla fase frontend e alla documentazione/configurazione di deploy, mantenendo chiaro che tali fasi non sono ancora completate. L'integrazione frontend dovrebbe partire dai 28 endpoint e dai contratti di errore già documentati, senza riaprire i moduli chiusi in assenza di bug verificati o requisiti nuovi.

Non è raccomandato introdurre ora Workout, Nutrition, Feedback, Measurements o ampliare il lifecycle account/token come parte della chiusura corrente: sono evoluzioni distinte e devono essere pianificate separatamente.

Prossimi passi consigliati:

1. implementare il frontend web React + TypeScript + Vite contro i contratti API verificati;
2. preparare una configurazione di integrazione MySQL e testare i flussi critici sul database applicativo reale;
3. introdurre migrazioni DB versionate prima di automatizzare il deploy;
4. definire ambienti, gestione dei secret, logging, monitoraggio e pipeline CI/CD;
5. pianificare in fasi separate hardening JWT, lifecycle refresh/logout e moduli post-MVP.

Il verdetto finale è quindi: **backend MVP funzionalmente chiudibile e pronto per il passaggio alla fase frontend; non ancora verificato come deploy-ready o production-ready**.
