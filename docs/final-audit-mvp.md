# Certificazione tecnica finale — Support Trainer Backend MVP

Data della certificazione conclusiva: 16 luglio 2026
Branch di revisione: `remediation/backend-audit`
Repository originale: branch `main`
Commit finale certificato: `b94936bf535f83fd012ac8490ec9d9792ed6613b`
Commit finale: `test: rende deterministica la scadenza dell'invito nel test transazionale`

Il trasferimento dalla copia alla repository originale ha preservato integralmente 37 commit lineari, senza merge commit. Al termine HEAD e `origin/main` coincidono con il commit certificato e il working tree dell'originale è pulito.

## Verdetto

**BACKEND MVP VALIDATED — READY FOR FRONTEND**

Il backend MVP è trasferito nella repository originale, validato e idoneo all'avvio della fase frontend. Build, suite standard H2, test MySQL opt-in, schema pulito, percorso legacy, rehearsal e migrazione controllata del database originale sono certificati. Il verdetto non equivale a production readiness: restano limiti MVP esplicitamente accettati.

## Baseline verificata

| Voce | Risultato |
|---|---|
| Runtime | Java 21, Spring Boot 4.0.5 |
| Build | Maven Wrapper 3.3.4 con Apache Maven 3.9.12 |
| Verifica locale | `clean verify`: `BUILD SUCCESS` |
| Suite standard | H2, 50 suite, 312 test, 0 failure, 0 error |
| Skipped previsto | 1: `BookingHistoricalSnapshotMySqlIntegrationTest`, opt-in con `it.mysql.enabled=true` |
| Test MySQL opt-in | Non eseguito nella suite standard; esecuzione separata: 1 test, 0 failure, 0 error, 0 skipped |
| Validazione MySQL | `MYSQL VALIDATION PASSED WITH WARNINGS` su MySQL 8.0.44 |
| Rehearsal database legacy | `DATABASE MIGRATION REHEARSAL PASSED WITH WARNINGS` |
| Database originale | Baseline V1, 21 migrazioni V2 → V6, 22 righe history tutte riuscite |
| Artefatto | `support_trainer-0.0.1-SNAPSHOT.jar` generato |
| CI | GitHub Actions su Ubuntu e Windows con Temurin 21 e Maven Wrapper |

La CI è definita in `.github/workflows/backend-ci.yml`: esegue `clean verify`, verifica il JAR, usa `contents: read`, cache Maven, concurrency con cancellazione delle esecuzioni precedenti e timeout di 20 minuti. Non installa Maven globalmente, non richiede MySQL locale e non usa segreti applicativi reali.

## Trasferimento Git e correzione temporale conclusiva

La repository originale usa `main` con HEAD e `origin/main` al commit `b94936bf535f83fd012ac8490ec9d9792ed6613b`. La sequenza importata è composta da 37 commit lineari e non contiene merge commit.

Durante la prima validazione nell'originale, `EmailVerificationTransactionIntegrationTest` è fallito perché una fixture usava una scadenza assoluta ormai trascorsa. La produzione si è comportata correttamente considerando l'invito scaduto. Il test è stato corretto senza modificare codice applicativo: importa `EmailTestClockConfiguration`, usa il relativo `MutableTestClock`, lo reimposta su `INITIAL_INSTANT` e calcola le scadenze tramite durate esplicite. Il test non dipende più dalla data corrente, dalla timezone host o dall'orologio reale.

## Perimetro API corrente

Il backend espone **29 endpoint applicativi**. `/error` è un fallback tecnico del framework e non entra nel conteggio funzionale.

| Area | Endpoint |
|---|---:|
| Auth | 6 |
| Me | 4 |
| Client | 2 |
| Professional | 3 |
| Invite | 2 |
| Availability | 5 |
| Booking | 7 |
| **Totale applicativo** | **29** |

La fonte operativa dei path è [Endpoint Map](08-endpoint-map.md); gli endpoint pianificati restano separati in [Planned Endpoints Roadmap](15-planned-endpoints-roadmap.md).

## Chiuso

- build, Maven Wrapper Windows/Linux e configurazioni tipizzate con validazione fail-fast;
- CI Linux e Windows;
- Flyway per le nove tabelle runtime, con Hibernate `ddl-auto=validate`;
- persistenza UTC, `Clock` applicativo, `DATETIME(6)` e auditing applicativo;
- sicurezza stateless JWT, CORS esplicito, password BCrypt entro il limite UTF-8 e contratto HTTP uniforme;
- registrazione Professional e Client neutra rispetto all'esistenza dell'email, con rollback atomico;
- verifica email, resend, sender `DISABLED`/`IN_MEMORY`/SMTP e consegna `AFTER_COMMIT`;
- privacy dei DTO, ownership scoped e anti-enumerazione per Client, Professional, Availability e Booking;
- Booking Summary/Detail autosufficienti e snapshot storico persistito;
- test applicativi completi e contratto degli errori.

## Sicurezza e contratti HTTP

La sicurezza è stateless. I ruoli sono `CLIENT` e `PROFESSIONAL`; specializzazione, stato account, verifica email, profilo e ownership sono controllati anche nel service layer. I dettagli non accessibili restituiscono `404` neutro dove necessario per evitare enumerazione; ruolo o stato non idoneo producono `403`.

Le registrazioni pubbliche restituiscono sempre `202 Accepted` neutro. La collisione dell'email è riconosciuta prima tramite il constraint Hibernate strutturato `uk_users_email`; il fallback testuale è usato solo se nessuna causa espone un constraint name. Non viene esposto `EMAIL_ALREADY_REGISTERED`.

`ErrorResponse` usa `timestamp`, `status`, `code`, `message`, `path` e `fieldErrors`. Il client deve guidare la logica con `code`, non con il testo. Le risposte `401` richiedono invalidazione della sessione frontend; `403` riguarda ruolo o stato; `404` include risorse inesistenti o non accessibili; `409` conflitti; `410` token email scaduto. Gli errori 5xx sono sanitizzati e `/error` usa lo stesso formato tecnico.

## Tempo, email e Booking

Gli istanti persistiti sono `Instant` UTC su `DATETIME(6)`. Gli orari civili Availability e gli snapshot Booking sono esposti come `OffsetDateTime` con offset coerente con `Europe/Rome`; gap, overlap, offset incoerenti e frazioni non nulle sono rifiutati.

Le email di verifica usano un link con token nel fragment, sono inviate solo dopo il commit e non espongono token o destinatari nei log. La pagina locale validata è `http://localhost:5173/verify-email`, coerente con la rotta frontend `/verify-email`; il frontend legge e rimuove il fragment prima di inviare il token al backend. Lo startup conclusivo ha usato email mode `DISABLED` e non ha inviato email SMTP reali. SMTP è disponibile, ma la consegna non è durevole senza outbox o retry.

Booking restituisce `BookingSummaryResponse` nelle liste e `BookingDetailResponse` per creazione, dettaglio e transizioni. Nomi e orari sono snapshot storici; `profileImageUrl` e specializzazione sono valori correnti opzionali. Paginazione e filtri non sono ancora implementati.

## Flyway e MySQL

Flyway governa 22 migrazioni runtime: V1, V2, `V3_1`–`V3_9`, V4, `V5_1`–`V5_9` e V6. V4 converte i valori legacy da `Europe/Rome` a UTC dopo preflight; V5 trasferisce l'ownership dell'auditing all'applicazione; V6 aggiunge e verifica gli snapshot storici Booking.

### Validazione isolata

Il 16 luglio 2026 la validazione conclusiva ha prodotto il verdetto **MYSQL VALIDATION PASSED WITH WARNINGS** su MySQL 8.0.44. Sono stati creati nuovi e usati esclusivamente per questa prova:

- `support_trainer_audit_empty_20260716_101232`;
- `support_trainer_audit_legacy_20260716_101232`.

Entrambi usano charset `utf8mb4` e collation `utf8mb4_0900_ai_ci`, sono rimasti presenti al termine e non devono essere eliminati senza autorizzazione. Durante questa prima validazione il database originale `support_trainer` non è stato interrogato o modificato.

Il percorso empty è partito da schema vuoto e ha applicato V1 → V6. Il percorso legacy simulato ha applicato le migrazioni fino a V5.9, inserito fixture controllate dal test e applicato V6: il backfill ha popolato gli snapshot dei nomi, l'intervallo storico e `cancelled_at` a precisione microsecondi; `confirmed_at` e `rejected_at` sono rimasti null per il caso testato. I nomi delle fixture non rappresentano dati reali.

In ciascuno schema `flyway_schema_history` contiene 22 righe versionate, 22 successi, 0 failed, nessun duplicato e V6 come ultima versione. Il secondo `migrate` non ha eseguito operazioni e un secondo avvio sullo schema empty non ha aggiunto righe. I checksum osservati e coincidenti sono V2 `-602898647` e V6 `-840301506`.

Hibernate `ddl-auto=validate` è riuscito senza generare DDL. La sessione applicativa ha riportato `session.time_zone=+00:00`; `NOW(6)`, `CURRENT_TIMESTAMP(6)` e `UTC_TIMESTAMP(6)` coincidevano, con differenza di 0 microsecondi. La configurazione verificata usa `connectionTimeZone=+00:00`, `forceConnectionTimeZoneToSession=true` e `hibernate.jdbc.time_zone=UTC`.

Nello schema empty non risultano colonne temporali applicative con precisione diversa da `DATETIME(6)`, trigger, default temporali DB o clausole temporali `ON UPDATE`. La fotografia della baseline comprende 9 tabelle applicative, 1 tabella history, 85 colonne applicative, 95 colonne totali, 4 unique constraint, 11 foreign key e 1 check constraint. Le foreign key usano `ON UPDATE RESTRICT` e `ON DELETE RESTRICT`. Questi conteggi descrivono la validazione corrente e non sono requisiti rigidi per evoluzioni future.

Le colonne Booking V6 `client_display_name`, `professional_display_name`, `scheduled_start`, `scheduled_end`, `confirmed_at`, `rejected_at` e `cancelled_at` sono state verificate come coerenti tra migrazione, entity, DTO, test MySQL e test H2.

`BookingHistoricalSnapshotMySqlIntegrationTest` è stato eseguito separatamente con 1 test, 0 failure, 0 error, 0 skipped e `BUILD SUCCESS`. Il successivo `clean verify` ordinario, basato su H2, ha prodotto il JAR con 50 suite, 312 test, 0 failure, 0 error e il solo test MySQL skipped perché opt-in.

### Restore e rehearsal del database originale

Il backup usato per il rehearsal è `C:\Users\96and\AppData\Local\Temp\support_trainer_backup_20260713_151823\support_trainer_backup_20260713_151823.sql`, pari a 30.731 byte e SHA-256 `763763FD276A8D972CD520525E187CBC454455DB22C4F2BC986221A58F5F3EE7`. Dimensione e hash sono stati verificati prima e dopo l'importazione e sono rimasti invariati. L'analisi strutturale ha rilevato 22 `CREATE TABLE` e 9 `INSERT INTO`, senza `CREATE DATABASE`, `DROP DATABASE`, `USE`, trigger, routine, eventi, `LOAD DATA` o comandi esterni. Nessun valore degli `INSERT` è stato riportato.

Il dump è stato importato esclusivamente in `support_trainer_rehearsal_legacy_20260716_105457`. Lo stato iniziale del clone coincideva con l'originale: MySQL 8.0.44, `utf8mb4`, `utf8mb4_0900_ai_ci`, 22 tabelle, 181 colonne, zero viste, trigger, routine ed eventi, history assente, struttura materiale V1 e conteggi coincidenti. Il clone è rimasto presente ed è ora migrato a V6.

La baseline è stata creata tramite Flyway Maven Plugin 11.14.1, senza modificare `pom.xml`, con versione 1, descrizione `Legacy schema V1 verified`, `baselineOnMigrate=false` e `cleanDisabled=true`. La prima riga della history ha `installed_rank=1`, versione 1, tipo `BASELINE`, checksum nullo e successo. V1 non è stata rieseguita: lo schema era già materialmente presente nel backup. Non è stata eseguita alcuna `INSERT` manuale nella history.

Sono state applicate 21 migrazioni effettive: V2, V3.1–V3.9, V4, V5.1–V5.9 e V6. La durata osservata, non trasferibile come SLA ad altri ambienti o volumi, è stata 3.321 ms: V2 403 ms, V3.1–V3.9 1.370 ms, V4 79 ms, V5.1–V5.9 478 ms e V6 991 ms. La history finale contiene 22 righe, una baseline e 21 migrazioni, 22 successi, 0 failed, 22 versioni distinte, nessun duplicato e V6 finale. I checksum verificati sono V2 `-602898647` e V6 `-840301506`.

V2 ha ampliato `primary_goal` a `VARCHAR(255)`, reso `booking_request_items.updated_at` `DATETIME(6) NOT NULL` e aggiunto i quattro indici runtime previsti, senza modificare conteggi o vincoli. Le V3 hanno convertito 22 colonne da `DATETIME(0)` a `DATETIME(6)`; una colonna era già a precisione 6. Dopo V6 le colonne temporali runtime sono 28, tutte a precisione 6.

Il preflight V4 ha controllato 9 tabelle, 27 righe e 72 celle temporali: 70 valori non nulli e 2 null, nessun gap o overlap DST, digest atteso e finale coincidenti, nessuna riga persa e nessuna perdita di microsecondi. Cinque valori frazionari sono stati preservati; 13 valori hanno applicato lo scarto Europe/Rome di un'ora e 57 quello di due ore.

V5 ha lasciato zero default temporali DB, zero clausole `ON UPDATE` e zero trigger, trasferendo l'auditing all'applicazione con nullability coerente. V6, non transazionale, ha aggiunto le sette colonne Booking e completato integralmente il backfill: 5 Booking, 5 item, 5 snapshot Client, 5 snapshot Professional e 5 intervalli storici. Le timeline risultano 3 `CANCELLED` con solo `cancelled_at`, 1 `CONFIRMED` con solo `confirmed_at` e 1 `REJECTED` con solo `rejected_at`; zero timestamp non pertinenti, orphan, duplicati request/slot o stato parziale.

Il clone finale contiene 22 tabelle applicative preservate, incluse le 13 legacy/future ancora vuote, più `flyway_schema_history`: 23 tabelle, 188 colonne applicative, 198 totali e 22 righe history. Le nove tabelle runtime coincidono con `support_trainer_audit_empty_20260716_101232`: 85 colonne, 41 indici, 25 constraint, 11 foreign key e un check, con zero differenze.

Il secondo `migrate` non ha applicato operazioni né aggiunto righe. `Flyway validate` è riuscito senza pending, failed o risorse incoerenti; Flyway ha indicato 23 elementi validati perché considera anche V1 rispetto alla baseline, mentre la history contiene correttamente 22 righe. Hibernate `ddl-auto=validate` ha inizializzato l'`EntityManagerFactory` senza `CREATE`, `ALTER` o `DROP`; Flyway non ha applicato nulla durante l'avvio web su porta effimera. Email era `DISABLED`, lo shutdown è stato controllato e non sono rimasti processi Java.

La connessione applicativa usava `session.time_zone=+00:00`, `connectionTimeZone=+00:00`, `forceConnectionTimeZoneToSession=true` e `hibernate.jdbc.time_zone=UTC`; `NOW(6)`, `CURRENT_TIMESTAMP(6)` e `UTC_TIMESTAMP(6)` coincidevano con differenza zero microsecondi. Il successivo `clean verify` H2 ha prodotto il JAR con 50 suite, 312 test, 0 failure, 0 error e il solo test MySQL opt-in skipped.

I warning non bloccanti del rehearsal sono un'API deprecata in `AvailabilityServiceIntegrationTest`, il self-attach Mockito/Byte Buddy, un primo tentativo Maven limitato dalla policy di rete e una prima osservazione incompleta dello shutdown, seguita da arresto verificato tramite Spring Admin MBean. Non sono difetti applicativi.

### Migrazione controllata del database originale

Prima della migrazione è stato creato il backup definitivo:

`C:\Users\96and\AppData\Local\Temp\support_trainer_backup_pre_migration_20260716_225159\support_trainer_backup_pre_migration_20260716_225159.sql`

- dimensione: 27.171 byte;
- SHA-256: `529E691F35B2AA66E86065BC0B0C4D2752F1362CACFC67260209FB2A6E7E8E49`;
- 22 `CREATE TABLE`, 9 `INSERT INTO` e 27 tuple complessive;
- nessun dato personale del dump è riportato in questa certificazione.

Il database reale `support_trainer` partiva dallo schema legacy compatibile con V1: 22 tabelle, 181 colonne, 27 record applicativi e `flyway_schema_history` assente. Flyway ha registrato una baseline esplicita versione 1, senza rieseguire V1, e ha poi applicato esattamente 21 migrazioni: V2, V3.1–V3.9, V4, V5.1–V5.9 e V6.

La history finale contiene 22 righe, una baseline e 21 migrazioni, tutte con `success=true`, senza rank o versioni duplicate e con V6 finale. I checksum sono V2 `-602898647` e V6 `-840301506`. `Flyway validate` è riuscito; un secondo `migrate` ha applicato zero operazioni.

V4 ha convertito i datetime legacy `Europe/Rome` in UTC su 9 tabelle, 27 righe e 23 colonne: 70 valori convertiti, 2 null e 5 valori frazionari preservati. V6 ha valorizzato 5 snapshot Client, 5 snapshot Professional e 5 intervalli storici; non risultano campi obbligatori null o timeline incoerenti.

Lo stato conclusivo contiene 23 tabelle totali, 198 colonne totali e gli stessi 27 record applicativi. Non risultano perdite di dati, record orfani o duplicazioni inattese. Hibernate `ddl-auto=validate`, la sessione JDBC UTC e lo startup su porta effimera sono riusciti; Flyway non ha applicato migrazioni durante lo startup e il processo è stato arrestato con shutdown grazioso.

Il primo tentativo di startup post-migrazione ha evidenziato che la configurazione locale ignorata, precedente all'introduzione di `app.email`, non conteneva `app.email.verification-page-url`. Lo schema era già stato validato correttamente da Flyway e Hibernate. La configurazione locale è stata poi allineata alla URL versionata `http://localhost:5173/verify-email`; test mirati e startup senza override della URL sono riusciti. Non sono stati modificati file versionati né esposti segreti.

## Limiti accettati

- nessun endpoint di refresh;
- nessuna blacklist, rotazione o revoca JWT;
- nessun outbox o retry per email;
- nessun request ID HTTP o tracing distribuito;
- nessuna paginazione Booking;
- nessun multi-slot operativo;
- nessuna lifecycle API dedicata al link Professional–Client;
- timing side-channel residuo nella registrazione neutra;
- configurazione locale e backup restano esterni a Git e richiedono gestione operativa sicura;
- le future evoluzioni dello schema devono essere forward-only, senza `repair`, modifiche manuali della history o `clean`.

## Funzionalità differite

Restano fuori dal perimetro MVP: reset password, logout server-side, upload immagine profilo, Workout, Nutrition, Feedback, Measurements, provider email esterno, template HTML, deploy e osservabilità di produzione.

## Indicazioni per il frontend

Il frontend può integrare i 29 endpoint applicativi esistenti. Deve usare le risposte neutre `202` per le registrazioni, non dedurre l'esistenza dell'email, trattare `ErrorResponse` tramite `code`, invalidare la sessione al `401`, rispettare `403` e `404` neutri e usare gli offset restituiti per Availability e Booking. Le response Booking sono autosufficienti; non è richiesto leggere lo slot live per ricostruire lo storico.

## Avvertenze documentali

I documenti storici datati possono citare conteggi o contratti precedenti. Non sono fonte dello stato corrente: per l'implementazione usare questa certificazione, il README, l'Endpoint Map, il Security Flow e la mappa frontend aggiornata.
