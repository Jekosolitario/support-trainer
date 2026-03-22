# Relationship Rules — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce le regole logiche delle relazioni tra le entità del dominio.

Lo scopo è chiarire:
- cardinalità principali
- vincoli di unicità
- regole di attivazione/disattivazione
- conservazione dello storico
- comportamento logico delle relazioni nel tempo

---

## 2. Regole generali di conservazione dati

### 2.1 Nessuna eliminazione fisica dei dati principali
Per le entità principali del sistema non è prevista, come regola generale, la cancellazione fisica dal database.

Si adotta invece una logica di:
- **soft delete**
- **disattivazione logica**
- **conservazione dello storico**

### 2.2 Motivazione
Questa scelta permette di:
- mantenere lo storico del sistema
- evitare perdita di dati importanti
- preservare relazioni, contenuti e tracciabilità
- rendere il progetto più realistico e professionale

### 2.3 Entità coinvolte principalmente
Questa regola vale soprattutto per:
- `ProfessionalProfile`
- `ClientProfile`
- `ProfessionalClientLink`
- `WorkoutPlan`
- `NutritionPlan`

---

## 3. Gerarchia utenti

### 3.1 Relazione di ereditarietà
- `ProfessionalProfile` estende `User`
- `ClientProfile` estende `User`

### 3.2 Identità logica
Ogni record di tipo professionista o cliente deriva da un utente base e rappresenta una singola identità logica nel sistema.

### 3.3 Regola di coerenza
Un account utente non può essere collegato a sé stesso in ruoli incompatibili nella stessa relazione professionista-cliente.

---

## 4. Relazione tra professionista e cliente

### 4.1 Cardinalità logica
La relazione tra professionista e cliente è:
- **molti-a-molti**
- gestita tramite entità intermedia `ProfessionalClientLink`

### 4.2 Regola di unicità del collegamento attivo
Tra lo stesso professionista e lo stesso cliente può esistere:
- **un solo collegamento attivo**

Non devono quindi esistere due `ProfessionalClientLink` attivi con la stessa coppia:
- `professional`
- `client`

### 4.3 Fine del rapporto
Quando il rapporto termina:
- il collegamento non viene eliminato
- il collegamento viene **disattivato** (`active = false`)

### 4.4 Limite massimo professionisti per cliente
Un cliente può avere al massimo:
- **3 professionisti attivi**

### 4.5 Regola di self-link
Il sistema non deve permettere che un professionista:
- generi un collegamento cliente verso sé stesso
- utilizzi il proprio account per auto-collegarsi come cliente

---

## 5. Relazione tra professionista e codici invito

### 5.1 Cardinalità logica
Un professionista può generare:
- **molti codici invito**

Ogni `InviteCode` appartiene a:
- **un solo professionista**

### 5.2 Codici contemporanei
Un professionista può avere:
- **più codici invito attivi contemporaneamente**

### 5.3 Vincoli sul codice invito
Ogni codice deve rispettare queste regole:
- è monouso
- ha una scadenza
- può essere usato una sola volta
- dopo l’utilizzo viene marcato come usato
- dopo la scadenza non è più valido

### 5.4 Regola di abilitazione
Solo un professionista con account:
- verificato
- attivo

può generare codici invito.

---

## 6. Relazione tra personal trainer e disponibilità

### 6.1 Ambito della relazione
Le disponibilità (`AvailabilitySlot`) sono previste solo per professionisti con specializzazione:
- `PERSONAL_TRAINER`

### 6.2 Cardinalità logica
Un personal trainer può avere:
- molti `AvailabilitySlot`

Ogni `AvailabilitySlot` appartiene a:
- un solo personal trainer

### 6.3 Regola temporale
Ogni slot deve avere:
- `startDateTime < endDateTime`

### 6.4 Regola di non sovrapposizione
Per lo stesso personal trainer non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

### 6.5 Stato dello slot
Uno slot può trovarsi in uno dei seguenti stati:
- `AVAILABLE`
- `BOOKED`
- `BLOCKED`

---

## 7. Relazione tra cliente, richiesta di prenotazione e slot

### 7.1 Cardinalità logica
Un cliente può creare:
- molte `BookingRequest`

Un personal trainer può ricevere:
- molte `BookingRequest`

Ogni `BookingRequest` appartiene a:
- un solo cliente
- un solo personal trainer

### 7.2 Relazione con i dettagli della richiesta
Una `BookingRequest` può contenere:
- uno o più `BookingRequestItem`

Ogni `BookingRequestItem` punta a:
- un solo `AvailabilitySlot`

### 7.3 Prenotazioni multi-giorno
La struttura `BookingRequest` + `BookingRequestItem` consente al cliente di:
- inviare una sola richiesta
- includere più date e più slot
- coprire anche periodi distribuiti su più giorni

### 7.4 Regola di coerenza professionista-slot
Tutti gli slot contenuti nei `BookingRequestItem` devono appartenere:
- allo stesso personal trainer indicato nella `BookingRequest`

### 7.5 Conferma della richiesta
Quando una `BookingRequest` viene confermata:
- tutti gli slot collegati diventano `BOOKED`

### 7.6 Rifiuto della richiesta
Quando una `BookingRequest` viene rifiutata:
- gli slot collegati restano o tornano `AVAILABLE`, se non impegnati da altre logiche di blocco

### 7.7 Regola di integrità
Uno slot già `BOOKED` non deve poter essere riutilizzato in una nuova richiesta confermata.

---

## 8. Relazione tra cliente e misurazioni fisiche

### 8.1 Cardinalità logica
Un cliente può avere:
- molte `ClientMeasurement`

Ogni `ClientMeasurement` appartiene a:
- un solo cliente

### 8.2 Inserimento delle misurazioni
Le misurazioni possono essere inserite:
- dal cliente
- dal professionista

### 8.3 Regola professionale consigliata
Per mantenere tracciabilità e affidabilità, ogni misurazione dovrebbe in futuro conservare anche:
- la fonte del dato
- l’utente che l’ha registrata

Esempi futuri:
- `sourceType = SELF_REPORTED / PROFESSIONAL_RECORDED`
- `recordedByUser`

### 8.4 Regola storica
Le misurazioni sono dati storici.  
Per questo motivo:
- non dovrebbero essere sovrascritte normalmente
- una nuova rilevazione dovrebbe generare un nuovo record
- eventuali correzioni vanno considerate eccezioni

---

## 9. Relazione tra personal trainer e schede di allenamento

### 9.1 Cardinalità logica
Un personal trainer può creare:
- molte `WorkoutPlan`

Un cliente può ricevere:
- molte `WorkoutPlan`

Ogni `WorkoutPlan` appartiene a:
- un solo personal trainer
- un solo cliente

### 9.2 Struttura interna
Una `WorkoutPlan` contiene:
- molte `WorkoutWeek`

Una `WorkoutWeek` contiene:
- molti `WorkoutDay`

Un `WorkoutDay` contiene:
- molti `WorkoutExercise`

### 9.3 Regola di attivazione
Per una coppia:
- personal trainer
- cliente

può esistere:
- **una sola scheda workout attiva alla volta**

### 9.4 Durata della scheda
La scheda attiva:
- può rimanere valida anche oltre un mese
- resta attiva finché il professionista non la sostituisce con una nuova versione

### 9.5 Aggiornamento della scheda
Quando il professionista aggiorna il programma in modo sostanziale:
- viene creata una nuova `WorkoutPlan`
- la precedente viene mantenuta nello storico
- la precedente viene disattivata logicamente (`active = false`)

### 9.6 Storico
Il sistema deve conservare lo storico delle versioni precedenti delle schede.

### 9.7 Evidenziazione modifiche
L’eventuale evidenziazione dei dati modificati rispetto alla versione precedente è considerata:
- funzione utile
- ma successiva alla regola base di storicizzazione

---

## 10. Relazione tra nutrizionista e piani alimentari

### 10.1 Cardinalità logica
Un nutrizionista può creare:
- molti `NutritionPlan`

Un cliente può ricevere:
- molti `NutritionPlan`

Ogni `NutritionPlan` appartiene a:
- un solo nutrizionista
- un solo cliente

### 10.2 Struttura interna
Un `NutritionPlan` contiene:
- molte `NutritionWeek`

Una `NutritionWeek` contiene:
- molti `NutritionDay`

Un `NutritionDay` contiene:
- molte `NutritionEntry`

### 10.3 Regola di attivazione
Per una coppia:
- nutrizionista
- cliente

può esistere:
- **un solo piano alimentare attivo alla volta**

### 10.4 Durata del piano
Anche il piano alimentare:
- può restare attivo oltre un singolo mese
- rimane valido finché non viene sostituito

### 10.5 Aggiornamento del piano
Quando il piano viene aggiornato in modo sostanziale:
- viene creato un nuovo `NutritionPlan`
- il precedente resta nello storico
- il precedente viene disattivato logicamente (`active = false`)

---

## 11. Relazione tra cliente e feedback workout

### 11.1 Cardinalità logica
Un cliente può inviare:
- molti `WorkoutFeedback`

Un personal trainer può ricevere:
- molti `WorkoutFeedback`

Ogni `WorkoutFeedback` appartiene a:
- un solo cliente
- un solo personal trainer
- un solo `WorkoutDay`

### 11.2 Regola di coerenza
Il `WorkoutDay` associato al feedback deve appartenere a:
- una `WorkoutPlan` del personal trainer destinatario
- assegnata allo stesso cliente che invia il feedback

---

## 12. Relazione tra cliente e feedback nutrizione

### 12.1 Cardinalità logica
Un cliente può inviare:
- molti `NutritionFeedback`

Un nutrizionista può ricevere:
- molti `NutritionFeedback`

Ogni `NutritionFeedback` appartiene a:
- un solo cliente
- un solo nutrizionista
- un solo `NutritionDay`

### 12.2 Regola di coerenza
Il `NutritionDay` associato al feedback deve appartenere a:
- un `NutritionPlan` del nutrizionista destinatario
- assegnato allo stesso cliente che invia il feedback

---

## 13. Regole di ownership logica

### 13.1 Entità “contenitore”
Le entità principali che fanno da contenitore logico sono:
- `WorkoutPlan`
- `NutritionPlan`
- `BookingRequest`

### 13.2 Entità dipendenti
Le entità dipendenti dal contenitore sono:

#### Area workout
- `WorkoutWeek`
- `WorkoutDay`
- `WorkoutExercise`

#### Area nutrition
- `NutritionWeek`
- `NutritionDay`
- `NutritionEntry`

#### Area booking
- `BookingRequestItem`

### 13.3 Regola generale
Le entità dipendenti:
- non hanno senso senza il proprio contenitore
- dipendono logicamente dalla sua esistenza
- devono essere trattate come parte della struttura interna del contenitore

---

## 14. Regole di archivio e storico

### 14.1 Dati da storicizzare
Devono essere mantenuti nello storico almeno:
- collegamenti professionista-cliente disattivati
- codici invito usati/scaduti
- schede workout non più attive
- piani alimentari non più attivi
- misurazioni fisiche
- richieste di prenotazione

### 14.2 Benefici dello storico
La storicizzazione consente:
- analisi successive
- maggiore tracciabilità
- confronto tra versioni
- comportamento più professionale del sistema

---

## 15. Regole logiche di unicità e coerenza

Il sistema deve garantire almeno le seguenti regole:

- una `email` utente è univoca
- un `InviteCode.code` è univoco
- non possono esistere due collegamenti attivi uguali tra stesso professionista e stesso cliente
- un cliente non può avere più di 3 professionisti attivi
- uno slot non può essere confermato due volte
- una scheda workout attiva è unica per coppia PT-cliente
- un piano nutrizione attivo è unico per coppia nutrizionista-cliente

---