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
- `profileImageUrl`
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

`profileImageUrl` rappresenta il percorso o URL della foto profilo dell’utente ed è facoltativo.

---

## 3.2 ProfessionalProfile
Rappresenta un professionista registrato alla piattaforma.

### Eredita da
- `User`

### Campi specifici iniziali
- `specialization`
- `operationalStatus`
- `phoneNumber`
- `bio`
- `workplaceName`
- `city`
- `active`

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
- `birthDate`
- `heightCm`
- `primaryGoal`
- `gender`
- `medicalNotes`
- `injuryNotes`
- `notes`
- `active`

### Esempi valori
- `operationalStatus`: `ATTIVO`, `INFORTUNATO`, `PAUSA`

### Note
`ClientProfile` contiene i dati principali e relativamente stabili del cliente.

I dati fisici che cambiano nel tempo, utili per monitoraggio, storico e grafici, non devono stare qui in modo diretto ma in una entità separata dedicata alle misurazioni.

### Responsabilità
Il cliente può:
- collegarsi a professionisti
- visualizzare contenuti assegnati
- inviare richieste di prenotazione al PT
- inviare segnalazioni sui contenuti ricevuti

---

## 3.4 ClientMeasurement
Rappresenta una rilevazione fisica del cliente registrata nel tempo.

### Campi iniziali
- `id`
- `client`
- `recordedAt`
- `weightKg`
- `bodyFatPercentage`
- `waistCm`
- `chestCm`
- `hipsCm`
- `notes`

### Note
Questa entità serve a:
- mantenere uno storico delle misurazioni
- supportare il monitoraggio da parte dei professionisti
- rendere possibili grafici futuri e analisi andamento

### Nota progettuale
Indicatori derivati come BMI o peso ideale stimato possono essere:
- calcolati dal backend
- eventualmente salvati in futuro se servirà uno snapshot storico esplicito

---

## 3.5 ProfessionalClientLink
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

## 3.6 InviteCode
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

## 3.7 AvailabilitySlot
Rappresenta una fascia di disponibilità del personal trainer.

### Campi attualmente implementati
- `id`
- `professional`
- `startDateTime`
- `endDateTime`
- `status`
- `active`
- `createdAt`
- `updatedAt`

### Note
Questa entità è utilizzata solo per i professionisti con specializzazione:
- `PERSONAL_TRAINER`

### Stati slot implementati
- `AVAILABLE`
- `BOOKED`
- `BLOCKED`

### Regole principali implementate
- lo slot appartiene a un solo professionista;
- lo slot deve iniziare nel futuro in fase di creazione o aggiornamento;
- gli slot attivi dello stesso professionista non possono sovrapporsi;
- lo stato `BOOKED` viene gestito dal flusso Booking.

---

## 3.8 BookingRequest
Rappresenta una richiesta di prenotazione inviata da un cliente verso un personal trainer.

### Campi attualmente implementati
- `id`
- `client`
- `professional`
- `status`
- `note`
- `active`
- `createdAt`
- `updatedAt`

### Stati richiesta implementati
- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### Note
Una richiesta di prenotazione appartiene a:
- un solo cliente;
- un solo personal trainer.

Nel backend attuale la richiesta viene creata a partire da un singolo `availabilitySlotId`.

La richiesta viene mantenuta nello storico tramite stato e flag `active`, senza eliminazione fisica nel normale ciclo operativo.

---

## 3.9 BookingRequestItem
Rappresenta il dettaglio dello slot collegato a una richiesta di prenotazione.

### Campi attualmente implementati
- `id`
- `bookingRequest`
- `availabilitySlot`
- `createdAt`
- `updatedAt`

### Stato attuale dell’implementazione
Nel backend attuale ogni richiesta creata tramite API contiene un solo `BookingRequestItem`, collegato allo slot indicato da `availabilitySlotId`.

### Evoluzione possibile
La presenza di questa entità mantiene il modello estendibile a una futura gestione multi-slot, ma tale comportamento non è attualmente implementato nel contratto API.

---

## 3.10 WorkoutPlan
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

## 3.11 WorkoutWeek
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

## 3.12 WorkoutDay
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

## 3.13 WorkoutExercise
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

## 3.14 NutritionPlan
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

## 3.15 NutritionWeek
Rappresenta una settimana interna a un piano alimentare.

### Campi iniziali
- `id`
- `nutritionPlan`
- `weekNumber`

---

## 3.16 NutritionDay
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

## 3.17 NutritionEntry
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

## 3.18 ClientFeedback
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

## 3.19 Stato di implementazione del dominio

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
- `ClientFeedback`

Le entità pianificate restano valide come ipotesi di dominio futuro, ma non rappresentano componenti già presenti nel codice reale.

---

## 4. Relazioni principali

### 4.1 Gerarchia utenti
- `ProfessionalProfile` estende `User`
- `ClientProfile` estende `User`

### 4.2 Monitoraggio fisico cliente
- un `ClientProfile` può avere molte `ClientMeasurement`
- ogni `ClientMeasurement` appartiene a un solo cliente

### 4.3 Collegamento professionista-cliente
- `ProfessionalProfile` ↔ `ClientProfile`
- relazione molti-a-molti gestita tramite `ProfessionalClientLink`

### 4.4 Codice invito
- un `ProfessionalProfile` può generare molti `InviteCode`
- un `InviteCode` appartiene a un solo professionista

### 4.5 Disponibilità
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può avere molti `AvailabilitySlot`

### 4.6 Prenotazioni
- un `ClientProfile` può creare molte `BookingRequest`;
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può ricevere molte `BookingRequest`;
- una `BookingRequest` contiene attualmente un singolo `BookingRequestItem` nel flusso esposto dalle API;
- ogni `BookingRequestItem` punta a un solo `AvailabilitySlot`;
- il modello dati resta predisposto per una futura evoluzione multi-slot.

### 4.7 Schede di allenamento
- un `ProfessionalProfile` di tipo `PERSONAL_TRAINER` può creare molte `WorkoutPlan`
- un `ClientProfile` può ricevere molte `WorkoutPlan`
- una `WorkoutPlan` contiene molte `WorkoutWeek`
- una `WorkoutWeek` contiene molti `WorkoutDay`
- un `WorkoutDay` contiene molti `WorkoutExercise`

### 4.8 Piani alimentari
- un `ProfessionalProfile` di tipo `NUTRITIONIST` può creare molti `NutritionPlan`
- un `ClientProfile` può ricevere molti `NutritionPlan`
- un `NutritionPlan` contiene molte `NutritionWeek`
- una `NutritionWeek` contiene molti `NutritionDay`
- un `NutritionDay` contiene molte `NutritionEntry`

### 4.9 Segnalazioni
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
- un cliente può creare booking solo verso professionisti a lui collegati
- uno slot confermato tramite booking passa allo stato `BOOKED`
- nel backend attuale una richiesta di prenotazione viene creata su un singolo slot
- il modello `BookingRequestItem` resta predisposto per una futura gestione multi-slot
- workout plan e nutrition plan restano separati
- le misurazioni fisiche del cliente devono essere storicizzate in una entità dedicata

---
