# Database Schema — Support Trainer

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

I 23 campi runtime che rappresentano istanti usano `DATETIME(6)` con semantica UTC. L'applicazione li mappa come `Instant`, imposta Hibernate/JDBC in UTC e normalizza a microsecondi. `Europe/Rome` non è una timezone di persistenza: resta la zona business per l'input/output civile degli slot.

### 2.4 Soft delete
Per alcune tabelle principali si usa:
- `active BOOLEAN NOT NULL DEFAULT TRUE`

### 2.5 Engine, charset e versionamento

Le tabelle runtime MySQL usano esplicitamente:

- `ENGINE=InnoDB`;
- charset `utf8mb4`;
- collation `utf8mb4_0900_ai_ci`;
- foreign key con `ON UPDATE RESTRICT` e `ON DELETE RESTRICT`.

Flyway governa esclusivamente le nove tabelle runtime della sezione 3. La V1 riproduce lo schema legacy runtime; la V2 converge al contratto canonico iniziale; `V3_1`–`V3_9` ampliano a microsecondi le colonne. La V4 Java converte i valori legacy `Europe/Rome` in UTC dopo controlli completi su schema, InnoDB, precisione, gap/overlap, conteggi e digest. Essendo MySQL-specifica, la V4 verifica la precisione tramite `information_schema.COLUMNS.DATETIME_PRECISION`: `DatabaseMetaData.DECIMAL_DIGITS` non è autoritativo perché Connector/J può restituirlo nullo anche per `DATETIME(6)`. Le `V5_1`–`V5_9` rimuovono default e `ON UPDATE`; gli audit mappati restano `NOT NULL`, mentre i quattro timestamp ombra dei profili diventano nullable e congelati. Sugli ambienti MySQL Hibernate usa `ddl-auto=validate`.

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
- `profile_image_url` `VARCHAR(500)` nullable
- `role` e `account_status` `VARCHAR(50)`

### Note
Questa tabella contiene i campi comuni a tutti gli utenti.

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

## 3.7 `availability_slots`

Slot di disponibilità dei professionisti.

### Colonne principali

- `id`
- `professional_id`
- `start_date_time`
- `end_date_time`
- `status`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `professional_id` → `professional_profiles(id)`

### Vincoli principali

- `professional_id` `NOT NULL`
- `start_date_time` `NOT NULL`
- `end_date_time` `NOT NULL`
- `status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Stati gestiti

- `AVAILABLE`
- `BLOCKED`
- `BOOKED`

### Note

Regole applicative attualmente implementate:

- gestione riservata ai professionisti `PERSONAL_TRAINER`;
- intervallo valido e data iniziale futura in creazione o aggiornamento;
- assenza di sovrapposizioni tra slot attivi dello stesso professionista;
- protezione da overlap concorrenti tramite lock pessimista sul `ProfessionalProfile`;
- esclusione dalla lettura cliente degli slot scaduti o con richiesta booking `PENDING` attiva;
- divieto di modifica o blocco manuale dello slot con richiesta booking `PENDING` attiva;
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

### Stati gestiti

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Note

Nel codice attuale la richiesta booking viene creata a partire da un singolo `availabilitySlotId`.

La presenza della tabella `booking_request_items` mantiene il modello estendibile a più slot in futuro, ma l’API attuale lavora su una richiesta single-slot.

Regole applicative attualmente implementate:

- booking consentito solo tra cliente e professionista collegati;
- professionista proprietario dello slot necessariamente `PERSONAL_TRAINER`;
- slot attivo, `AVAILABLE` e non scaduto;
- assenza di una seconda richiesta `PENDING` attiva sullo stesso slot;
- `note` facoltativa, normalizzata e limitata a `1000` caratteri;
- booking `PENDING` che riserva logicamente lo slot rispetto a esposizione cliente, modifica e blocco manuale;
- conferma consentita solo se lo slot è ancora disponibile, futuro e coerente con la specializzazione prevista;
- protezione delle transizioni tramite lock pessimista;
- conservazione dell’intervallo temporale originario dello slot anche dopo `REJECTED` o `CANCELLED`.
- V6 esegue il backfill dei display name dai profili correnti dopo preflight: per il legacy non sono una prova del nome originario;
- V6 usa `updated_at` solo per il timestamp dello stato finale legacy e non inferisce stati intermedi non ricostruibili.

---

## 3.9 `booking_request_items`

Dettaglio degli slot collegati a una richiesta booking.

### Colonne principali

- `id`
- `booking_request_id`
- `availability_slot_id`
- `scheduled_start`
- `scheduled_end`
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
- `updated_at DATETIME(6) NOT NULL` dopo il backfill conservativo della V2

### Note

Nel backend attuale ogni booking creato tramite API contiene un solo item.

`scheduled_start` e `scheduled_end` sono snapshot UTC dell'intervallo al momento della prenotazione e non vengono aggiornati se lo slot cambia. V6 ricostruisce i valori legacy dallo slot referenziato solo dopo averne verificato esistenza, ordine e precisione microsecondi; il migration fallisce se il backfill non è deterministico.

`DATETIME(6)` è il contratto canonico temporaneo di `updated_at` per preservare esattamente i valori legacy non nulli. La V2 valorizza soltanto gli eventuali null; la definizione di una precisione temporale globale resta rinviata all'intervento CM-05.

La tabella collega la richiesta allo slot availability selezionato e consente al service layer di:

- verificare l’assenza di richieste `PENDING` attive sullo stesso slot;
- escludere dalla lettura cliente gli slot con richiesta `PENDING` attiva;
- impedire modifica o blocco manuale dello slot mentre una richiesta è in attesa;
- impedire la ripianificazione temporale di uno slot già coinvolto in una richiesta booking;
- aggiornare coerentemente lo stato dello slot durante conferma o cancellazione booking.

---

## 4. Tabelle già presenti nel database ma non ancora integrate nel codice

Queste tabelle possono già essere presenti nel database MySQL locale come preparazione ai moduli successivi, ma **al momento non risultano ancora integrate nei flussi runtime del backend attuale e non sono governate da Flyway**.

Il perimetro legacy non governato comprende tredici tabelle: `refresh_tokens`, `password_reset_tokens`, `workout_plans`, `workout_weeks`, `workout_days`, `workout_exercises`, `workout_feedbacks`, `nutrition_plans`, `nutrition_weeks`, `nutrition_days`, `nutrition_entries`, `nutrition_feedbacks` e `client_measurements`. Le migrazioni runtime non le creano, non le modificano e non le eliminano.

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
Nel backend attuale il refresh token:
- viene generato
- viene restituito nella risposta di login
- **non viene ancora persistito e gestito tramite questa tabella**

---

## 4.2 `password_reset_tokens`

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
- `expires_at` `NOT NULL`
- `used` `NOT NULL DEFAULT FALSE`

### Nota importante
Questa tabella può già essere presente nel database, ma il flusso di forgot/reset password **non è ancora implementato nel backend attuale**.

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
- `password_reset_tokens.user_id` → `users.id`

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
- coppia `booking_request_items(booking_request_id, availability_slot_id)` tramite `uk_booking_request_items_request_slot`

## 9.2 Tabelle già presenti nel DB ma non ancora integrate
- `refresh_tokens.token`
- `password_reset_tokens.token`

## 9.3 Da gestire a livello business/service

### Area utenti e collegamenti

- massimo 3 professionisti attivi per cliente;
- un solo collegamento attivo per coppia professionista-cliente;
- divieto di auto-collegamento.

### Area availability

- gestione slot riservata ai professionisti `PERSONAL_TRAINER`;
- intervallo temporale valido e data iniziale futura in creazione o aggiornamento;
- nessuna sovrapposizione tra slot attivi dello stesso professionista;
- esclusione dalla lettura cliente degli slot scaduti o con booking `PENDING` attivo;
- impossibilità di modificare o bloccare manualmente uno slot con booking `PENDING` attivo;
- immutabilità dell’intervallo temporale di uno slot già coinvolto in una richiesta booking;
- obbligo di creare un nuovo slot per proporre un intervallo diverso dopo uno storico booking.

### Area booking

- booking consentito solo tra cliente e professionista collegati;
- booking consentito solo su slot appartenenti a un `PERSONAL_TRAINER`;
- booking consentito solo su slot attivi, disponibili e futuri;
- una sola richiesta `PENDING` attiva per slot;
- nota facoltativa, normalizzata e limitata a `1000` caratteri;
- transizioni booking consentite:
  - `PENDING -> CONFIRMED`;
  - `PENDING -> REJECTED`;
  - `PENDING -> CANCELLED`;
  - `CONFIRMED -> CANCELLED`;
- conferma booking consentita solo se lo slot è ancora valido e prenotabile;
- sincronizzazione coerente tra stato booking e stato slot;
- preservazione dell’intervallo temporale originario dello slot nello storico del booking.

### Protezione da concorrenza

- lock pessimista sul professionista durante creazione e aggiornamento availability;
- lock pessimista sullo slot durante creazione booking;
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
22. `V6__add_booking_historical_snapshots`.

Le risorse sono 22 in totale: V1, V2, nove V3, V4, nove V5 e V6.

La V1 crea esclusivamente le nove tabelle runtime, con PK, FK restrittive, unique, nullability, default, precisioni, engine, charset, collation e indici dello schema legacy. Non contiene dati applicativi.

La V2:

- porta `client_profiles.primary_goal` da `VARCHAR(150)` a `VARCHAR(255)`;
- valorizza esclusivamente gli eventuali `booking_request_items.updated_at` nulli usando `created_at` o, in fallback, `CURRENT_TIMESTAMP(6)`;
- porta `booking_request_items.updated_at` a `DATETIME(6) NOT NULL`, preservando esattamente i valori legacy già presenti e mantenendo default e aggiornamento automatico a precisione 6;
- aggiunge quattro indici composti motivati dalle query runtime.

Le nove V3 contengono esclusivamente un `ALTER TABLE` ciascuna. Portano a `DATETIME(6)` i timestamp della rispettiva tabella, preservando nullability, default e aggiornamento automatico. `client_profiles.birth_date` resta `DATE`; `booking_request_items.updated_at`, già definito a microsecondi dalla V2, non viene alterato nuovamente.

Il passaggio strutturale delle sole V3 da `DATETIME(0)` a `DATETIME(6)` mantiene invariati anno, mese, giorno, ora, minuto e secondo e aggiunge una frazione zero. Le V3 non usano `CONVERT_TZ` e non convertono i valori da `Europe/Rome` a UTC: questa responsabilità appartiene alla successiva V4, mentre le V5 trasferiscono l'ownership degli audit all'applicazione.

La V4 Java verifica schema, precisione, gap/overlap e dati prima di convertire i datetime legacy `Europe/Rome` verso UTC. Le V5 rimuovono default e `ON UPDATE` dagli audit, trasferendone l'ownership a Spring Data JPA; i timestamp ombra dei profili diventano nullable e congelati. La V6 Java aggiunge gli snapshot storici Booking e ne esegue il backfill dopo preflight, senza inventare dati o orari.

### 12.2 Indici di convergenza

- `invite_codes(professional_id, created_at)` supporta la lista inviti del professionista ordinata per creazione.
- `availability_slots(professional_id, active, status, start_date_time)` supporta gli slot visibili al cliente filtrati per stato e ordinati temporalmente.
- `booking_requests(client_id, active, created_at)` supporta le liste booking del cliente.
- `booking_requests(professional_id, active, created_at)` supporta le liste booking del professionista.

Gli indici legacy con prefissi parzialmente sovrapposti vengono preservati nella prima adozione per mantenere la migrazione non distruttiva. Un'eventuale rimozione richiederà dati rappresentativi, analisi `EXPLAIN` e una migrazione separata.

Non viene introdotta una unique su `professional_client_links(professional_id, client_id, active)`: impedirebbe di conservare più record storici inattivi della stessa coppia.

### 12.3 Validazione conclusiva MySQL 8

Il 16 luglio 2026 la validazione conclusiva su MySQL 8.0.44 ha prodotto il verdetto **MYSQL VALIDATION PASSED WITH WARNINGS**. Sono stati creati nuovi i due schemi isolati:

- `support_trainer_audit_empty_20260716_101232`;
- `support_trainer_audit_legacy_20260716_101232`.

Entrambi usano charset `utf8mb4` e collation `utf8mb4_0900_ai_ci`. Sono rimasti presenti al termine e non devono essere eliminati senza autorizzazione. Il database originale `support_trainer` non è stato interrogato o modificato.

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

### 12.6 Database esistente e rischio V2

Non è ammessa la baseline automatica. Prima di registrare manualmente uno schema esistente alla versione 1 sono obbligatori backup, clone isolato, confronto con la V1, verifica dei dati e approvazione esplicita.

V2 risulta modificata storicamente. Il checksum corrente `-602898647`, verificato sui due nuovi schemi, non dimostra la compatibilità con una `flyway_schema_history` reale che contenga una variante precedente. Prima di migrare o avviare il backend aggiornato sul database originale è obbligatorio controllare, con autorizzazione dedicata, la sua eventuale history. Questa validazione non ha accertato se il database originale possieda o meno `flyway_schema_history`.

Non usare `flyway repair`, modifiche manuali della history o l'avvio del backend aggiornato sul database originale prima del controllo. `baseline-on-migrate` deve rimanere `false`. Flyway `clean` è vietato sugli ambienti persistenti ed è disabilitato dalla configurazione. Le tabelle legacy future restano fuori dalla history finché i relativi moduli non saranno progettati e approvati.

### 12.7 Immutabilità e rollback

Tutte le risorse nella baseline corrente devono rimanere immutabili dopo la loro applicazione; le correzioni future usano nuove migrazioni forward-only. La modifica storica di V2 è un'eccezione già avvenuta e richiede il controllo della history prima di migrare ambienti esistenti. V4 contiene DML transazionale e non effettua commit manuali; ogni V5 circoscrive il proprio DDL. Il recupero resta basato su backup verificato e ripristino controllato, non sulla cancellazione automatica dello schema.
