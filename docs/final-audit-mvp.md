# Certificazione tecnica finale — Support Trainer Backend MVP

Data della certificazione: 15 luglio 2026
Integration branch: `remediation/backend-audit`
Commit applicativo certificato: `3cf48902b6c193c5f25740eab7e774ce26e3dcc3`

Eventuali commit successivi esclusivamente documentali non cambiano questa baseline applicativa. Il branch usato per l'audit non è un branch operativo definitivo.

## Verdetto

**READY WITH NON-BLOCKING LIMITS**

Il backend MVP è idoneo alla fase frontend e al successivo trasferimento controllato nell'originale. Il verdetto non equivale a production readiness: restano azioni obbligatorie prima di una migrazione su un database reale e limiti MVP esplicitamente accettati.

## Baseline verificata

| Voce | Risultato |
|---|---|
| Runtime | Java 21, Spring Boot 4.0.5 |
| Build | Maven Wrapper 3.3.4 con Apache Maven 3.9.12 |
| Verifica locale | `clean verify` riuscito |
| Test | 50 suite, 312 test, 0 failure, 0 error |
| Skipped previsto | 1: `BookingHistoricalSnapshotMySqlIntegrationTest`, opt-in con `it.mysql.enabled=true` |
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

Esiste evidenza precedente di validazione su MySQL 8.0.44 sia da installazione vuota sia da clone legacy, con Hibernate `ddl-auto=validate`. Questa certificazione non ha ripetuto la prova MySQL: la suite ordinaria usa H2 e il test MySQL è opt-in/skipped.

V2 è stata modificata storicamente. Un ambiente che abbia già applicato una versione differente può avere un checksum Flyway incompatibile. Il rischio riguarda gli ambienti già migrati, non le installazioni pulite validate da zero.

Prima di qualsiasi migrazione reale sono obbligatori:

1. controllo di `flyway_schema_history` del database destinatario;
2. backup e clone sottoposti ad analisi approvata, se il database è già esistente;
3. creazione di un nuovo schema MySQL isolato, senza usare il database originale né riusare/eliminare schemi temporanei precedenti;
4. migrazione completa, secondo avvio Flyway e Hibernate `ddl-auto=validate`;
5. verifica UTC, `DATETIME(6)`, dati, vincoli e indici.

Non usare `flyway repair`, baseline automatica o modifiche manuali della history senza analisi e autorizzazione. `baseline-on-migrate=false` e Flyway `clean` resta vietato sugli ambienti persistenti.

## Limiti accettati

- nessun endpoint di refresh;
- nessuna blacklist, rotazione o revoca JWT;
- nessun outbox o retry per email;
- nessun request ID HTTP o tracing distribuito;
- nessuna paginazione Booking;
- nessun multi-slot operativo;
- nessuna lifecycle API dedicata al link Professional–Client;
- timing side-channel residuo nella registrazione neutra;
- validazione MySQL finale da ripetere prima del trasferimento operativo.

## Funzionalità differite

Restano fuori dal perimetro MVP: reset password, logout server-side, upload immagine profilo, Workout, Nutrition, Feedback, Measurements, provider email esterno, template HTML, deploy e osservabilità di produzione.

## Indicazioni per il frontend

Il frontend può integrare i 29 endpoint applicativi esistenti. Deve usare le risposte neutre `202` per le registrazioni, non dedurre l'esistenza dell'email, trattare `ErrorResponse` tramite `code`, invalidare la sessione al `401`, rispettare `403` e `404` neutri e usare gli offset restituiti per Availability e Booking. Le response Booking sono autosufficienti; non è richiesto leggere lo slot live per ricostruire lo storico.

## Avvertenze documentali

I documenti storici datati possono citare conteggi o contratti precedenti. Non sono fonte dello stato corrente: per l'implementazione usare questa certificazione, il README, l'Endpoint Map, il Security Flow e la mappa frontend aggiornata.
