# Database Schema — Support Trainer

> V9 additiva: `booking_requests` include `rejection_reason VARCHAR(1000) NULL`, `cancellation_reason VARCHAR(1000) NULL`, `cancelled_by VARCHAR(32) NULL`. Nessun backfill, indice o vincolo aggiuntivo: i metadata legacy null restano validi. `cancelled_by` mappa l'enum dedicato `BookingCancellationActor`, non `UserRole`.
>
> V10: tabella runtime `password_reset_tokens` (hash SHA-256, niente raw token). V11: `users.session_version BIGINT NOT NULL DEFAULT 0`. Lo schema Flyway runtime termina a V11.

## 1. Obiettivo del documento

Questo documento definisce lo schema del database di Support Trainer distinguendo chiaramente tra:

- schema **attualmente integrato nel backend**
- tabelle **già presenti nel database ma non ancora integrate nel codice**
- schema **pianificato per moduli futuri**

Lo scopo è:

- tradurre il domain model in tabelle SQL
- definire chiavi primarie e foreign key
- chiarire i vincoli principali
- mantenere coerenza tra database reale, codice attuale e roadmap futura

---

## 2. Convenzioni adottate

### 2.1 Naming

- nomi tabelle in **snake_case plurale**
- nomi colonne in **snake_case**

### 2.2 Chiavi primarie

Tutte le tabelle principali usano:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`

### 2.3 Timestamps

- tabelle principali: `created_at`, `updated_at`
- tabelle evento/token: almeno `created_at`
- dove serve, si aggiungono campi specifici come:
  - `expires_at`
  - `used_at`
  - `recorded_at`

Lo schema finale contiene 28 campi runtime che rappresentano istanti, tutti `DATETIME(6)` con semantica UTC. Ventitré appartenevano già al modello legacy e sono stati sottoposti alla conversione V4; gli altri cinque timestamp di transizione o snapshot sono stati aggiunti da V6. L'applicazione li mappa come `Instant`, imposta Hibernate/JDBC in UTC e normalizza a microsecondi. `Europe/Rome` non è una timezone di persistenza: resta la zona business per l'input/output civile degli slot.

### 2.4 Soft delete

Per alcune tabelle principali si usa:

- `active BOOLEAN NOT NULL DEFAULT TRUE`

### 2.5 Engine, charset e versionamento

Le tabelle runtime MySQL usano esplicitamente:

- `ENGINE=InnoDB`;
- charset `utf8mb4`;
- collation `utf8mb4_0900_ai_ci`;
- foreign key con `ON UPDATE RESTRICT` e `ON DELETE RESTRICT`.

Flyway governa le nove tabelle di dominio runtime della sezione 3 e, con la V7, l’infrastruttura Spring Session JDBC della sezione 3.10. La V1 riproduce lo schema legacy runtime; la V2 converge al contratto canonico iniziale; `V3_1`–`V3_9` ampliano a microsecondi le colonne. La V4 Java converte i valori legacy `Europe/Rome` in UTC dopo controlli completi su schema, InnoDB, precisione, gap/overlap, conteggi e digest. Essendo MySQL-specifica, la V4 verifica la precisione tramite `information_schema.COLUMNS.DATETIME_PRECISION`: `DatabaseMetaData.DECIMAL_DIGITS` non è autoritativo perché Connector/J può restituirlo nullo anche per `DATETIME(6)`. Le `V5_1`–`V5_9` rimuovono default e `ON UPDATE`; gli audit mappati restano `NOT NULL`, mentre i quattro timestamp ombra dei profili diventano nullable e congelati. La V7 crea le tabelle di store sessione server-side; `spring.session.jdbc.initialize-schema=never`. Sugli ambienti MySQL Hibernate usa `ddl-auto=validate` sulle entity di dominio (non sulle tabelle Spring Session).

---

## 3. Schema attualmente integrato nel backend

## 3.1 `users`

Tabella base della gerarchia utenti.

### Colonne principali

- `id`
- `first_name`
- `last_name`
- `email`
- `password`
- `profile_image_url`
- `role`
- `account_status`
- `email_verified`
- `session_version`
- `created_at`
- `updated_at`

### Vincoli principali

- `email` **UNIQUE**
- `first_name` `NOT NULL`
- `last_name` `NOT NULL`
- `email` `NOT NULL`
- `password` `NOT NULL`
- `role` `NOT NULL`
- `account_status` `NOT NULL`
- `email_verified` `NOT NULL`
- `session_version` `BIGINT NOT NULL DEFAULT 0` (V11)
- `profile_image_url` `VARCHAR(500)` nullable
- `role` e `account_status` `VARCHAR(50)`

### Note

Questa tabella contiene i campi comuni a tutti gli utenti. `session_version` è il confine di revoca logica delle sessioni: un reset password lo incrementa nella stessa transazione dell'aggiornamento hash; le request autenticate confrontano lo snapshot del principal con il valore persistito.

---

## 3.2 `professional_profiles`

Tabella figlia di `users` per i professionisti.

### Colonne principali

- `id`
- `specialization`
- `operational_status`
- `phone_number`
- `bio`
- `workplace_name`
- `city`
- `instagram_url`
- `website_url`
- `active`
- `created_at`
- `updated_at`

### Chiavi

- `id` → PK e FK verso `users(id)`

### Vincoli principali

- `specialization` `NOT NULL`
- `operational_status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`
- `specialization` `VARCHAR(100)`
- `instagram_url` e `website_url` `VARCHAR(500)`

### Note

Rappresenta i dati specifici del professionista.

---

## 3.3 `client_profiles`

Tabella figlia di `users` per i clienti.

### Colonne principali

- `id`
- `operational_status`
- `birth_date`
- `height_cm`
- `primary_goal`
- `gender`
- `medical_notes`
- `injury_notes`
- `notes`
- `active`
- `created_at`
- `updated_at`

### Chiavi

- `id` → PK e FK verso `users(id)`

### Vincoli principali

- `operational_status` `NOT NULL`
- `birth_date` `NOT NULL`
- `height_cm` `NOT NULL`
- `primary_goal` `NOT NULL`
- `gender` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`
- `height_cm` `DECIMAL(5,2)`
- `primary_goal` `VARCHAR(255)` dopo la V2

---

## 3.4 `professional_client_links`

Tabella intermedia per la relazione molti-a-molti tra professionisti e clienti.

### Colonne principali

- `id`
- `professional_id`
- `client_id`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`
- `client_id` → `client_profiles(id)`

### Vincoli principali

- `professional_id` `NOT NULL`
- `client_id` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Note

Non si impone qui un vincolo SQL rigido su `(professional_id, client_id, active)`.  
La regola di unicità del collegamento attivo viene gestita da:

- business logic
- query dedicate
- validazioni service layer

---

## 3.5 `invite_codes`

Codici invito generati dai professionisti.

### Colonne principali

- `id`
- `code`
- `professional_id`
- `expires_at`
- `used`
- `used_at`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`

### Vincoli principali

- `code` `NOT NULL UNIQUE`
- `professional_id` `NOT NULL`
- `expires_at` `NOT NULL`
- `used` `NOT NULL DEFAULT FALSE`
- `active` `NOT NULL DEFAULT TRUE`

### Note

`active` è utile per eventuale disattivazione logica di codici non ancora usati.

---

## 3.6 `email_verification_tokens`

### Colonne principali

- `id`
- `user_id`
- `token`
- `expires_at`
- `used`
- `used_at`
- `created_at`

### Foreign key

- `user_id` → `users(id)`

### Vincoli principali

- `user_id` `NOT NULL`
- `token` `NOT NULL UNIQUE`
- `token` `VARCHAR(500)`
- `expires_at` `NOT NULL`
- `used` `NOT NULL DEFAULT FALSE`

### Note

È il token di sicurezza attualmente realmente integrato nel backend per il flusso di verifica email del professionista.

---

## 3.6.1 `weekly_availability_rules` e durate

`weekly_availability_rules` conserva la finestra ricorrente del Personal Trainer: `professional_id`, giorno, `start_time`, `end_time`, luogo, capacità, stato attivo, prima data valida e audit. Le durate non sono compresse in una singola colonna: `weekly_availability_rule_durations(weekly_rule_id, duration_minutes)` è la tabella figlia normalizzata, con primary key composta.

Vincoli strutturali e applicativi:

- ogni durata è compresa fra 15 e 180 minuti ed è multipla di 15;
- la primary key composta impedisce duplicati per regola;
- il service verifica che gli estremi siano allineati a 15 minuti e che ogni durata rientri nella finestra;
- le regole attive dello stesso giorno non si sovrappongono;
- la foreign key delle durate usa `ON DELETE CASCADE`, mentre la regola riferisce il Professional con `ON DELETE RESTRICT`.

## 3.6.2 Audit Availability

`availability_rule_changes` registra update/deactivate immediati con regola, data business corrente, tipo, motivazione, numero di Booking occupanti coinvolti e audit. `availability_slot_changes` registra block/unblock della singola occorrenza con gli stessi dati di motivazione e impatto. Il service rende la motivazione obbligatoria quando `impacted_booking_count > 0`.

---

## 3.7 `availability_slots`

Slot di disponibilità dei professionisti.

### Colonne principali

- `id`
- `professional_id`
- `weekly_rule_id` nullable per gli slot manuali legacy
- `start_date_time`
- `end_date_time`
- `location_label`
- `capacity`
- `blocked`
- `status`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`
- `weekly_rule_id` → `weekly_availability_rules(id)`

### Vincoli principali

- `professional_id` `NOT NULL`
- `start_date_time` `NOT NULL`
- `end_date_time` `NOT NULL`
- `capacity >= 1`
- (`weekly_rule_id`, `start_date_time`) `UNIQUE` per l'idempotenza della materializzazione
- `status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Stati gestiti

- `AVAILABLE`
- `BLOCKED`
- `BOOKED`

### Note

Regole applicative attualmente implementate:

- gestione riservata ai professionisti `PERSONAL_TRAINER`;
- una regola genera una sola occorrenza-finestra per data nella rolling horizon, non una riga per combinazione inizio/durata;
- intervallo valido e data iniziale futura in creazione o aggiornamento manuale;
- assenza di sovrapposizioni tra slot attivi dello stesso professionista;
- protezione da overlap concorrenti tramite lock pessimista sul `ProfessionalProfile`;
- esclusione dalla lettura cliente degli slot scaduti, bloccati o privi di combinazioni con capacità temporale;
- divieto di ripianificazione manuale delle occorrenze generate;
- blocco di una occorrenza futura senza cancellare i Booking e con motivazione/audit quando ha impatto;
- immutabilità di data e ora dopo il primo coinvolgimento dello slot in una richiesta booking;
- creazione di un nuovo slot per proporre un intervallo temporale diverso dopo uno storico booking.

---

## 3.8 `booking_requests`

Richieste di prenotazione create dai clienti.

### Colonne principali

- `id`
- `client_id`
- `professional_id`
- `client_display_name`
- `professional_display_name`
- `status`
- `note`
- `active`
- `created_at`
- `updated_at`
- `confirmed_at`
- `rejected_at`
- `cancelled_at`
- `rejection_reason`
- `cancellation_reason`
- `cancelled_by`

### Foreign key

- `client_id` → `client_profiles(id)`
- `professional_id` → `professional_profiles(id)`

### Vincoli principali

- `client_id` `NOT NULL`
- `professional_id` `NOT NULL`
- `client_display_name VARCHAR(201) NOT NULL`
- `professional_display_name VARCHAR(201) NOT NULL`
- `status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`
- i timestamp di transizione sono `DATETIME(6)` nullable e persistiti UTC
- `rejection_reason VARCHAR(1000) NULL`
- `cancellation_reason VARCHAR(1000) NULL`
- `cancelled_by VARCHAR(32) NULL`, enum applicativo `CLIENT|PROFESSIONAL`

### Stati gestiti

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Note

Nel codice attuale la richiesta booking viene creata a partire da un singolo `availabilitySlotId`, un `startDateTime` e una `durationMinutes`; il server deriva la fine. Per le nuove richieste lo slot deve riferire una `WeeklyAvailabilityRule`. Gli slot manuali legacy con `weekly_rule_id` nullo restano nello schema per compatibilità e Booking storici, ma non sono selezionabili dalla create.

La presenza della tabella `booking_request_items` mantiene il modello estendibile a più slot in futuro, ma l’API attuale lavora su una richiesta single-slot.

Regole applicative attualmente implementate:

- booking consentito solo tra cliente e professionista collegati;
- professionista proprietario dello slot necessariamente `PERSONAL_TRAINER`;
- intervallo futuro, contenuto nell'occorrenza e non bloccato, con capacità temporale sufficiente;
- occupancy calcolata sugli intervalli sovrapposti delle richieste `PENDING` e `CONFIRMED`;
- assenza di overlap con altri Booking occupanti dello stesso Client;
- `note` facoltativa, normalizzata e limitata a `1000` caratteri;
- booking `PENDING` che riserva un posto sulla capacità dello slot;
- conferma a occupancy invariata, poiché `PENDING` e `CONFIRMED` occupano entrambi un posto;
- protezione delle transizioni tramite lock pessimista;
- conservazione dell’intervallo temporale originario dello slot anche dopo `REJECTED` o `CANCELLED`.
- V6 esegue il backfill dei display name dai profili correnti dopo preflight: per il legacy non sono una prova del nome originario;
- V6 usa `updated_at` solo per il timestamp dello stato finale legacy e non inferisce stati intermedi non ricostruibili.
- V9 aggiunge reason e actor nullable senza backfill; le nuove transizioni richiedono reject reason e cancel actor, mentre la nullability preserva soltanto lo storico.

---

## 3.9 `booking_request_items`

Dettaglio degli slot collegati a una richiesta booking.

### Colonne principali

- `id`
- `booking_request_id`
- `availability_slot_id`
- `scheduled_start`
- `scheduled_end`
- `location_label_snapshot`
- `created_at`
- `updated_at`

### Foreign key

- `booking_request_id` → `booking_requests(id)`
- `availability_slot_id` → `availability_slots(id)`

### Vincoli principali

- `booking_request_id` `NOT NULL`
- `availability_slot_id` `NOT NULL`
- coppia (`booking_request_id`, `availability_slot_id`) `UNIQUE`
- `scheduled_start DATETIME(6) NOT NULL`
- `scheduled_end DATETIME(6) NOT NULL`
- `location_label_snapshot VARCHAR(255) NULL`
- `updated_at DATETIME(6) NOT NULL` dopo il backfill conservativo della V2

### Note

Nel backend attuale ogni booking creato tramite API contiene un solo item.

`scheduled_start`, `scheduled_end` e `location_label_snapshot` sono gli snapshot dell'intervallo e del luogo al momento della prenotazione e non vengono aggiornati se l'occorrenza o la regola cambiano. V6 ricostruisce gli orari legacy dallo slot referenziato solo dopo averne verificato esistenza, ordine e precisione microsecondi; il migration fallisce se il backfill non è deterministico. La colonna luogo viene introdotta dalla V8 ed è nullable per preservare lo storico legacy.

`DATETIME(6)` è il contratto canonico finale di `updated_at`. La V2 valorizza soltanto gli eventuali null e preserva i valori legacy non nulli; le migrazioni successive uniformano la precisione, convertono la semantica in UTC e trasferiscono l'auditing all'applicazione.

La tabella collega la richiesta allo slot availability selezionato e consente al service layer di:

- calcolare l'occupancy concorrente delle richieste `PENDING` e `CONFIRMED` sugli intervalli richiesti;
- escludere dalla lettura cliente le finestre bloccate, scadute o senza combinazioni prenotabili;
- preservare gli intervalli esistenti anche quando una occorrenza viene bloccata;
- impedire la ripianificazione temporale di uno slot già coinvolto in una richiesta booking;
- conservare sul booking la transizione di stato relativa alla prenotazione per quello specifico cliente, senza mutare globalmente lo stato dello slot.

---

## 3.10 Infrastruttura Spring Session JDBC (non domain)

Le tabelle seguenti appartengono allo **storage delle sessioni server-side** (Spring Session JDBC), non al domain model applicativo. Non sono entity JPA di dominio e non devono essere interpretate come parte del modello business.

Sono create da Flyway `V7__create_spring_session_jdbc_schema.sql` (schema ufficiale Spring Session 4.0.2 per MySQL, copiato senza alterazioni semantiche). L’inizializzazione automatica di Spring Session è disabilitata (`spring.session.jdbc.initialize-schema=never`).

### `SPRING_SESSION`

#### Colonne

- `PRIMARY_ID` `CHAR(36) NOT NULL`
- `SESSION_ID` `CHAR(36) NOT NULL`
- `CREATION_TIME` `BIGINT NOT NULL`
- `LAST_ACCESS_TIME` `BIGINT NOT NULL`
- `MAX_INACTIVE_INTERVAL` `INT NOT NULL`
- `EXPIRY_TIME` `BIGINT NOT NULL`
- `PRINCIPAL_NAME` `VARCHAR(100)` nullable

#### Vincoli e indici

- PK `SPRING_SESSION_PK` su `PRIMARY_ID`
- unique index `SPRING_SESSION_IX1` su `SESSION_ID`
- index `SPRING_SESSION_IX2` su `EXPIRY_TIME`
- index `SPRING_SESSION_IX3` su `PRINCIPAL_NAME`
- `ENGINE=InnoDB` `ROW_FORMAT=DYNAMIC`

### `SPRING_SESSION_ATTRIBUTES`

#### Colonne

- `SESSION_PRIMARY_ID` `CHAR(36) NOT NULL`
- `ATTRIBUTE_NAME` `VARCHAR(200) NOT NULL`
- `ATTRIBUTE_BYTES` `BLOB NOT NULL`

#### Vincoli

- PK `SPRING_SESSION_ATTRIBUTES_PK` su (`SESSION_PRIMARY_ID`, `ATTRIBUTE_NAME`)
- FK `SPRING_SESSION_ATTRIBUTES_FK`: `SESSION_PRIMARY_ID` → `SPRING_SESSION(PRIMARY_ID)` `ON DELETE CASCADE`
- `ENGINE=InnoDB` `ROW_FORMAT=DYNAMIC`

### Relazione con l’autenticazione

Lo store JDBC persiste le sessioni autenticate (cookie HttpOnly + attributi di sessione, incluso `authenticatedAt`). Non sostituisce né estende le tabelle di dominio della sezione 3.1–3.9. La revoca dopo password reset è logica (`users.session_version`); la pulizia fisica delle righe Spring Session è best-effort post-commit.

---

## 3.11 `password_reset_tokens` (V10)

Tabella runtime Flyway per Password Recovery V1. Non memorizza il raw token.

### Colonne principali

- `id` `BIGINT` PK auto-increment
- `user_id` `BIGINT NOT NULL`
- `token_hash` `CHAR(64) NOT NULL`
- `created_at` `DATETIME(6) NOT NULL`
- `expires_at` `DATETIME(6) NOT NULL`
- `consumed_at` `DATETIME(6) NULL`

### Foreign key

- `fk_password_reset_tokens_user`: `user_id` → `users(id)` `ON DELETE RESTRICT ON UPDATE RESTRICT`

### Vincoli e indici

- `uk_password_reset_tokens_token_hash` UNIQUE su `token_hash`
- `idx_password_reset_tokens_user_id` su `user_id`
- `ENGINE=InnoDB` `utf8mb4_0900_ai_ci`

### Note

- `token_hash` è SHA-256 hex lowercase del raw token (64 caratteri);
- TTL applicativo 30 minuti su `expires_at`;
- consumo one-time e invalidazione dei token aperti tramite `consumed_at`;
- i timestamp sono `DATETIME(6)` UTC di proprietà applicativa, non fanno parte del set convertito da V4.

---

## 4. Tabelle già presenti nel database ma non ancora integrate nel codice

Queste tabelle possono già essere presenti nel database MySQL locale come preparazione ai moduli successivi, ma **al momento non risultano ancora integrate nei flussi runtime del backend attuale e non sono governate da Flyway**.

Il perimetro legacy non governato comprende dodici tabelle: `refresh_tokens`, `workout_plans`, `workout_weeks`, `workout_days`, `workout_exercises`, `workout_feedbacks`, `nutrition_plans`, `nutrition_weeks`, `nutrition_days`, `nutrition_entries`, `nutrition_feedbacks` e `client_measurements`. Le migrazioni runtime non le creano, non le modificano e non le eliminano. `password_reset_tokens` appartiene allo schema Flyway V10 (sezione 3.11), non a questo perimetro.

## 4.1 `refresh_tokens`

### Colonne principali

- `id`
- `user_id`
- `token`
- `expires_at`
- `revoked`
- `created_at`

### Foreign key

- `user_id` → `users(id)`

### Vincoli principali

- `user_id` `NOT NULL`
- `token` `NOT NULL UNIQUE`
- `expires_at` `NOT NULL`
- `revoked` `NOT NULL DEFAULT FALSE`

### Nota importante

`refresh_tokens` è una **struttura legacy** eventualmente presente in database locali storici. **Non governa** l’autenticazione session-based corrente.

Nel backend attuale:

- il login **non** genera né restituisce un refresh token;
- non esistono entity, repository o service runtime collegati a questa tabella;
- l’autenticazione usa Spring Session JDBC e cookie HttpOnly (`docs/09-security-flow.md`).

La tabella resta documentata qui solo come reperto legacy / non utilizzato dal flusso runtime corrente.

---

## 5. Schema pianificato per moduli futuri

Le tabelle seguenti appartengono alla roadmap progettuale, ma **non sono ancora da considerare integrate nel backend attuale né governate dalle migrazioni Flyway runtime**.

---

## 5.1 `workout_plans`

Schede di allenamento create dai professionisti.

### Colonne principali

- `id`
- `professional_id`
- `client_id`
- `title`
- `month_reference`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`
- `client_id` → `client_profiles(id)`

### Vincoli principali

- `professional_id` `NOT NULL`
- `client_id` `NOT NULL`
- `title` `NOT NULL`
- `month_reference` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Note

La regola della singola scheda attiva per coppia professionista-cliente andrà gestita a livello business/service.

---

## 5.2 `workout_weeks`

### Colonne principali

- `id`
- `workout_plan_id`
- `week_number`
- `created_at`
- `updated_at`

### Foreign key

- `workout_plan_id` → `workout_plans(id)`

### Vincoli principali

- `workout_plan_id` `NOT NULL`
- `week_number` `NOT NULL`

---

## 5.3 `workout_days`

### Colonne principali

- `id`
- `workout_week_id`
- `date`
- `day_label`
- `day_type`
- `notes`
- `created_at`
- `updated_at`

### Foreign key

- `workout_week_id` → `workout_weeks(id)`

### Vincoli principali

- `workout_week_id` `NOT NULL`
- `date` `NOT NULL`
- `day_label` `NOT NULL`
- `day_type` `NOT NULL`

---

## 5.4 `workout_exercises`

### Colonne principali

- `id`
- `workout_day_id`
- `exercise_name`
- `sets`
- `reps`
- `intensity`
- `recovery_time`
- `extra_techniques`
- `description`
- `logged_load`
- `logged_reps`
- `notes`
- `created_at`
- `updated_at`

### Foreign key

- `workout_day_id` → `workout_days(id)`

### Vincoli principali

- `workout_day_id` `NOT NULL`
- `exercise_name` `NOT NULL`
- `sets` `NOT NULL`
- `reps` `NOT NULL`

---

## 5.5 `nutrition_plans`

Piani alimentari creati dai professionisti.

### Colonne principali

- `id`
- `professional_id`
- `client_id`
- `title`
- `month_reference`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`
- `client_id` → `client_profiles(id)`

### Vincoli principali

- `professional_id` `NOT NULL`
- `client_id` `NOT NULL`
- `title` `NOT NULL`
- `month_reference` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Note

La regola del singolo piano attivo per coppia professionista-cliente andrà gestita a livello business/service.

---

## 5.6 `nutrition_weeks`

### Colonne principali

- `id`
- `nutrition_plan_id`
- `week_number`
- `created_at`
- `updated_at`

### Foreign key

- `nutrition_plan_id` → `nutrition_plans(id)`

### Vincoli principali

- `nutrition_plan_id` `NOT NULL`
- `week_number` `NOT NULL`

---

## 5.7 `nutrition_days`

### Colonne principali

- `id`
- `nutrition_week_id`
- `date`
- `day_label`
- `day_type`
- `notes`
- `created_at`
- `updated_at`

### Foreign key

- `nutrition_week_id` → `nutrition_weeks(id)`

### Vincoli principali

- `nutrition_week_id` `NOT NULL`
- `date` `NOT NULL`
- `day_label` `NOT NULL`
- `day_type` `NOT NULL`

---

## 5.8 `nutrition_entries`

### Colonne principali

- `id`
- `nutrition_day_id`
- `meal_type`
- `content`
- `quantity`
- `notes`
- `created_at`
- `updated_at`

### Foreign key

- `nutrition_day_id` → `nutrition_days(id)`

### Vincoli principali

- `nutrition_day_id` `NOT NULL`
- `meal_type` `NOT NULL`
- `content` `NOT NULL`

---

## 5.9 `workout_feedbacks`

### Colonne principali

- `id`
- `client_id`
- `professional_id`
- `workout_day_id`
- `message`
- `created_at`

### Foreign key

- `client_id` → `client_profiles(id)`
- `professional_id` → `professional_profiles(id)`
- `workout_day_id` → `workout_days(id)`

### Vincoli principali

- `client_id` `NOT NULL`
- `professional_id` `NOT NULL`
- `workout_day_id` `NOT NULL`
- `message` `NOT NULL`

---

## 5.10 `nutrition_feedbacks`

### Colonne principali

- `id`
- `client_id`
- `professional_id`
- `nutrition_day_id`
- `message`
- `created_at`

### Foreign key

- `client_id` → `client_profiles(id)`
- `professional_id` → `professional_profiles(id)`
- `nutrition_day_id` → `nutrition_days(id)`

### Vincoli principali

- `client_id` `NOT NULL`
- `professional_id` `NOT NULL`
- `nutrition_day_id` `NOT NULL`
- `message` `NOT NULL`

---

## 5.11 `client_measurements`

### Colonne principali

- `id`
- `client_id`
- `recorded_at`
- `weight_kg`
- `body_fat_percentage`
- `muscle_mass_kg`
- `waist_cm`
- `chest_cm`
- `hips_cm`
- `arm_cm`
- `thigh_cm`
- `shoulders_cm`
- `notes`
- `created_at`

### Foreign key

- `client_id` → `client_profiles(id)`

### Vincoli principali

- `client_id` `NOT NULL`
- `recorded_at` `NOT NULL`
- `weight_kg` `NOT NULL`

### Note

È una tabella storica, non va pensata come “profilo aggiornabile”.

---

## 6. Enum da salvare come stringa

## 6.1 Enum attualmente integrati nel backend

I seguenti campi enum devono essere salvati come stringhe leggibili:

- `users.role`
- `users.account_status`
- `professional_profiles.specialization`
- `professional_profiles.operational_status`
- `client_profiles.operational_status`
- `client_profiles.gender`
- `availability_slots.status`
- `booking_requests.status`

## 6.2 Enum previsti per moduli futuri

Quando verranno implementati i moduli futuri, andranno salvati come stringhe leggibili anche:

- `workout_days.day_type`
- `nutrition_days.day_type`

---

## 7. Foreign key attualmente integrate

### Area utenti

- `professional_profiles.id` → `users.id`
- `client_profiles.id` → `users.id`

### Area collegamenti

- `professional_client_links.professional_id` → `professional_profiles.id`
- `professional_client_links.client_id` → `client_profiles.id`

### Area inviti

- `invite_codes.professional_id` → `professional_profiles.id`

### Area sicurezza

- `email_verification_tokens.user_id` → `users.id`
- `password_reset_tokens.user_id` → `users.id`

### Area availability

- `availability_slots.professional_id` → `professional_profiles(id)`

### Area booking

- `booking_requests.client_id` → `client_profiles(id)`
- `booking_requests.professional_id` → `professional_profiles(id)`
- `booking_request_items.booking_request_id` → `booking_requests(id)`
- `booking_request_items.availability_slot_id` → `availability_slots(id)`

---

## 8. Foreign key future o non ancora integrate

### Tabelle già presenti nel DB ma non ancora integrate

- `refresh_tokens.user_id` → `users.id`

### Area workout

- `workout_plans.professional_id` → `professional_profiles.id`
- `workout_plans.client_id` → `client_profiles.id`
- `workout_weeks.workout_plan_id` → `workout_plans.id`
- `workout_days.workout_week_id` → `workout_weeks.id`
- `workout_exercises.workout_day_id` → `workout_days.id`

### Area nutrition

- `nutrition_plans.professional_id` → `professional_profiles.id`
- `nutrition_plans.client_id` → `client_profiles.id`
- `nutrition_weeks.nutrition_plan_id` → `nutrition_plans.id`
- `nutrition_days.nutrition_week_id` → `nutrition_weeks.id`
- `nutrition_entries.nutrition_day_id` → `nutrition_days.id`

### Area feedback

- `workout_feedbacks.client_id` → `client_profiles.id`
- `workout_feedbacks.professional_id` → `professional_profiles.id`
- `workout_feedbacks.workout_day_id` → `workout_days.id`
- `nutrition_feedbacks.client_id` → `client_profiles.id`
- `nutrition_feedbacks.professional_id` → `professional_profiles.id`
- `nutrition_feedbacks.nutrition_day_id` → `nutrition_days.id`

### Area misurazioni

- `client_measurements.client_id` → `client_profiles.id`

---

## 9. Unique constraints principali

## 9.1 Vincoli SQL confermati nello schema attualmente integrato

- `users.email` tramite `uk_users_email`
- `invite_codes.code` tramite `uk_invite_codes_code`
- `email_verification_tokens.token` tramite `uk_email_verification_tokens_token`
- `password_reset_tokens.token_hash` tramite `uk_password_reset_tokens_token_hash`
- coppia `booking_request_items(booking_request_id, availability_slot_id)` tramite `uk_booking_request_items_request_slot`

## 9.2 Tabelle già presenti nel DB ma non ancora integrate

- `refresh_tokens.token`

## 9.3 Da gestire a livello business/service

### Area utenti e collegamenti

- massimo 3 professionisti attivi per cliente;
- un solo collegamento attivo per coppia professionista-cliente;
- divieto di auto-collegamento.

### Area availability

- gestione delle regole settimanali riservata ai professionisti `PERSONAL_TRAINER`;
- giorno, finestra, durate multiple normalizzate, luogo e capacità concorrente come dati della regola ricorrente;
- estremi, inizi selezionabili e durate allineati a 15 minuti; durate da 15 a 180 minuti che devono rientrare nella finestra;
- materializzazione di una occorrenza-finestra per data per un intervallo di 6 mesi, esteso automaticamente;
- assenza di sovrapposizioni tra regole e slot attivi dello stesso professionista, con adiacenza consentita;
- blocco della singola occorrenza futura senza modificare la regola o cancellare Booking, con audit e motivazione in caso di impatto;
- update/deactivate immediati che preservano passato e Booking, con anteprima impatto e motivazione obbligatoria quando necessario;
- capacità che non può scendere sotto la massima occupancy concorrente `PENDING` più `CONFIRMED`.

### Area booking

- booking consentito solo tra cliente e professionista collegati;
- booking consentito solo su slot appartenenti a un `PERSONAL_TRAINER`;
- booking consentito solo su combinazioni future, contenute nella finestra, non bloccate e con capacità temporale residua;
- inizio e durata scelti dal Client, fine derivata e validata dal server;
- richieste `PENDING` e `CONFIRMED` conteggiate come occupancy negli intervalli temporali sovrapposti;
- divieto race-safe di Booking occupanti sovrapposti per lo stesso Client;
- nota facoltativa, normalizzata e limitata a `1000` caratteri;
- transizioni booking consentite:
  - `PENDING -> CONFIRMED`;
  - `PENDING -> REJECTED`;
  - `PENDING -> CANCELLED`;
  - `CONFIRMED -> CANCELLED`;
- conferma booking a occupancy invariata e senza transizione globale dello slot a `BOOKED`;
- rifiuto e cancellazione che liberano il posto occupato;
- preservazione dell'intervallo temporale e del luogo originari nello storico del booking.

### Protezione da concorrenza

- lock condiviso sul professionista e lock della regola per coordinare materializzazione, update e deactivate;
- lock pessimista sul Client e sull'occorrenza, con nuovo calcolo di overlap e occupancy durante creazione booking;
- lock pessimista sulla richiesta durante conferma, rifiuto e cancellazione;
- lock pessimista sullo slot durante conferma booking;
- lock pessimista sullo slot durante modifica o blocco manuale in presenza potenziale di richieste booking.

---

## 9.4 Vincoli futuri previsti

- una sola scheda workout attiva per coppia personal trainer-cliente;
- un solo piano nutrizione attivo per coppia nutrizionista-cliente;
- regole di ownership per misurazioni e feedback;
- eventuali vincoli aggiuntivi per versionamento di workout plan e nutrition plan.

---

## 10. Note progettuali importanti

### 10.1 JOINED inheritance

La scelta `JOINED` tra:

- `users`
- `professional_profiles`
- `client_profiles`

è coerente con:

- domain model
- strategia JPA definita
- separazione pulita tra campi comuni e specifici

### 10.2 Immagine profilo

Nel database viene salvato solo:

- `profile_image_url`

Non si salvano file binari nel DB.

### 10.3 Storico dati

Restano storicizzati:

- collegamenti disattivati;
- codici invito usati o scaduti;
- token usati o scaduti, se mantenuti;
- richieste booking rifiutate o cancellate;
- slot availability già coinvolti in booking, mantenendo immutabile il relativo intervallo temporale;
- dati futuri storici quando i relativi moduli verranno implementati.

### 10.4 Regola di lettura del documento

Questo documento va sempre letto distinguendo tra:

- tabelle già supportate dal backend attuale
- tabelle già presenti nel DB ma non ancora usate dal codice
- tabelle solo pianificate per i moduli futuri

---

## 11. Decisioni confermate

Per Support Trainer si confermano le seguenti scelte:

- naming SQL in snake_case;
- tabelle plurali;
- PK `BIGINT AUTO_INCREMENT`;
- immagine profilo salvata come URL/path;
- vincoli logici complessi gestiti a livello business/service;
- separazione chiara tra schema attuale e schema futuro;
- tabelle token documentate distinguendo tra uso reale e preparazione tecnica;
- slot scaduti o con booking `PENDING` esclusi dalle disponibilità mostrate al cliente;
- intervallo temporale degli slot immutabile dopo il primo coinvolgimento in una richiesta booking;
- ripianificazione gestita tramite creazione di un nuovo slot, non modifica dello slot storico.

---

## 12. Migrazioni Flyway dello schema runtime

### 12.1 Perimetro e ordine

Le risorse versionate sono applicate in questo ordine:

1. `V1__create_legacy_compatible_runtime_schema.sql`;
2. `V2__align_runtime_schema_contract.sql`;
3. `V3_1__expand_users_timestamps_to_microseconds.sql`;
4. `V3_2__expand_professional_profiles_timestamps_to_microseconds.sql`;
5. `V3_3__expand_client_profiles_timestamps_to_microseconds.sql`;
6. `V3_4__expand_professional_client_links_timestamps_to_microseconds.sql`;
7. `V3_5__expand_invite_codes_timestamps_to_microseconds.sql`;
8. `V3_6__expand_email_verification_tokens_timestamps_to_microseconds.sql`;
9. `V3_7__expand_availability_slots_timestamps_to_microseconds.sql`;
10. `V3_8__expand_booking_requests_timestamps_to_microseconds.sql`;
11. `V3_9__expand_booking_request_items_timestamps_to_microseconds.sql`;
12. `V4__convert_runtime_datetimes_from_rome_to_utc`;
13. `V5_1__transfer_users_audit_ownership_to_application.sql`;
14. `V5_2__freeze_professional_profile_shadow_timestamps.sql`;
15. `V5_3__freeze_client_profile_shadow_timestamps.sql`;
16. `V5_4__transfer_link_audit_ownership_to_application.sql`;
17. `V5_5__transfer_invite_audit_ownership_to_application.sql`;
18. `V5_6__transfer_email_token_audit_ownership_to_application.sql`;
19. `V5_7__transfer_availability_audit_ownership_to_application.sql`;
20. `V5_8__transfer_booking_request_audit_ownership_to_application.sql`;
21. `V5_9__transfer_booking_item_audit_ownership_to_application.sql`;
22. `V6__add_booking_historical_snapshots`;
23. `V7__create_spring_session_jdbc_schema.sql`;
24. `V8__add_weekly_availability_and_capacity.sql`;
25. `V9__add_booking_transition_metadata.sql`;
26. `V10__password_recovery.sql`;
27. `V11__user_session_version.sql`.

Questo è l’elenco delle migrazioni Flyway correnti. Lo schema runtime versionato termina a V11.

La V1 crea esclusivamente le nove tabelle di dominio runtime, con PK, FK restrittive, unique, nullability, default, precisioni, engine, charset, collation e indici dello schema legacy. Non contiene dati applicativi.

La V2:

- porta `client_profiles.primary_goal` da `VARCHAR(150)` a `VARCHAR(255)`;
- valorizza esclusivamente gli eventuali `booking_request_items.updated_at` nulli usando `created_at` o, in fallback, `CURRENT_TIMESTAMP(6)`;
- porta `booking_request_items.updated_at` a `DATETIME(6) NOT NULL`, preservando esattamente i valori legacy già presenti e mantenendo default e aggiornamento automatico a precisione 6;
- aggiunge quattro indici composti motivati dalle query runtime.

Le nove V3 contengono esclusivamente un `ALTER TABLE` ciascuna. Portano a `DATETIME(6)` i timestamp della rispettiva tabella, preservando nullability, default e aggiornamento automatico. `client_profiles.birth_date` resta `DATE`; `booking_request_items.updated_at`, già definito a microsecondi dalla V2, non viene alterato nuovamente.

Il passaggio strutturale delle sole V3 da `DATETIME(0)` a `DATETIME(6)` mantiene invariati anno, mese, giorno, ora, minuto e secondo e aggiunge una frazione zero. Le V3 non usano `CONVERT_TZ` e non convertono i valori da `Europe/Rome` a UTC: questa responsabilità appartiene alla successiva V4, mentre le V5 trasferiscono l'ownership degli audit all'applicazione.

La V4 Java verifica schema, precisione, gap/overlap e dati prima di convertire i datetime legacy `Europe/Rome` verso UTC. Le V5 rimuovono default e `ON UPDATE` dagli audit, trasferendone l'ownership a Spring Data JPA; i timestamp ombra dei profili diventano nullable e congelati. La V6 Java aggiunge gli snapshot storici Booking e ne esegue il backfill dopo preflight, senza inventare dati o orari. La V7 crea `SPRING_SESSION` e `SPRING_SESSION_ATTRIBUTES` per lo store JDBC delle sessioni server-side. La V8 aggiunge `weekly_availability_rules`, la tabella normalizzata delle durate, gli audit di regola e occorrenza, i campi `weekly_rule_id`, `location_label`, `capacity`, `blocked` sugli slot e `location_label_snapshot` sugli item Booking, con backfill conservativo degli slot legacy. La V9 aggiunge `rejection_reason`, `cancellation_reason` e `cancelled_by` nullable a `booking_requests`, senza backfill né nuovi indici. La V10 crea `password_reset_tokens` (hash SHA-256, TTL/consumo applicativi). La V11 aggiunge `users.session_version BIGINT NOT NULL DEFAULT 0`.

### 12.2 Indici di convergenza

- `invite_codes(professional_id, created_at)` supporta la lista inviti del professionista ordinata per creazione.
- `availability_slots(professional_id, active, status, start_date_time)` supporta gli slot visibili al cliente filtrati per stato e ordinati temporalmente.
- `availability_slots(professional_id, active, blocked, start_date_time)` supporta il filtro di prenotabilità e capacità;
- `weekly_availability_rules(professional_id, active, day_of_week, valid_from)` supporta la settimana tipo;
- `availability_slots(weekly_rule_id, start_date_time)` è univocamente vincolato per rendere idempotente la materializzazione.
- `booking_request_items(availability_slot_id, scheduled_start, scheduled_end)` supporta i calcoli temporali di occupancy e overlap.
- `booking_requests(client_id, active, created_at)` supporta le liste booking del cliente.
- `booking_requests(professional_id, active, created_at)` supporta le liste booking del professionista.

Gli indici legacy con prefissi parzialmente sovrapposti vengono preservati nella prima adozione per mantenere la migrazione non distruttiva. Un'eventuale rimozione richiederà dati rappresentativi, analisi `EXPLAIN` e una migrazione separata.

Non viene introdotta una unique su `professional_client_links(professional_id, client_id, active)`: impedirebbe di conservare più record storici inattivi della stessa coppia.

### 12.3 Validazione conclusiva MySQL 8

> Sezione storica: i conteggi e la versione finale riportati nei §§12.3–12.12 fotografano la certificazione del 16 luglio 2026 fino a V6; non descrivono l'elenco runtime corrente, oggi esteso forward-only fino a V9 nel §12.1.

Il 16 luglio 2026 la validazione conclusiva su MySQL 8.0.44 ha prodotto il verdetto **MYSQL VALIDATION PASSED WITH WARNINGS**. Sono stati creati nuovi i due schemi isolati:

- `support_trainer_audit_empty_20260716_101232`;
- `support_trainer_audit_legacy_20260716_101232`.

Entrambi usano charset `utf8mb4` e collation `utf8mb4_0900_ai_ci`. Sono rimasti presenti al termine e non devono essere eliminati senza autorizzazione. Durante questa validazione isolata il database originale `support_trainer` non è stato interrogato o modificato; è stato analizzato in sola lettura soltanto nella successiva fase di rehearsal descritta nelle sezioni 12.8–12.12.

In entrambi gli schemi la history finale contiene 22 righe versionate, 22 successi, 0 failed, nessuna versione duplicata e V6 come ultima versione. Non risultano migrazioni pending; il secondo avvio sullo schema empty non ha eseguito nuove migrazioni o aggiunto righe. I checksum osservati e coincidenti sono:

- V2: `-602898647`;
- V6: `-840301506`.

### 12.4 Percorsi empty e legacy simulato

Lo schema empty è partito vuoto e ha applicato l'intera sequenza V1 → V6. Hibernate `ddl-auto=validate` ha accettato il contratto risultante senza produrre DDL e ha verificato anche la struttura Booking V6.

Lo schema legacy ha applicato le migrazioni fino a V5.9, ricevuto esclusivamente dati fixture controllati da `BookingHistoricalSnapshotMySqlIntegrationTest` e applicato V6. Il backfill ha popolato:

- `client_display_name`;
- `professional_display_name`;
- `scheduled_start`;
- `scheduled_end`;
- `cancelled_at` con precisione microsecondi.

Per il caso testato `confirmed_at` e `rejected_at` sono rimasti null. I nomi presenti nelle fixture non sono dati reali. Un secondo `migrate` sul percorso legacy non ha eseguito operazioni.

Le sette colonne Booking V6 sono risultate coerenti tra migrazione Java, entity, DTO, test MySQL e test H2:

- `client_display_name`;
- `professional_display_name`;
- `scheduled_start`;
- `scheduled_end`;
- `confirmed_at`;
- `rejected_at`;
- `cancelled_at`.

### 12.5 UTC, precisione e fotografia strutturale

La connessione applicativa ha operato con `session.time_zone=+00:00`. `NOW(6)`, `CURRENT_TIMESTAMP(6)` e `UTC_TIMESTAMP(6)` coincidevano, con differenza rilevata di 0 microsecondi. La configurazione verificata usa:

- `connectionTimeZone=+00:00`;
- `forceConnectionTimeZoneToSession=true`;
- `hibernate.jdbc.time_zone=UTC`.

Nello schema empty non sono state rilevate colonne temporali applicative con precisione diversa da `DATETIME(6)`, trigger, default temporali DB o clausole temporali `ON UPDATE`.

La fotografia strutturale osservata durante questa validazione comprende:

- 9 tabelle applicative e 1 `flyway_schema_history`, 10 totali;
- 85 colonne applicative e 95 totali includendo la history;
- 4 unique constraint;
- 11 foreign key;
- 1 check constraint;
- foreign key con `ON UPDATE RESTRICT` e `ON DELETE RESTRICT`.

Questi conteggi descrivono la baseline certificata del 16 luglio 2026 e non costituiscono requisiti rigidi: le evoluzioni future devono avvenire tramite nuove migrazioni forward-only.

`BookingHistoricalSnapshotMySqlIntegrationTest` è stato eseguito come test opt-in con 1 test, 0 failure, 0 error, 0 skipped e `BUILD SUCCESS`. Il successivo `clean verify` ordinario su H2 ha prodotto il JAR con 50 suite, 312 test, 0 failure, 0 error e 1 skipped previsto: il test MySQL, che resta opt-in.

I warning non bloccanti sono stati MySQL 1681 sulla display width degli interi durante V1, un'API deprecata usata in `AvailabilityServiceIntegrationTest`, il caricamento dinamico dell'agente Mockito/Byte Buddy e un primo tentativo Maven bloccato dalla policy di rete seguito da esecuzione riuscita. Non costituiscono difetti funzionali o vulnerabilità accertate.

### 12.6 Database esistente, baseline V1 e rischio V2

Non è ammessa la baseline automatica. Per uno schema esistente sono obbligatori backup verificato, clone isolato, confronto materiale con V1, verifica dei dati e approvazione esplicita. La registrazione deve essere eseguita tramite Flyway, mai con `INSERT` manuali nella history.

Prima della migrazione, il database originale `support_trainer` è stato verificato in sola lettura: conteneva 22 tabelle, 181 colonne, 27 record applicativi, non conteneva `flyway_schema_history` e coincideva materialmente con V1 per struttura, conteggi e aggregati. Il rehearsal completo sul clone fedele è riuscito e ha costituito il prerequisito per la procedura controllata poi eseguita sull'originale.

V2 risulta modificata storicamente, ma il rischio di incompatibilità con una variante già registrata non si applicava alla fotografia dell'originale perché la history era assente. Dopo la baseline V1, V2 è stata registrata con checksum `-602898647` e tutte le migrazioni fino a V6 sono riuscite. `baseline-on-migrate` resta `false`; non eseguire nuovamente la baseline sullo schema corrente. Flyway `repair`, modifiche manuali della history e `clean` restano vietati. Le tabelle legacy future restano fuori dalla history finché i relativi moduli non saranno progettati e approvati.

### 12.7 Immutabilità e rollback

Tutte le risorse nella baseline corrente devono rimanere immutabili dopo la loro applicazione; le correzioni future usano nuove migrazioni forward-only. V4 contiene DML transazionale e non effettua commit manuali; ogni V5 circoscrive il proprio DDL. V6 è non transazionale: un errore non deve essere seguito da correzioni SQL improvvisate, `repair` o rollback manuali. Il recupero resta basato su backup verificato e ripristino controllato in un nuovo schema, non sulla cancellazione automatica dello schema.

### 12.8 Backup e restore verificato del database originale

Il rehearsal ha usato esclusivamente il backup:

`C:\Users\96and\AppData\Local\Temp\support_trainer_backup_20260713_151823\support_trainer_backup_20260713_151823.sql`

Evidenza verificata:

- dimensione: 30.731 byte;
- SHA-256: `763763FD276A8D972CD520525E187CBC454455DB22C4F2BC986221A58F5F3EE7`;
- dimensione e hash invariati prima e dopo l'importazione;
- 22 istruzioni `CREATE TABLE`;
- 9 istruzioni `INSERT INTO`;
- nessun `CREATE DATABASE`, `DROP DATABASE` o `USE`;
- nessun riferimento qualificato ad altri schemi;
- nessun trigger, routine, evento, `LOAD DATA`, `INTO OUTFILE`, `SOURCE` o comando esterno.

Il dump non poteva cambiare autonomamente database ed è stato importato specificando come unica destinazione `support_trainer_rehearsal_legacy_20260716_105457`. Non sono stati riportati né modificati valori degli `INSERT`.

Lo stato iniziale del clone era:

- MySQL 8.0.44;
- charset `utf8mb4`;
- collation `utf8mb4_0900_ai_ci`;
- 22 tabelle InnoDB;
- 181 colonne;
- 0 viste, trigger, routine ed eventi;
- `flyway_schema_history` assente;
- struttura materiale V1;
- conteggi e aggregati coincidenti con l'originale.

Il clone è rimasto presente al termine ed è ora nello stato finale V6.

### 12.9 Baseline V1 e migrazioni V2 → V6

La baseline è stata creata con Flyway Maven Plugin 11.14.1, già disponibile nelle dipendenze locali, senza modificare `pom.xml`. I parametri concettuali sono:

- baseline version `1`;
- descrizione `Legacy schema V1 verified`;
- `baselineOnMigrate=false`;
- `cleanDisabled=true`;
- location `classpath:db/migration`.

La prima riga di `flyway_schema_history` contiene:

- `installed_rank=1`;
- `version=1`;
- `type=BASELINE`;
- checksum nullo;
- `success=1`.

V1 non è stata rieseguita: lo schema V1 era già presente nel backup e la baseline ha registrato soltanto la fotografia verificata. Non è stata eseguita alcuna modifica manuale della history.

Sono state applicate 21 migrazioni effettive: V2, V3.1–V3.9, V4, V5.1–V5.9 e V6. La durata osservata è stata 3.321 ms:

| Blocco    | Durata osservata |
| --------- | ---------------: |
| V2        |           403 ms |
| V3.1–V3.9 |         1.370 ms |
| V4        |            79 ms |
| V5.1–V5.9 |           478 ms |
| V6        |           991 ms |

Questi tempi descrivono soltanto il rehearsal locale e non costituiscono una garanzia per altri computer o quantità di dati.

La history finale contiene:

- 22 righe complessive;
- 1 riga BASELINE V1;
- 21 migrazioni effettive;
- 22 successi;
- 0 failed;
- 22 versioni distinte;
- nessun duplicato;
- V6 come versione finale;
- checksum V2 `-602898647`;
- checksum V6 `-840301506`.

Il secondo `migrate` non ha applicato operazioni né aggiunto righe. `Flyway validate` è riuscito senza migrazioni pending, failed, applicate ma non risolte o risolte ma non applicate. Flyway ha indicato 23 elementi validati perché considera anche la risorsa V1 rispetto alla baseline; la history contiene correttamente 22 righe.

### 12.10 Verifiche delle migrazioni sul clone

V2 ha:

- ampliato `client_profiles.primary_goal` a `VARCHAR(255) NOT NULL`;
- reso `booking_request_items.updated_at` `DATETIME(6) NOT NULL`;
- creato `idx_invite_codes_professional_created`;
- creato `idx_availability_slots_professional_active_status_start`;
- creato `idx_booking_requests_client_active_created`;
- creato `idx_booking_requests_professional_active_created`;
- preservato conteggi e vincoli.

Le V3 hanno convertito 22 colonne da `DATETIME(0)` a `DATETIME(6)`; una colonna era già `DATETIME(6)`. Dopo V6 le colonne temporali runtime sono 28, tutte a precisione 6, senza colonne temporali runtime a precisione inferiore.

Il preflight V4 ha verificato:

- 9 tabelle;
- 27 righe;
- 72 celle temporali;
- 70 valori non nulli;
- 2 valori null;
- nessun valore in gap DST;
- nessun valore ambiguo in overlap DST;
- digest atteso e finale coincidenti;
- nessuna riga persa;
- nessuna perdita di microsecondi;
- 5 valori frazionari preservati;
- 13 valori convertiti applicando lo scarto Europe/Rome di un'ora;
- 57 valori convertiti applicando lo scarto Europe/Rome di due ore.

V5 ha prodotto lo stato finale previsto:

- zero default temporali DB sulle colonne runtime;
- zero clausole temporali `ON UPDATE`;
- zero trigger;
- auditing affidato all'applicazione;
- nullability coerente con entity e migrazioni.

V6 ha aggiunto:

- `booking_requests.client_display_name`;
- `booking_requests.professional_display_name`;
- `booking_requests.confirmed_at`;
- `booking_requests.rejected_at`;
- `booking_requests.cancelled_at`;
- `booking_request_items.scheduled_start`;
- `booking_request_items.scheduled_end`.

Il backfill ha preservato 5 Booking e 5 item, valorizzato 5 snapshot Client, 5 snapshot Professional e 5 intervalli storici. Le timeline risultano:

- 3 richieste `CANCELLED` con solo `cancelled_at`;
- 1 richiesta `CONFIRMED` con solo `confirmed_at`;
- 1 richiesta `REJECTED` con solo `rejected_at`;
- zero timestamp di transizione non pertinenti;
- zero orphan;
- zero duplicati request/slot;
- nessuno stato parziale.

V6 è non transazionale: il rehearsal completo e il backup immediatamente precedente sono stati prerequisiti obbligatori per l'esecuzione sull'originale. Le stesse cautele restano necessarie per eventuali future migrazioni non transazionali.

### 12.11 Struttura finale, Hibernate, UTC e test

Lo stato finale del clone comprende:

- 22 tabelle applicative preservate;
- 13 tabelle legacy/future ancora presenti e vuote;
- `flyway_schema_history` come unica nuova tabella;
- 23 tabelle totali;
- 188 colonne applicative;
- 198 colonne totali;
- 22 righe nella history.

Le nove tabelle runtime sono state confrontate con `support_trainer_audit_empty_20260716_101232`:

- 85 colonne runtime;
- 41 indici;
- 25 constraint;
- 11 foreign key;
- 1 check;
- zero differenze su nomi, tipi, lunghezze, precisione, scala, nullability, default, extra, chiavi o regole referenziali.

Hibernate `ddl-auto=validate` ha inizializzato l'`EntityManagerFactory` senza emettere `CREATE`, `ALTER` o `DROP`. Durante l'avvio Flyway ha rilevato V6 e applicato zero migrazioni. Un avvio web reale è stato completato su porta effimera con email mode `DISABLED`; lo shutdown è stato eseguito in modo controllato e non sono rimasti processi Java.

La connessione applicativa ha verificato:

- `session.time_zone=+00:00`;
- `connectionTimeZone=+00:00`;
- `forceConnectionTimeZoneToSession=true`;
- `hibernate.jdbc.time_zone=UTC`;
- `NOW(6)`, `CURRENT_TIMESTAMP(6)` e `UTC_TIMESTAMP(6)` coincidenti;
- differenza tra sessione e UTC pari a 0 microsecondi.

Il `clean verify` successivo ha usato H2 e ha prodotto:

- `BUILD SUCCESS`;
- 50 suite;
- 312 test;
- 0 failure;
- 0 error;
- 1 skipped previsto, il test MySQL opt-in;
- JAR generato.

Warning non bloccanti:

- API deprecata in `AvailabilityServiceIntegrationTest`;
- self-attach Mockito/Byte Buddy;
- primo tentativo Maven limitato dalla policy di rete, seguito da esecuzione riuscita;
- prima osservazione dello shutdown incompleta, seguita da arresto verificato correttamente tramite Spring Admin MBean.

Questi warning non sono difetti applicativi.

### 12.12 Migrazione controllata eseguita sul database originale

Il 16 luglio 2026 il runbook provato sul clone è stato eseguito sul database originale `support_trainer`, durante una finestra controllata e dopo un nuovo backup verificato. Il riferimento completo del backup, inclusi dimensione e SHA-256, è registrato in [Certificazione tecnica finale](final-audit-mvp.md).

Lo stato iniziale confermato era:

- MySQL 8.0.44;
- 22 tabelle e 181 colonne;
- 27 record applicativi;
- `flyway_schema_history` assente;
- struttura materiale V1;
- zero viste, trigger, routine ed eventi.

Flyway ha registrato una sola riga `BASELINE` versione 1, checksum nullo e successo, senza rieseguire V1. Il dump runtime prima e dopo la baseline era identico. Sono state quindi applicate esattamente 21 migrazioni da V2 a V6.

Lo stato finale certificato è:

- 23 tabelle totali e 198 colonne totali;
- 22 tabelle applicative e 188 colonne applicative;
- 22 righe history: 1 baseline e 21 migrazioni;
- 22 successi, 0 failed e V6 finale;
- checksum V2 `-602898647` e V6 `-840301506`;
- 27 record applicativi preservati;
- zero record orfani o duplicazioni request/slot;
- 28 colonne runtime `DATETIME(6)`, senza precisioni inferiori;
- zero default temporali DB, clausole `ON UPDATE` o trigger runtime.

V4 ha convertito 70 valori su 23 colonne, 9 tabelle e 27 righe, preservando 2 null e 5 valori frazionari. V6 ha valorizzato 5 snapshot Client, 5 snapshot Professional e 5 intervalli storici, con zero campi obbligatori null e zero timeline incoerenti.

`Flyway validate` è riuscito e il secondo `migrate` non ha applicato operazioni. Hibernate `ddl-auto=validate`, la sessione JDBC UTC e lo startup controllato con email `DISABLED` sono riusciti; il backend è stato arrestato con shutdown grazioso.

Lo schema corrente è già baselinato e migrato: non ripetere la baseline e non applicare istruzioni pensate per una fotografia legacy priva di history. Le evoluzioni future devono usare esclusivamente nuove migrazioni forward-only.

#### Regole di recupero ancora valide

Poiché V4 modifica dati e V6 è non transazionale:

1. non tentare rollback SQL manuali;
2. non usare `flyway repair`;
3. non modificare manualmente `flyway_schema_history`;
4. non eseguire Flyway `clean`;
5. in caso di errore futuro, arrestare l'applicazione e conservare lo schema per l'analisi;
6. ripristinare un backup verificato esclusivamente in un nuovo schema;
7. validare struttura, conteggi, hash e aggregati prima di riconfigurare il backend;
8. non sovrascrivere o eliminare i backup precedenti.

#### Trasferimento dei file completato

Il trasferimento verso il progetto originale ha:

- copiato soltanto file tracciati;
- escluso `frontend/` non tracciata, `backend/target/` e `application.properties` locale;
- escluso schemi, dump e backup;
- preservato 37 commit lineari senza merge commit;
- allineato `main`, HEAD e `origin/main` al commit certificato;
- verificato il contenuto trasferito tramite confronto dei file e `clean verify`;
- evitato avvii automatici contro `support_trainer`; lo startup è stato eseguito separatamente e in modo controllato dopo la migrazione.
