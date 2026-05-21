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

### 2.4 Soft delete
Per alcune tabelle principali si usa:
- `active BOOLEAN NOT NULL DEFAULT TRUE`

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

### Chiavi
- `id` → PK e FK verso `users(id)`

### Vincoli principali
- `specialization` `NOT NULL`
- `operational_status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

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

### Chiavi
- `id` → PK e FK verso `users(id)`

### Vincoli principali
- `operational_status` `NOT NULL`
- `birth_date` `NOT NULL`
- `height_cm` `NOT NULL`
- `primary_goal` `NOT NULL`
- `gender` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

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

La regola “niente sovrapposizione slot” è gestita dalla business logic nel service layer.

---

## 3.8 `booking_requests`

Richieste di prenotazione create dai clienti.

### Colonne principali

- `id`
- `client_id`
- `professional_id`
- `status`
- `note`
- `active`
- `created_at`
- `updated_at`

### Foreign key

- `client_id` → `client_profiles(id)`
- `professional_id` → `professional_profiles(id)`

### Vincoli principali

- `client_id` `NOT NULL`
- `professional_id` `NOT NULL`
- `status` `NOT NULL`
- `active` `NOT NULL DEFAULT TRUE`

### Stati gestiti

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Note

Nel codice attuale la richiesta booking viene creata a partire da un singolo `availabilitySlotId`.

La presenza della tabella `booking_request_items` mantiene il modello estendibile a più slot in futuro, ma l’API attuale lavora su una richiesta single-slot.

---

## 3.9 `booking_request_items`

Dettaglio degli slot collegati a una richiesta booking.

### Colonne principali

- `id`
- `booking_request_id`
- `availability_slot_id`
- `created_at`
- `updated_at`

### Foreign key

- `booking_request_id` → `booking_requests(id)`
- `availability_slot_id` → `availability_slots(id)`

### Vincoli principali

- `booking_request_id` `NOT NULL`
- `availability_slot_id` `NOT NULL`

### Note

Nel backend attuale ogni booking creato tramite API contiene un solo item.

---

## 4. Tabelle già presenti nel database ma non ancora integrate nel codice

Queste tabelle possono già essere presenti nel database MySQL locale come preparazione ai moduli successivi, ma **al momento non risultano ancora integrate nei flussi runtime del backend attuale**.

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

## 5 Schema pianificato per moduli futuri

Le tabelle seguenti appartengono alla roadmap progettuale, ma **non sono ancora da considerare integrate nel backend attuale**.

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

## 9.1 Da confermare come vincoli SQL nello schema attualmente integrato
- `users.email`
- `invite_codes.code`
- `email_verification_tokens.token`

## 9.2 Tabelle già presenti nel DB ma non ancora integrate
- `refresh_tokens.token`
- `password_reset_tokens.token`

## 9.3 Da gestire a livello business/service
- massimo 3 professionisti attivi per cliente
- un solo collegamento attivo per coppia professionista-cliente
- nessuna sovrapposizione slot per lo stesso professionista
- uno slot non può essere confermato due volte
- un booking può essere confermato, rifiutato o cancellato solo secondo le transizioni di stato consentite

## 9.4 Vincoli futuri previsti
Quando i relativi moduli verranno implementati, andranno gestiti anche:
- una sola scheda workout attiva per coppia professionista-cliente
- un solo piano nutrizione attivo per coppia professionista-cliente

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
- collegamenti disattivati
- codici invito usati o scaduti
- token usati o scaduti, se mantenuti
- dati futuri storici quando i relativi moduli verranno implementati

### 10.4 Regola di lettura del documento
Questo documento va sempre letto distinguendo tra:
- tabelle già supportate dal backend attuale
- tabelle già presenti nel DB ma non ancora usate dal codice
- tabelle solo pianificate per i moduli futuri

---

## 11. Decisioni confermate
Per Support Trainer si confermano le seguenti scelte:

- naming SQL in snake_case
- tabelle plurali
- PK `BIGINT AUTO_INCREMENT`
- immagine profilo salvata come URL/path
- vincoli logici più complessi gestiti a livello business/service
- separazione chiara tra schema attuale e schema futuro
- tabelle token documentate distinguendo tra uso reale e preparazione tecnica