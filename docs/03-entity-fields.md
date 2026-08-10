# Entity Fields — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce i campi principali delle entità del dominio con un livello di dettaglio intermedio tra analisi funzionale e modello tecnico.

Per ogni campo vengono indicati:
- nome
- tipo logico suggerito
- obbligatorietà
- nullable
- default, se presente
- note e vincoli rilevanti

---

## 2. Convenzioni generali

### 2.1 Identificativi
Per la v1, tutti gli identificativi possono essere modellati come:

- `id` → `Long`, obbligatorio, `nullable = false`

### 2.2 Timestamp
Dove presenti:
- `createdAt` → `Instant`
- `updatedAt` → `Instant`

### 2.3 Campi decimali
Per pesi, misure e valori numerici con decimali si consiglia:
- `BigDecimal`

### 2.4 Enum
Per campi con valori chiusi si consiglia:
- `Enum`

### 2.5 Regola pratica
- `nullable = false` per i campi davvero essenziali
- `nullable = true` per campi facoltativi, descrittivi o compilabili in un secondo momento

---

# 3. Entità e campi

## 3.0 Stato di implementazione

### Entità attualmente implementate nel backend

- `User`
- `ProfessionalProfile`
- `ClientProfile`
- `ProfessionalClientLink`
- `InviteCode`
- `EmailVerificationToken`
- `AvailabilitySlot`
- `BookingRequest`
- `BookingRequestItem`

### Entità pianificate ma non ancora implementate

- `ClientMeasurement`
- `WorkoutPlan`
- `WorkoutWeek`
- `WorkoutDay`
- `WorkoutExercise`
- `NutritionPlan`
- `NutritionWeek`
- `NutritionDay`
- `NutritionEntry`
- `WorkoutFeedback`
- `NutritionFeedback`

Le sezioni dedicate alle entità pianificate descrivono ipotesi di dominio futuro e non componenti già presenti nel codice reale.

## 3.1 User (astratta)

| Campo             | Tipo            | Obbligatorio | Nullable | Default                | Note                                             |
| ----------------- | --------------- | -----------: | -------: | ---------------------- | ------------------------------------------------ |
| `id`              | `Long`          |           Sì |       No | —                      | Identificativo univoco                           |
| `firstName`       | `String`        |           Sì |       No | —                      | Nome utente                                      |
| `lastName`        | `String`        |           Sì |       No | —                      | Cognome utente                                   |
| `email`           | `String`        |           Sì |       No | —                      | Univoca, da salvare preferibilmente normalizzata |
| `password`        | `String`        |           Sì |       No | —                      | Password hashata, mai in chiaro                  |
| `profileImageUrl` | `String`        |           No |       Sì | `null`                 | URL/path immagine profilo                        |
| `role`            | `Enum`          |           Sì |       No | —                      | `CLIENT`, `PROFESSIONAL`                         |
| `accountStatus`   | `Enum`          |           Sì |       No | `PENDING_VERIFICATION` | Stato account                                    |
| `emailVerified`   | `Boolean`       |           Sì |       No | `false`                | Verifica email completata o no                   |
| `createdAt`       | `Instant`       |           Sì |       No | audit app              | Istante UTC di creazione                         |
| `updatedAt`       | `Instant`       |           Sì |       No | audit app              | Istante UTC ultimo aggiornamento                 |

### Note
- `email` deve essere **univoca**
- `password` deve contenere il valore **criptato/hashato**
- `accountStatus` è diverso da `operationalStatus`
- per le nuove registrazioni entrambi i ruoli mantengono i default `PENDING_VERIFICATION` ed `emailVerified=false` fino alla conferma email
- i clienti già persistiti come `ACTIVE` e verificati non sono modificati da questa regola

---

## 3.2 ProfessionalProfile

| Campo               | Tipo              | Obbligatorio | Nullable | Default       | Note                               |
| ------------------- | ----------------- | -----------: | -------: | ------------- | ---------------------------------- |
| `specialization`    | `Enum`            |           Sì |       No | —             | `PERSONAL_TRAINER`, `NUTRITIONIST` |
| `operationalStatus` | `Enum`            |           Sì |       No | `DISPONIBILE` | Stato operativo del professionista |
| `phoneNumber`       | `String`          |           No |       Sì | `null`        | Facoltativo                        |
| `bio`               | `String` / `Text` |           No |       Sì | `null`        | Descrizione breve profilo          |
| `workplaceName`     | `String`          |           No |       Sì | `null`        | Nome palestra/studio/attività      |
| `city`              | `String`          |           No |       Sì | `null`        | Città principale di lavoro         |
| `instagramUrl`      | `String`          |           No |       Sì | `null`        | Link profilo Instagram             |
| `websiteUrl`        | `String`          |           No |       Sì | `null`        | Link sito web                      |
| `active`            | `Boolean`         |           Sì |       No | `true`        | Flag logico di attivazione         |

### Note
- `active` serve come flag logico interno
- `operationalStatus` valori iniziali:
  - `DISPONIBILE`
  - `ASSENTE`
  - `FERIE`
  - `MALATTIA`

---

## 3.3 ClientProfile

| Campo               | Tipo              | Obbligatorio | Nullable | Default  | Note                             |
| ------------------- | ----------------- | -----------: | -------: | -------- | -------------------------------- |
| `operationalStatus` | `Enum`            |           Sì |       No | `ATTIVO` | Stato operativo cliente          |
| `birthDate`         | `LocalDate`       |           Sì |       No | —        | Data di nascita                  |
| `heightCm`          | `BigDecimal`      |           Sì |       No | —        | Altezza in cm                    |
| `primaryGoal`       | `String`          |           Sì |       No | —        | Obiettivo principale del cliente |
| `gender`            | `Enum`            |           Sì |       No | —        | Genere dichiarato                |
| `medicalNotes`      | `String` / `Text` |           No |       Sì | `null`   | Note mediche rilevanti           |
| `injuryNotes`       | `String` / `Text` |           No |       Sì | `null`   | Infortuni, limitazioni, recuperi |
| `notes`             | `String` / `Text` |           No |       Sì | `null`   | Note generali cliente            |
| `active`            | `Boolean`         |           Sì |       No | `true`   | Flag logico di attivazione       |

### Note
- `operationalStatus` valori iniziali:
  - `ATTIVO`
  - `INFORTUNATO`
  - `PAUSA`
- `gender` può avere valori iniziali:
  - `MALE`
  - `FEMALE`
  - `OTHER`
  - `NOT_SPECIFIED`
- `primaryGoal` per la v1 può restare testo libero

### Contratto pubblico minimizzato dei dati cliente

La struttura persistita di `ClientProfile` non coincide con il profilo condiviso ai professionisti.

- il profilo owner ottenuto tramite `/api/v1/me/profile` resta un contratto separato e comprende i campi CLIENT previsti per la gestione del proprio profilo;
- `GET /api/v1/clients/my` espone soltanto `id`, `firstName`, `lastName` e `profileImageUrl`;
- `GET /api/v1/clients/{clientId}` espone gli stessi campi e aggiunge `primaryGoal`, `operationalStatus`, `birthDate`, `heightCm` e `gender`;
- `medicalNotes`, `injuryNotes`, `notes`, `active`, dati account, dati tecnici del collegamento e audit restano fuori dai DTO Clients condivisi;
- `PERSONAL_TRAINER` e `NUTRITIONIST` usano lo stesso contratto condiviso.

Le note sensibili sono persistite nel profilo owner ma non sono condivise dal contratto corrente. Una loro eventuale condivisione futura richiede una decisione dedicata su finalità, visibilità e protezioni.

Il cliente proprietario continua a leggere i dati completi tramite `/api/v1/me/profile` e `/api/v1/me/account`. La minimizzazione riguarda soltanto la risposta HTTP condivisa e non modifica entity, colonne o dati già persistiti. Eventuali sezioni specialistiche future richiederanno uno scopo e una policy dedicati.

---

## 3.4 ClientMeasurement

| Campo               | Tipo              | Obbligatorio | Nullable | Default | Note                                   |
| ------------------- | ----------------- | -----------: | -------: | ------- | -------------------------------------- |
| `id`                | `Long`            |           Sì |       No | —       | Identificativo univoco                 |
| `client`            | `ClientProfile`   |           Sì |       No | —       | Cliente proprietario della misurazione |
| `recordedAt`        | `LocalDateTime`   |           Sì |       No | auto    | Data/ora registrazione                 |
| `weightKg`          | `BigDecimal`      |           Sì |       No | —       | Peso corporeo                          |
| `bodyFatPercentage` | `BigDecimal`      |           No |       Sì | `null`  | Percentuale massa grassa               |
| `muscleMassKg`      | `BigDecimal`      |           No |       Sì | `null`  | Massa muscolare stimata                |
| `waistCm`           | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza vita                     |
| `chestCm`           | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza petto                    |
| `hipsCm`            | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza fianchi                  |
| `armCm`             | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza braccio                  |
| `thighCm`           | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza coscia                   |
| `shouldersCm`       | `BigDecimal`      |           No |       Sì | `null`  | Circonferenza/spalle                   |
| `notes`             | `String` / `Text` |           No |       Sì | `null`  | Note rilevazione                       |

### Note
- Questa entità va storicizzata, non sovrascritta
- Eventuali indicatori derivati come BMI possono essere calcolati dal backend

---

## 3.5 ProfessionalClientLink

| Campo          | Tipo                  | Obbligatorio | Nullable | Default | Note                                            |
| -------------- | --------------------- | -----------: | -------: | ------- | ----------------------------------------------- |
| `id`           | `Long`                |           Sì |       No | —       | Identificativo univoco                          |
| `professional` | `ProfessionalProfile` |           Sì |       No | —       | Professionista collegato                        |
| `client`       | `ClientProfile`       |           Sì |       No | —       | Cliente collegato                               |
| `createdAt`    | `Instant`             |           Sì |       No | audit app | Data collegamento UTC                           |
| `updatedAt`    | `Instant`             |           Sì |       No | audit app | Istante UTC ultimo aggiornamento                |
| `active`       | `Boolean`             |           Sì |       No | `true`  | Collegamento attivo/disattivato                 |

### Note
- Va previsto un vincolo logico per evitare duplicati dello stesso collegamento
- Un cliente può avere massimo **3** professionisti attivi

---

## 3.6 InviteCode

| Campo          | Tipo                  | Obbligatorio | Nullable | Default | Note                                             |
| -------------- | --------------------- | -----------: | -------: | ------- | ------------------------------------------------ |
| `id`           | `Long`                |           Sì |       No | —       | Identificativo univoco                           |
| `code`         | `String`              |           Sì |       No | —       | Codice invito univoco                            |
| `professional` | `ProfessionalProfile` |           Sì |       No | —       | Professionista che genera il codice              |
| `expiresAt`    | `Instant`             |           Sì |       No | —       | Scadenza UTC dopo 168 ore reali                  |
| `used`         | `Boolean`             |           Sì |       No | `false` | Codice già usato o no                            |
| `active`       | `Boolean`             |           Sì |       No | `true`  | Flag logico di attivazione/disattivazione codice |
| `updatedAt`    | `Instant`             |           Sì |       No | audit app | Istante UTC ultimo aggiornamento                |
| `usedAt`       | `Instant`             |           No |       Sì | `null`  | Istante UTC di utilizzo                          |
| `createdAt`    | `Instant`             |           Sì |       No | audit app | Istante UTC di creazione                        |

### Note
- `code` deve essere **univoco**
- Monouso fisso
- Può essere generato solo da un professionista con:
  - `accountStatus = ACTIVE`
  - `emailVerified = true`
  - profilo `active = true`

---

## 3.6.1 EmailVerificationToken

| Campo       | Tipo            | Obbligatorio | Nullable | Default | Note                               |
| ----------- | --------------- | -----------: | -------: | ------- | ---------------------------------- |
| `id`        | `Long`          |           Sì |       No | auto    | Identificativo univoco             |
| `user`      | `User`          |           Sì |       No | —       | Utente destinatario della verifica |
| `token`     | `String`        |           Sì |       No | —       | Token univoco di verifica email    |
| `expiresAt` | `Instant`       |           Sì |       No | —       | Scadenza UTC dopo 24 ore reali     |
| `used`      | `Boolean`       |           Sì |       No | `false` | Token consumato o invalidato       |
| `usedAt`    | `Instant`       |           No |       Sì | `null`  | Istante UTC di uso o invalidazione |
| `createdAt` | `Instant`       |           Sì |       No | audit app | Istante UTC di creazione         |

### Note

- `token` è univoco;
- professionisti e clienti ricevono lo stesso tipo di token, con durata di 24 ore reali;
- il primo consumo valido attiva l'account e valorizza `usedAt`;
- un consumo successivo è idempotente soltanto quando token, stato account, verifica email e profilo restano coerenti;
- un reinvio consentito marca `used=true` e `usedAt=now` su tutti i precedenti token non usati, senza modificare quelli già usati;
- l'uso di `used` per la revoca è un limite semantico dell'attuale schema, che non dispone di `revokedAt` o `active`;
- dopo il reinvio resta un solo token non usato, con nuova durata di 24 ore;
- il token scade quando `expiresAt <= now`;
- questa entity non eredita da `BaseEntity`;
- non contiene `updatedAt`.

---

## 3.7 AvailabilitySlot

| Campo           | Tipo                  | Obbligatorio | Nullable | Default     | Note                                   |
| --------------- | --------------------- | -----------: | -------: | ----------- | -------------------------------------- |
| `id`            | `Long`                |           Sì |       No | auto        | Identificativo univoco                 |
| `professional`  | `ProfessionalProfile` |           Sì |       No | —           | Professionista proprietario dello slot |
| `startDateTime` | `Instant`             |           Sì |       No | —           | Inizio slot persistito UTC             |
| `endDateTime`   | `Instant`             |           Sì |       No | —           | Fine slot persistita UTC               |
| `status`        | `Enum`                |           Sì |       No | `AVAILABLE` | Stato slot                             |
| `active`        | `Boolean`             |           Sì |       No | `true`      | Flag logico di attivazione             |
| `createdAt`     | `Instant`             |           Sì |       No | audit app   | Istante UTC creazione                  |
| `updatedAt`     | `Instant`             |           Sì |       No | audit app   | Istante UTC ultimo aggiornamento       |

### Note

- `status` valori implementati:
  - `AVAILABLE`
  - `BOOKED`
  - `BLOCKED`
- `endDateTime` deve essere successivo a `startDateTime`;
- in creazione o aggiornamento, `startDateTime` deve essere nel futuro;
- gli slot attivi dello stesso professionista non possono sovrapporsi;
- la gestione degli slot è prevista per professionisti `PERSONAL_TRAINER`;
- lo stato `BOOKED` viene utilizzato dal modulo Bookings.

---

## 3.8 BookingRequest

| Campo          | Tipo                       | Obbligatorio | Nullable | Default     | Note                           |
| -------------- | -------------------------- | -----------: | -------: | ----------- | ------------------------------ |
| `id`           | `Long`                     |           Sì |       No | auto        | Identificativo univoco         |
| `client`       | `ClientProfile`            |           Sì |       No | —           | Cliente richiedente            |
| `professional` | `ProfessionalProfile`      |           Sì |       No | —           | Professionista destinatario    |
| `clientDisplayName` | `String`               |           Sì |       No | —           | Snapshot storico, max 201 caratteri |
| `professionalDisplayName` | `String`         |           Sì |       No | —           | Snapshot storico, max 201 caratteri |
| `status`       | `Enum`                     |           Sì |       No | `PENDING`   | Stato richiesta                |
| `note`         | `String` / `Text`          |           No |       Sì | `null`      | Nota facoltativa del cliente   |
| `active`       | `Boolean`                  |           Sì |       No | `true`      | Flag logico di attivazione     |
| `items`        | `List<BookingRequestItem>` |           Sì |       No | lista vuota | Slot collegati alla richiesta  |
| `createdAt`    | `Instant`                  |           Sì |       No | audit app   | Istante UTC creazione          |
| `updatedAt`    | `Instant`                  |           Sì |       No | audit app   | Istante UTC aggiornamento      |
| `confirmedAt`  | `Instant`                  |           No |       Sì | `null`      | Istante UTC della conferma     |
| `rejectedAt`   | `Instant`                  |           No |       Sì | `null`      | Istante UTC del rifiuto        |
| `cancelledAt`  | `Instant`                  |           No |       Sì | `null`      | Istante UTC dell'annullamento  |

### Note

- `status` valori implementati:
  - `PENDING`
  - `CONFIRMED`
  - `REJECTED`
  - `CANCELLED`
- nel backend attuale una richiesta viene creata a partire da un singolo `availabilitySlotId`;
- la `note`, se presente, viene normalizzata eliminando spazi iniziali e finali;
- una `note` vuota dopo la normalizzazione viene trattata come assente;
- la `note` non può superare `1000` caratteri;
- la struttura con `items` mantiene il modello estendibile a scenari multi-slot futuri.
- i display name sono costruiti al momento della creazione con nome e cognome normalizzati, trim e un solo spazio; non cambiano quando cambia il profilo;
- per i record legacy i display name vengono ricostruiti dai profili correnti durante V6: non provano il nome originario;
- i timestamp di transizione sono assegnati dal clock applicativo; il backfill legacy usa `updatedAt` solo per lo stato finale e non inventa stati intermedi.

---

## 3.9 BookingRequestItem

| Campo              | Tipo               | Obbligatorio | Nullable | Default | Note                           |
| ------------------ | ------------------ | -----------: | -------: | ------- | ------------------------------ |
| `id`               | `Long`             |           Sì |       No | auto    | Identificativo univoco         |
| `bookingRequest`   | `BookingRequest`   |           Sì |       No | —       | Richiesta principale           |
| `availabilitySlot` | `AvailabilitySlot` |           Sì |       No | —       | Slot richiesto                 |
| `scheduledStart`   | `Instant`          |           Sì |       No | —       | Snapshot UTC dell'inizio       |
| `scheduledEnd`     | `Instant`          |           Sì |       No | —       | Snapshot UTC della fine        |
| `createdAt`        | `Instant`          |           Sì |       No | audit app | Istante UTC creazione          |
| `updatedAt`        | `Instant`          |           Sì |       No | audit app | Istante UTC aggiornamento      |

### Note

- ogni item punta a uno slot specifico;
- gli orari snapshot sono `DATETIME(6)` in UTC e restano la fonte autorevole per lo storico Booking;
- nel contratto API attuale ogni booking creato contiene un solo item;
- il modello resta predisposto per un’eventuale evoluzione multi-slot futura.

---

## 3.10 WorkoutPlan

| Campo            | Tipo                  | Obbligatorio | Nullable | Default | Note                                 |
| ---------------- | --------------------- | -----------: | -------: | ------- | ------------------------------------ |
| `id`             | `Long`                |           Sì |       No | —       | Identificativo univoco               |
| `professional`   | `ProfessionalProfile` |           Sì |       No | —       | Solo PT                              |
| `client`         | `ClientProfile`       |           Sì |       No | —       | Cliente destinatario                 |
| `title`          | `String`              |           Sì |       No | —       | Titolo scheda                        |
| `monthReference` | `LocalDate`           |           Sì |       No | —       | Primo giorno del mese di riferimento |
| `createdAt`      | `LocalDateTime`       |           Sì |       No | auto    | Data creazione                       |
| `updatedAt`      | `LocalDateTime`       |           Sì |       No | auto    | Data aggiornamento                   |
| `active`         | `Boolean`             |           Sì |       No | `true`  | Scheda attiva o archiviata           |

### Note
- `monthReference` può rappresentare il primo giorno del mese, es. `2026-03-01`

---

## 3.11 WorkoutWeek

| Campo         | Tipo          | Obbligatorio | Nullable | Default | Note                   |
| ------------- | ------------- | -----------: | -------: | ------- | ---------------------- |
| `id`          | `Long`        |           Sì |       No | —       | Identificativo univoco |
| `workoutPlan` | `WorkoutPlan` |           Sì |       No | —       | Scheda proprietaria    |
| `weekNumber`  | `Integer`     |           Sì |       No | —       | Da 1 a 4               |

### Note
- Per la v1 si assume struttura mensile in 4 settimane

---

## 3.12 WorkoutDay

| Campo         | Tipo              | Obbligatorio | Nullable | Default | Note                   |
| ------------- | ----------------- | -----------: | -------: | ------- | ---------------------- |
| `id`          | `Long`            |           Sì |       No | —       | Identificativo univoco |
| `workoutWeek` | `WorkoutWeek`     |           Sì |       No | —       | Settimana proprietaria |
| `date`        | `LocalDate`       |           Sì |       No | —       | Data reale del giorno  |
| `dayLabel`    | `String`          |           Sì |       No | —       | Es. Lunedì, Martedì    |
| `dayType`     | `Enum`            |           Sì |       No | `REST`  | Tipo giorno            |
| `notes`       | `String` / `Text` |           No |       Sì | `null`  | Note del giorno        |

### Note
- `dayType` valori iniziali:
  - `REST`
  - `WORKOUT`

---

## 3.13 WorkoutExercise

| Campo             | Tipo              | Obbligatorio | Nullable | Default | Note                                |
| ----------------- | ----------------- | -----------: | -------: | ------- | ----------------------------------- |
| `id`              | `Long`            |           Sì |       No | —       | Identificativo univoco              |
| `workoutDay`      | `WorkoutDay`      |           Sì |       No | —       | Giorno di appartenenza              |
| `exerciseName`    | `String`          |           Sì |       No | —       | Nome esercizio                      |
| `sets`            | `String`          |           Sì |       No | —       | Es. `4` o `4x`                      |
| `reps`            | `String`          |           Sì |       No | —       | Es. `10`, `8-10`, `max`             |
| `intensity`       | `String`          |           No |       Sì | `null`  | Intensità o RPE                     |
| `recoveryTime`    | `String`          |           No |       Sì | `null`  | Recupero tra le serie               |
| `extraTechniques` | `String` / `Text` |           No |       Sì | `null`  | Superset, drop set, rest pause ecc. |
| `description`     | `String` / `Text` |           No |       Sì | `null`  | Spiegazioni esercizio               |
| `loggedLoad`      | `BigDecimal`      |           No |       Sì | `null`  | Carico registrato, `DECIMAL(6,2)` nello schema legacy futuro |
| `loggedReps`      | `Integer`         |           No |       Sì | `null`  | Ripetizioni registrate nello schema legacy futuro |
| `notes`           | `String` / `Text` |           No |       Sì | `null`  | Note aggiuntive                     |

### Note
- `sets` e `reps` come `String` danno più flessibilità nella v1
- `loggedLoad` e `loggedReps` descrivono valori consuntivi numerici; le relative tabelle non sono ancora governate da Flyway né integrate nel codice runtime

---

## 3.14 NutritionPlan

| Campo            | Tipo                  | Obbligatorio | Nullable | Default | Note                                 |
| ---------------- | --------------------- | -----------: | -------: | ------- | ------------------------------------ |
| `id`             | `Long`                |           Sì |       No | —       | Identificativo univoco               |
| `professional`   | `ProfessionalProfile` |           Sì |       No | —       | Solo nutrizionista                   |
| `client`         | `ClientProfile`       |           Sì |       No | —       | Cliente destinatario                 |
| `title`          | `String`              |           Sì |       No | —       | Titolo piano                         |
| `monthReference` | `LocalDate`           |           Sì |       No | —       | Primo giorno del mese di riferimento |
| `createdAt`      | `LocalDateTime`       |           Sì |       No | auto    | Data creazione                       |
| `updatedAt`      | `LocalDateTime`       |           Sì |       No | auto    | Data aggiornamento                   |
| `active`         | `Boolean`             |           Sì |       No | `true`  | Piano attivo o archiviato            |

---

## 3.15 NutritionWeek

| Campo           | Tipo            | Obbligatorio | Nullable | Default | Note                   |
| --------------- | --------------- | -----------: | -------: | ------- | ---------------------- |
| `id`            | `Long`          |           Sì |       No | —       | Identificativo univoco |
| `nutritionPlan` | `NutritionPlan` |           Sì |       No | —       | Piano proprietario     |
| `weekNumber`    | `Integer`       |           Sì |       No | —       | Da 1 a 4               |

---

## 3.16 NutritionDay

| Campo           | Tipo              | Obbligatorio | Nullable | Default   | Note                   |
| --------------- | ----------------- | -----------: | -------: | --------- | ---------------------- |
| `id`            | `Long`            |           Sì |       No | —         | Identificativo univoco |
| `nutritionWeek` | `NutritionWeek`   |           Sì |       No | —         | Settimana proprietaria |
| `date`          | `LocalDate`       |           Sì |       No | —         | Data reale del giorno  |
| `dayLabel`      | `String`          |           Sì |       No | —         | Es. Lunedì             |
| `dayType`       | `Enum`            |           Sì |       No | `PLANNED` | Tipo giorno            |
| `notes`         | `String` / `Text` |           No |       Sì | `null`    | Note del giorno        |

### Note
- `dayType` valori iniziali:
  - `FREE`
  - `PLANNED`

---

## 3.17 NutritionEntry

| Campo          | Tipo              | Obbligatorio | Nullable | Default | Note                                |
| -------------- | ----------------- | -----------: | -------: | ------- | ----------------------------------- |
| `id`           | `Long`            |           Sì |       No | —       | Identificativo univoco              |
| `nutritionDay` | `NutritionDay`    |           Sì |       No | —       | Giorno proprietario                 |
| `mealType`     | `String`          |           Sì |       No | —       | Colazione, pranzo, cena, snack ecc. |
| `content`      | `String` / `Text` |           Sì |       No | —       | Descrizione contenuto pasto         |
| `quantity`     | `String`          |           No |       Sì | `null`  | Quantità/grammi/porzioni            |
| `notes`        | `String` / `Text` |           No |       Sì | `null`  | Note aggiuntive                     |

### Note
- Per la v1 va bene una struttura flessibile e non troppo rigida

---

## 3.18 WorkoutFeedback

| Campo          | Tipo                  | Obbligatorio | Nullable | Default | Note                          |
| -------------- | --------------------- | -----------: | -------: | ------- | ----------------------------- |
| `id`           | `Long`                |           Sì |       No | —       | Identificativo univoco        |
| `client`       | `ClientProfile`       |           Sì |       No | —       | Cliente che invia             |
| `professional` | `ProfessionalProfile` |           Sì |       No | —       | PT destinatario               |
| `workoutDay`   | `WorkoutDay`          |           Sì |       No | —       | Giorno specifico della scheda |
| `message`      | `String` / `Text`     |           Sì |       No | —       | Messaggio del cliente         |
| `createdAt`    | `LocalDateTime`       |           Sì |       No | auto    | Data invio                    |

### Note
- Riferito solo a contenuti workout

---

## 3.19 NutritionFeedback

| Campo          | Tipo                  | Obbligatorio | Nullable | Default | Note                       |
| -------------- | --------------------- | -----------: | -------: | ------- | -------------------------- |
| `id`           | `Long`                |           Sì |       No | —       | Identificativo univoco     |
| `client`       | `ClientProfile`       |           Sì |       No | —       | Cliente che invia          |
| `professional` | `ProfessionalProfile` |           Sì |       No | —       | Nutrizionista destinatario |
| `nutritionDay` | `NutritionDay`        |           Sì |       No | —       | Giorno specifico del piano |
| `message`      | `String` / `Text`     |           Sì |       No | —       | Messaggio del cliente      |
| `createdAt`    | `LocalDateTime`       |           Sì |       No | auto    | Data invio                 |

### Note
- Riferito solo a contenuti nutrizione

---

# 4. Enum consigliati

## 4.1 Role
- `CLIENT`
- `PROFESSIONAL`

## 4.2 AccountStatus
- `PENDING_VERIFICATION`
- `ACTIVE`

## 4.3 ProfessionalSpecialization
- `PERSONAL_TRAINER`
- `NUTRITIONIST`

## 4.4 ProfessionalOperationalStatus
- `DISPONIBILE`
- `ASSENTE`
- `FERIE`
- `MALATTIA`

## 4.5 ClientOperationalStatus
- `ATTIVO`
- `INFORTUNATO`
- `PAUSA`

## 4.6 AvailabilitySlotStatus
- `AVAILABLE`
- `BOOKED`
- `BLOCKED`

## 4.7 BookingRequestStatus
- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

## 4.8 WorkoutDayType
- `REST`
- `WORKOUT`

## 4.9 NutritionDayType
- `FREE`
- `PLANNED`

## 4.10 Gender
- `MALE`
- `FEMALE`
- `OTHER`
- `NOT_SPECIFIED`

---

# 5. Note di progettazione utili

- `email`, `code` dovranno avere vincolo di unicità
- `ProfessionalClientLink` dovrà evitare duplicati tra stesso professionista e stesso cliente
- `BookingRequest` + `BookingRequestItem` supporta il flusso single-slot attuale e mantiene il modello estendibile per richieste multi-slot future
- `ClientMeasurement` deve restare storica
- `profileImageUrl` è meglio come URL/path e non come file binario nel database
- `sets`, `reps`, `quantity` come `String` nella v1 ti danno più flessibilità e meno attrito

---
