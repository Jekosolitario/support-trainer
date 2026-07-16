# Certificazione tecnica finale — Support Trainer Backend MVP

Data della certificazione: 15 luglio 2026
Validazione MySQL conclusiva: 16 luglio 2026
Integration branch: `remediation/backend-audit`
Commit applicativo certificato: `3cf48902b6c193c5f25740eab7e774ce26e3dcc3`

Eventuali commit successivi esclusivamente documentali non cambiano questa baseline applicativa. Il branch usato per l'audit non è un branch operativo definitivo.

## Verdetto

**READY WITH NON-BLOCKING LIMITS**

Il backend MVP è idoneo alla fase frontend e al successivo trasferimento controllato nell'originale. L'installazione pulita MySQL e il percorso legacy simulato sono certificati; l'avvio sul database originale resta subordinato al controllo dedicato e autorizzato della sua eventuale `flyway_schema_history`. Il verdetto non equivale a production readiness: restano limiti MVP esplicitamente accettati.

## Baseline verificata

| Voce | Risultato |
|---|---|
| Runtime | Java 21, Spring Boot 4.0.5 |
| Build | Maven Wrapper 3.3.4 con Apache Maven 3.9.12 |
| Verifica locale | `clean verify` riuscito |
| Test | 50 suite, 312 test, 0 failure, 0 error |
| Skipped previsto | 1: `BookingHistoricalSnapshotMySqlIntegrationTest`, opt-in con `it.mysql.enabled=true` |
| Test MySQL opt-in | 1 test, 0 failure, 0 error, 0 skipped, `BUILD SUCCESS` |
| Validazione MySQL | `MYSQL VALIDATION PASSED WITH WARNINGS` su MySQL 8.0.44 |
| Artefatto | `support_trainer-0.0.1-SNAPSHOT.jar` generato |
| CI | GitHub Actions su Ubuntu e Windows con Temurin 21 e Maven Wrapper |

La CI è definita in `.github/workflows/backend-ci.yml`: esegue `clean verify`, verifica il JAR, usa `contents: read`, cache Maven, concurrency con cancellazione delle esecuzioni precedenti e timeout di 20 minuti. Non installa Maven globalmente, non richiede MySQL locale e non usa segreti applicativi reali.

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

Le email di verifica usano un link con token nel fragment, sono inviate solo dopo il commit e non espongono token o destinatari nei log. SMTP è disponibile, ma la consegna non è durevole senza outbox o retry.

Booking restituisce `BookingSummaryResponse` nelle liste e `BookingDetailResponse` per creazione, dettaglio e transizioni. Nomi e orari sono snapshot storici; `profileImageUrl` e specializzazione sono valori correnti opzionali. Paginazione e filtri non sono ancora implementati.

## Flyway e MySQL

Flyway governa 22 migrazioni runtime: V1, V2, `V3_1`–`V3_9`, V4, `V5_1`–`V5_9` e V6. V4 converte i valori legacy da `Europe/Rome` a UTC dopo preflight; V5 trasferisce l'ownership dell'auditing all'applicazione; V6 aggiunge e verifica gli snapshot storici Booking.

Il 16 luglio 2026 la validazione conclusiva ha prodotto il verdetto **MYSQL VALIDATION PASSED WITH WARNINGS** su MySQL 8.0.44. Sono stati creati nuovi e usati esclusivamente per questa prova:

- `support_trainer_audit_empty_20260716_101232`;
- `support_trainer_audit_legacy_20260716_101232`.

Entrambi usano charset `utf8mb4` e collation `utf8mb4_0900_ai_ci`, sono rimasti presenti al termine e non devono essere eliminati senza autorizzazione. Il database originale `support_trainer` non è stato interrogato o modificato.

Il percorso empty è partito da schema vuoto e ha applicato V1 → V6. Il percorso legacy simulato ha applicato le migrazioni fino a V5.9, inserito fixture controllate dal test e applicato V6: il backfill ha popolato gli snapshot dei nomi, l'intervallo storico e `cancelled_at` a precisione microsecondi; `confirmed_at` e `rejected_at` sono rimasti null per il caso testato. I nomi delle fixture non rappresentano dati reali.

In ciascuno schema `flyway_schema_history` contiene 22 righe versionate, 22 successi, 0 failed, nessun duplicato e V6 come ultima versione. Il secondo `migrate` non ha eseguito operazioni e un secondo avvio sullo schema empty non ha aggiunto righe. I checksum osservati e coincidenti sono V2 `-602898647` e V6 `-840301506`.

Hibernate `ddl-auto=validate` è riuscito senza generare DDL. La sessione applicativa ha riportato `session.time_zone=+00:00`; `NOW(6)`, `CURRENT_TIMESTAMP(6)` e `UTC_TIMESTAMP(6)` coincidevano, con differenza di 0 microsecondi. La configurazione verificata usa `connectionTimeZone=+00:00`, `forceConnectionTimeZoneToSession=true` e `hibernate.jdbc.time_zone=UTC`.

Nello schema empty non risultano colonne temporali applicative con precisione diversa da `DATETIME(6)`, trigger, default temporali DB o clausole temporali `ON UPDATE`. La fotografia della baseline comprende 9 tabelle applicative, 1 tabella history, 85 colonne applicative, 95 colonne totali, 4 unique constraint, 11 foreign key e 1 check constraint. Le foreign key usano `ON UPDATE RESTRICT` e `ON DELETE RESTRICT`. Questi conteggi descrivono la validazione corrente e non sono requisiti rigidi per evoluzioni future.

Le colonne Booking V6 `client_display_name`, `professional_display_name`, `scheduled_start`, `scheduled_end`, `confirmed_at`, `rejected_at` e `cancelled_at` sono state verificate come coerenti tra migrazione, entity, DTO, test MySQL e test H2.

`BookingHistoricalSnapshotMySqlIntegrationTest` è stato eseguito separatamente con 1 test, 0 failure, 0 error, 0 skipped e `BUILD SUCCESS`. Il successivo `clean verify` ordinario, basato su H2, ha prodotto il JAR con 50 suite, 312 test, 0 failure, 0 error e il solo test MySQL skipped perché opt-in.

I warning non bloccanti osservati sono: MySQL 1681 sulla display width degli interi durante V1, uso di API deprecata in `AvailabilityServiceIntegrationTest`, warning Mockito/Byte Buddy sul caricamento dinamico dell'agente e un primo tentativo Maven bloccato dalla policy di rete seguito da esecuzione riuscita. Non sono stati classificati come difetti funzionali o vulnerabilità.

V2 è stata modificata storicamente. Il checksum corrente `-602898647`, verificato sui due nuovi schemi, non dimostra la compatibilità con una history reale più vecchia. Prima di qualsiasi migrazione o avvio del backend aggiornato sul database originale è obbligatorio controllare, con autorizzazione dedicata, la sua eventuale `flyway_schema_history`. Non usare `flyway repair`, baseline automatica o modifiche manuali della history; non è stato accertato se il database originale possieda o meno tale tabella.

`baseline-on-migrate=false` e Flyway `clean` resta vietato sugli ambienti persistenti.

## Limiti accettati

- nessun endpoint di refresh;
- nessuna blacklist, rotazione o revoca JWT;
- nessun outbox o retry per email;
- nessun request ID HTTP o tracing distribuito;
- nessuna paginazione Booking;
- nessun multi-slot operativo;
- nessuna lifecycle API dedicata al link Professional–Client;
- timing side-channel residuo nella registrazione neutra;
- avvio sul database originale subordinato al controllo autorizzato della sua eventuale history Flyway.

## Funzionalità differite

Restano fuori dal perimetro MVP: reset password, logout server-side, upload immagine profilo, Workout, Nutrition, Feedback, Measurements, provider email esterno, template HTML, deploy e osservabilità di produzione.

## Indicazioni per il frontend

Il frontend può integrare i 29 endpoint applicativi esistenti. Deve usare le risposte neutre `202` per le registrazioni, non dedurre l'esistenza dell'email, trattare `ErrorResponse` tramite `code`, invalidare la sessione al `401`, rispettare `403` e `404` neutri e usare gli offset restituiti per Availability e Booking. Le response Booking sono autosufficienti; non è richiesto leggere lo slot live per ricostruire lo storico.

## Avvertenze documentali

I documenti storici datati possono citare conteggi o contratti precedenti. Non sono fonte dello stato corrente: per l'implementazione usare questa certificazione, il README, l'Endpoint Map, il Security Flow e la mappa frontend aggiornata.
