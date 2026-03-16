# Domain Model — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce le principali entità del dominio, le loro responsabilità e le relazioni logiche tra di esse.

Lo scopo è creare una base chiara per:
- progettazione database
- definizione delle entity JPA
- costruzione della business logic
- progettazione delle API REST

---

## 2. Scelte di modellazione principali

### 2.1 Gerarchia utenti
Il sistema prevede una gerarchia con una entità base astratta:

- **User** *(astratta)*
- **ProfessionalProfile**
- **ClientProfile**

### 2.2 Motivazione della scelta
Questa soluzione permette di:
- centralizzare i campi comuni degli utenti
- distinguere chiaramente professionisti e clienti
- aggiungere campi specifici per ogni tipo di utente
- mantenere il modello più ordinato e leggibile

### 2.3 Specializzazione del professionista
Un professionista può avere una specializzazione tra:
- **PERSONAL_TRAINER**
- **NUTRITIONIST**

---

## 3. Entità principali

## 3.1 User (astratta)
Rappresenta la base comune di tutti gli utenti del sistema.

### Campi comuni iniziali
- `id`
- `firstName`
- `lastName`
- `email`
- `password`
- `role`
- `accountStatus`
- `emailVerified`
- `createdAt`
- `updatedAt`

### Note
Campi come `role`, `accountStatus` ed `emailVerified` servono a distinguere:
- permessi di accesso
- stato dell’account
- verifica email completata o meno

---

## 3.2 ProfessionalProfile
Rappresenta un professionista registrato alla piattaforma.

### Eredita da
- `User`

### Campi specifici iniziali
- `specialization`
- `operationalStatus`

### Esempi valori
- `specialization`: `PERSONAL_TRAINER`, `NUTRITIONIST`
- `operationalStatus`: `DISPONIBILE`, `ASSENTE`, `FERIE`, `MALATTIA`

### Responsabilità
Il professionista può:
- invitare clienti
- gestire contenuti professionali
- nel caso del PT, gestire disponibilità e prenotazioni

---

## 3.3 ClientProfile
Rappresenta un cliente registrato alla piattaforma.

### Eredita da
- `User`

### Campi specifici iniziali
- `operationalStatus`

### Esempi valori
- `ATTIVO`
- `INFORTUNATO`
- `PAUSA`

### Responsabilità
Il cliente può:
- collegarsi a professionisti
- visualizzare contenuti assegnati
- inviare richieste di prenotazione al PT
- inviare segnalazioni sui contenuti ricevuti

---

## 3.4 ProfessionalClientLink
Entità intermedia che rappresenta il collegamento tra un professionista e un cliente.

### Campi iniziali
- `id`
- `professional`
- `client`
- `createdAt`
- `active`

### Relazione
- molti collegamenti possono riferirsi a un professionista
- molti collegamenti possono riferirsi a un cliente

### Note di business
- un cliente può avere al massimo **3 professionisti**
- il sistema non deve permettere il collegamento di un professionista a sé stesso come cliente

---

## 3.5 InviteCode
Rappresenta il codice invito generato da un professionista per permettere la registrazione di un cliente.

### Campi iniziali
- `id`
- `code`
- `professional`
- `expiresAt`
- `used`
- `usedAt`
- `createdAt`

### Regole di business
- può essere generato solo da un professionista con account verificato e attivo
- ha una scadenza
- è usabile una sola volta
- il collegamento con il professionista avviene solo dopo la registrazione corretta del cliente

---

## 3.6 AvailabilitySlot
Rappresenta una fascia di disponibilità del personal trainer.

### Campi iniziali
- `id`
- `professional`
- `startDateTime`
- `endDateTime`
- `status`
- `createdAt`

### Note
Questa entità è utilizzata solo per i professionisti con specializzazione:
- `PERSONAL_TRAINER`

### Esempi stato slot
- `AVAILABLE`
- `BOOKED`
- `BLOCKED`

---

## 3.7 BookingRequest
Rappresenta una richiesta di prenotazione inviata da un cliente verso un personal trainer.

### Campi iniziali
- `id`
- `client`
- `professional`
- `status`
- `note`
- `createdAt`
- `updatedAt`

### Esempi stato richiesta
- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Note
Una richiesta di prenotazione appartiene a:
- un solo cliente
- un solo personal trainer

---

## 3.8 BookingRequestItem
Rappresenta il dettaglio di una richiesta di prenotazione composta da uno o più slot.

### Campi iniziali
- `id`
- `bookingRequest`
- `availabilitySlot`

### Motivazione
Questa entità permette di gestire una singola richiesta contenente:
- uno slot singolo
- più slot distribuiti su più giorni

In questo modo il cliente può inviare una richiesta unica che copre più date.

---

## 3.9 WorkoutPlan
Rappresenta una scheda di allenamento mensile assegnata da un personal trainer a un cliente.

### Campi iniziali
- `id`
- `professional`
- `client`
- `title`
- `monthReference`
- `createdAt`
- `updatedAt`
- `active`

### Note
Una scheda appartiene a:
- un personal trainer
- un cliente

---

## 3.10 WorkoutWeek
Rappresenta una settimana interna a una scheda di allenamento.

### Campi iniziali
- `id`
- `workoutPlan`
- `weekNumber`

### Note
Serve a organizzare la scheda in:
- settimana 1
- settimana 2
- settimana 3
- settimana 4

---

## 3.11 WorkoutDay
Rappresenta un singolo giorno della scheda di allenamento.

### Campi iniziali
- `id`
- `workoutWeek`
- `date`
- `dayLabel`
- `dayType`
- `notes`

### Esempi dayType
- `REST`
- `WORKOUT`

### Note
Se il giorno è di tipo `WORKOUT`, il cliente può aprire il dettaglio dell’allenamento.

---

## 3.12 WorkoutExercise
Rappresenta una riga di esercizio associata a un giorno di allenamento.

### Campi iniziali
- `id`
- `workoutDay`
- `exerciseName`
- `sets`
- `reps`
- `intensity`
- `recoveryTime`
- `extraTechniques`
- `description`
- `loggedLoad`
- `loggedReps`
- `notes`

---

## 3.13 NutritionPlan
Rappresenta un piano alimentare mensile assegnato da un nutrizionista a un cliente.

### Campi iniziali
- `id`
- `professional`
- `client`
- `title`
- `monthReference`
- `createdAt`
- `updatedAt`
- `active`

---

## 3.14 NutritionWeek
Rappresenta una settimana interna a un piano alimentare.

### Campi iniziali
- `id`
- `nutritionPlan`
- `weekNumber`

---

## 3.15 NutritionDay
Rappresenta un singolo giorno del piano alimentare.

### Campi iniziali
- `id`
- `nutritionWeek`
- `date`
- `dayLabel`
- `dayType`
- `notes`

### Esempi dayType
- `FREE`
- `PLANNED`

---

## 3.16 NutritionEntry
Rappresenta una riga di contenuto giornaliero del piano alimentare.

### Campi iniziali
- `id`
- `nutritionDay`
- `mealType`
- `content`
- `quantity`
- `notes`

### Note
La struttura esatta dei campi nutrizionali verrà raffinata in un documento successivo.

---

## 3.17 ClientFeedback
Rappresenta una segnalazione inviata dal cliente su un contenuto assegnato.

### Campi iniziali
- `id`
- `client`
- `professional`
- `message`
- `createdAt`

### Collegamenti possibili
La segnalazione deve riferirsi a uno specifico giorno di contenuto.

### Nota progettuale
Poiché workout e piano alimentare sono separati, sarà necessario decidere in fase tecnica se:
- usare una struttura generica di feedback
oppure
- separare i feedback tra area allenamento e area nutrizione

Questa decisione verrà definita meglio in una fase successiva.

---

## 4. Relazioni principali

### 4.1 Gerarchia utenti
- `ProfessionalProfile` estende `User`
- `ClientProfile` estende `User`

### 4.2 Collegamento professionista-cliente
- `ProfessionalProfile` ↔ `ClientProfile`
- relazione molti-a-molti gestita tramite `ProfessionalClientLink`

### 4.3 Codice invito
- un `ProfessionalProfile` può generare molti `InviteCode`
- un `InviteCode` appartiene a un solo professionista

### 4.4 Disponibilità
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può avere molti `AvailabilitySlot`

### 4.5 Prenotazioni
- un `ClientProfile` può creare molte `BookingRequest`
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può ricevere molte `BookingRequest`
- una `BookingRequest` può contenere molti `BookingRequestItem`
- ogni `BookingRequestItem` punta a un solo `AvailabilitySlot`

### 4.6 Schede di allenamento
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può creare molte `WorkoutPlan`
- un `ClientProfile` può ricevere molte `WorkoutPlan`
- una `WorkoutPlan` contiene molte `WorkoutWeek`
- una `WorkoutWeek` contiene molti `WorkoutDay`
- un `WorkoutDay` contiene molti `WorkoutExercise`

### 4.7 Piani alimentari
- un `ProfessionalProfile` di tipo `NUTRITIONIST` può creare molti `NutritionPlan`
- un `ClientProfile` può ricevere molti `NutritionPlan`
- un `NutritionPlan` contiene molte `NutritionWeek`
- una `NutritionWeek` contiene molti `NutritionDay`
- un `NutritionDay` contiene molte `NutritionEntry`

### 4.8 Segnalazioni
- un `ClientProfile` può inviare molte segnalazioni
- un `ProfessionalProfile` può ricevere molte segnalazioni

---

## 5. Regole di business principali

- un professionista deve verificare l’email prima di usare le funzionalità operative
- un cliente può registrarsi solo con codice invito valido
- un codice invito è monouso e ha scadenza
- un cliente può essere collegato a massimo 3 professionisti
- un professionista non può collegarsi a sé stesso come cliente
- le prenotazioni tramite app riguardano solo i personal trainer
- una richiesta di prenotazione può includere uno o più slot
- workout plan e nutrition plan restano separati

---

## 6. Decisioni aperte da approfondire
Nei prossimi documenti dovranno essere definiti meglio:

- campi finali completi di ogni entità
- strategia tecnica di ereditarietà JPA
- struttura finale dei feedback cliente
- regole precise sugli slot prenotabili
- gestione notifiche e promemoria
- validazioni principali