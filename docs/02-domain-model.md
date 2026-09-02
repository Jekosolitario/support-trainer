# Domain Model — Support Trainer

> Booking V1 corrente: `BookingRequest` include `rejectionReason`, `cancellationReason` e `cancelledBy` (`BookingCancellationActor.CLIENT|PROFESSIONAL`), oltre ai timestamp di transizione. I metadata sono atomici con la transizione, privi di setter pubblici e nullable per lo storico legacy. Il limite mutation deriva da `MAX(BookingRequestItem.scheduledEnd)`, non dallo slot live. Vedi [19-booking-domain-contract-v1.md](19-booking-domain-contract-v1.md).

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

- **User** _(astratta)_
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
- `sessionVersion`
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

## 3.6.1 WeeklyAvailabilityRule

Definisce una fascia ricorrente della settimana lavorativa del Personal Trainer.

I campi correnti sono `id`, `professional`, `dayOfWeek`, `startTime`, `endTime`, `allowedDurations`, `locationLabel`, `capacityPerSlot`, `active`, `validFrom` e audit. `allowedDurations` è una collezione normalizzata di durate da 15 a 180 minuti, multiple di 15 e interamente contenute nella finestra. Anche gli estremi della finestra sono allineati a 15 minuti. Le regole attive dello stesso giorno non possono sovrapporsi, mentre possono essere adiacenti.

Update e deactivate modificano immediatamente il futuro dalla data/ora corrente. L'eventuale impatto sui Booking occupanti richiede una motivazione, registrata in `AvailabilityRuleChange`.

---

## 3.7 AvailabilitySlot

Rappresenta un'occorrenza-finestra limitata nel tempo e materializzata da una regola settimanale. Non rappresenta una singola combinazione inizio/durata: una sola occorrenza espone più inizi a intervalli di 15 minuti e più durate consentite.

### Campi attualmente implementati

- `id`
- `professional`
- `weeklyRule` opzionale per gli slot legacy
- `startDateTime`
- `endDateTime`
- `locationLabel`
- `capacity`
- `blocked`
- `status` legacy
- `active`
- `createdAt`
- `updatedAt`

### Note

Questa entità è utilizzata solo per i professionisti con specializzazione:

- `PERSONAL_TRAINER`

### Regole principali implementate

- lo slot appartiene a un solo professionista;
- l'occupancy è derivata da Booking `PENDING` e `CONFIRMED`;
- la capacità misura il massimo numero di Client contemporaneamente presenti, calcolato sugli intervalli Booking sovrapposti;
- il Booking è ammesso soltanto se lo slot è attivo, futuro, non bloccato e la combinazione scelta ha capacità residua;
- una singola occorrenza futura può essere bloccata senza cancellare i Booking; se ne coinvolge almeno uno serve una motivazione conservata in `AvailabilitySlotChange`;
- le occorrenze passate non sono elencate fra le azioni operative e non sono modificabili;
- `BOOKED` resta un valore legacy e non costituisce più l'unica authority per la prenotabilità.

---

## 3.8 BookingRequest

Rappresenta una richiesta di prenotazione inviata da un cliente verso un personal trainer.

### Campi attualmente implementati

- `id`
- `client`
- `professional`
- `status`
- `note`
- `clientDisplayName`
- `professionalDisplayName`
- `confirmedAt`
- `rejectedAt`
- `cancelledAt`
- `rejectionReason`
- `cancellationReason`
- `cancelledBy`
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

Nel backend attuale la richiesta viene creata a partire da una singola occorrenza settimanale identificata da `availabilitySlotId`, con `startDateTime` e `durationMinutes` scelti dal Client. Il server deriva e valida la fine. Gli slot manuali legacy restano conservati esclusivamente per compatibilità e Booking storici: non possono essere usati per creare nuove richieste.

Il lock del Client rende race-safe il vincolo che vieta allo stesso Client due Booking `PENDING` o `CONFIRMED` temporalmente sovrapposti; intervalli adiacenti sono ammessi.

La richiesta viene mantenuta nello storico tramite stato e flag `active`, senza eliminazione fisica nel normale ciclo operativo.

Le nuove transizioni valorizzano atomicamente timestamp e metadata: reject richiede una reason valida; cancel richiede sempre l'actor server-side e, sullo stato `CONFIRMED`, una reason. I metadata restano nullable nello schema esclusivamente per idratare record legacy.

---

## 3.9 BookingRequestItem

Rappresenta il dettaglio dello slot collegato a una richiesta di prenotazione.

### Campi attualmente implementati

- `id`
- `bookingRequest`
- `availabilitySlot`
- `scheduledStart`
- `scheduledEnd`
- `locationLabelSnapshot`
- `createdAt`
- `updatedAt`

### Stato attuale dell’implementazione

Nel backend attuale ogni richiesta creata tramite API contiene un solo `BookingRequestItem`, collegato all'occorrenza indicata da `availabilitySlotId`. Inizio, fine e luogo sono snapshot immutabili della scelta concordata e non seguono successive modifiche alla regola o all'occorrenza.

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
- `PasswordResetToken`
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
- le richieste `PENDING` e `CONFIRMED` occupano una unità di capacità per il rispettivo intervallo; confermare una richiesta non modifica globalmente la prenotabilità della finestra
- nel backend attuale una richiesta di prenotazione viene creata su una singola occorrenza con inizio e durata scelti
- il modello `BookingRequestItem` resta predisposto per una futura gestione multi-slot
- workout plan e nutrition plan restano separati
- le misurazioni fisiche del cliente devono essere storicizzate in una entità dedicata

---
