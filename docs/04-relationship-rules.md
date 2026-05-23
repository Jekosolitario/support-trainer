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

## 1.1 Stato di implementazione delle relazioni

### Relazioni attualmente implementate nel backend

- ereditarietà tra `User`, `ProfessionalProfile` e `ClientProfile`;
- relazione professionista-cliente tramite `ProfessionalClientLink`;
- relazione professionista-codici invito tramite `InviteCode`;
- relazione personal trainer-disponibilità tramite `AvailabilitySlot`;
- relazione cliente-professionista-prenotazione tramite `BookingRequest`;
- relazione richiesta-slot tramite `BookingRequestItem`.

### Relazioni pianificate ma non ancora implementate

- cliente-misurazioni fisiche;
- personal trainer-schede workout;
- nutrizionista-piani alimentari;
- cliente-feedback workout;
- cliente-feedback nutrizione.

Le sezioni relative ai moduli pianificati descrivono regole di dominio future e non componenti già presenti nel codice reale.

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

### 2.3 Entità coinvolte attualmente

Nel backend attuale questa regola riguarda principalmente:

- `ProfessionalProfile`;
- `ClientProfile`;
- `ProfessionalClientLink`;
- `InviteCode`;
- `AvailabilitySlot`;
- `BookingRequest`.

Per i moduli futuri, la stessa logica potrà essere applicata anche a:

- `WorkoutPlan`;
- `NutritionPlan`;
- `ClientMeasurement`;
- feedback e altri dati storici del cliente.

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
Solo un professionista con:
- `accountStatus = ACTIVE`
- `emailVerified = true`
- profilo `active = true`

può generare codici invito.

---

## 6. Relazione tra personal trainer e disponibilità

### 6.1 Stato di implementazione

La relazione tra personal trainer e disponibilità è implementata nel backend tramite l’entità `AvailabilitySlot`.

### 6.2 Ambito della relazione

Le disponibilità sono gestite solo per professionisti con specializzazione:

- `PERSONAL_TRAINER`

### 6.3 Cardinalità logica

Un personal trainer può avere:

- molti `AvailabilitySlot`.

Ogni `AvailabilitySlot` appartiene a:

- un solo personal trainer.

### 6.4 Regole temporali implementate

Ogni slot deve rispettare queste regole:

- `startDateTime < endDateTime`;
- in creazione o aggiornamento, `startDateTime` deve essere nel futuro.

### 6.5 Regola di non sovrapposizione

Per lo stesso personal trainer non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

### 6.6 Stati dello slot

Uno slot può trovarsi in uno dei seguenti stati:

- `AVAILABLE`;
- `BOOKED`;
- `BLOCKED`.

### 6.7 Ownership e gestione

Il personal trainer autenticato può:

- creare i propri slot;
- leggere i propri slot;
- aggiornare solo i propri slot `AVAILABLE`;
- bloccare solo i propri slot `AVAILABLE`;
- sbloccare solo i propri slot `BLOCKED`.

### 6.8 Lettura lato cliente

Un cliente può leggere gli slot disponibili di un professionista solo se:

- il cliente è attivo;
- il professionista è attivo;
- esiste un collegamento attivo tra cliente e professionista.

### 6.9 Slot con richiesta booking pendente

Uno slot può rimanere in stato `AVAILABLE` mentre esiste una richiesta booking in stato `PENDING`, perché la prenotazione non è ancora stata confermata.

Tuttavia, durante questa fase lo slot è considerato logicamente riservato rispetto alle operazioni manuali del professionista.

Finché esiste una richiesta booking `PENDING` attiva collegata allo slot, il personal trainer non può:

- modificare data o orario dello slot;
- bloccare manualmente lo slot.

Il professionista deve prima gestire la richiesta pendente tramite il flusso Booking previsto, ad esempio rifiutandola.

---

## 7. Relazione tra cliente, richiesta di prenotazione e slot

### 7.1 Stato di implementazione

La relazione cliente-professionista-prenotazione è implementata tramite:

- `BookingRequest`;
- `BookingRequestItem`;
- `AvailabilitySlot`.

### 7.2 Cardinalità logica

Un cliente può creare:

- molte `BookingRequest`.

Un personal trainer può ricevere:

- molte `BookingRequest`.

Ogni `BookingRequest` appartiene a:

- un solo cliente;
- un solo personal trainer.

### 7.3 Relazione con lo slot

Nel contratto API attuale una `BookingRequest` viene creata a partire da:

- un singolo `availabilitySlotId`.

Ogni richiesta creata tramite API contiene quindi:

- un singolo `BookingRequestItem`.

Ogni `BookingRequestItem` punta a:

- un solo `AvailabilitySlot`.

### 7.4 Evoluzione multi-slot

La struttura `BookingRequest` + `BookingRequestItem` mantiene il modello predisposto per future richieste contenenti più slot.

La gestione multi-slot non è però attualmente implementata nelle API del backend.

### 7.5 Regola di collegamento cliente-professionista

Un cliente può creare una richiesta booking solo verso uno slot appartenente a un professionista con cui esiste un collegamento attivo.

### 7.6 Regola di prenotabilità dello slot

Una nuova richiesta può essere creata solo se lo slot:

- esiste;
- è attivo;
- appartiene al professionista collegato;
- si trova in stato `AVAILABLE`;
- non ha già una richiesta `PENDING` attiva associata.

### 7.7 Stati booking implementati

Una `BookingRequest` può trovarsi in uno dei seguenti stati:

- `PENDING`;
- `CONFIRMED`;
- `REJECTED`;
- `CANCELLED`.

### 7.8 Transizioni implementate

| Azione | Attore autorizzato | Stato iniziale | Stato finale | Effetto sullo slot |
|---|---|---|---|---|
| confirm | professionista coinvolto | `PENDING` | `CONFIRMED` | `AVAILABLE -> BOOKED` |
| reject | professionista coinvolto | `PENDING` | `REJECTED` | resta `AVAILABLE` |
| cancel | cliente coinvolto | `PENDING` | `CANCELLED` | resta `AVAILABLE` |
| cancel | cliente coinvolto | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |
| cancel | professionista coinvolto | `CONFIRMED` | `CANCELLED` | `BOOKED -> AVAILABLE` |

### 7.9 Ownership e visibilità

Il cliente può:

- leggere solo le proprie richieste;
- leggere il dettaglio solo delle richieste in cui è coinvolto;
- cancellare solo richieste proprie negli stati consentiti.

Il professionista può:

- leggere solo le richieste ricevute;
- leggere il dettaglio solo delle richieste riferite ai propri slot;
- confermare o rifiutare solo richieste riferite ai propri slot;
- cancellare solo richieste confermate in cui è coinvolto.

### 7.10 Integrità dello slot

Uno slot già `BOOKED` non può essere confermato nuovamente tramite un’altra richiesta.

### 7.11 Riserva logica dello slot durante `PENDING`

La creazione di una `BookingRequest` in stato `PENDING` non modifica immediatamente lo stato dello slot in `BOOKED`.

Lo slot resta formalmente `AVAILABLE` fino alla conferma del professionista, ma non è più liberamente modificabile dal professionista mentre il cliente attende risposta.

Durante lo stato `PENDING`:

- non può essere creata una seconda richiesta `PENDING` attiva sullo stesso slot;
- lo slot non può essere modificato;
- lo slot non può essere bloccato manualmente.

Questa regola garantisce coerenza tra la disponibilità selezionata dal cliente e la successiva decisione del professionista.

---

## 8. Relazione tra cliente e misurazioni fisiche — Pianificata, non implementata

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

## 9. Relazione tra personal trainer e schede di allenamento — Pianificata, non implementata

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

## 10. Relazione tra nutrizionista e piani alimentari — Pianificata, non implementata

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

## 11. Relazione tra cliente e feedback workout — Pianificata, non implementata

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

## 12. Relazione tra cliente e feedback nutrizione — Pianificata, non implementata

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

### 13.1 Ownership attualmente implementata

#### Area availability

- un `AvailabilitySlot` appartiene a un solo `ProfessionalProfile`;
- solo il professionista proprietario può modificarlo, bloccarlo o sbloccarlo.

#### Area booking

- una `BookingRequest` appartiene a un solo `ClientProfile` e a un solo `ProfessionalProfile`;
- un `BookingRequestItem` dipende logicamente dalla propria `BookingRequest`;
- il dettaglio booking è accessibile solo agli utenti coinvolti;
- conferma e rifiuto competono al professionista coinvolto;
- cancellazione compete al cliente coinvolto o, se già confermata, anche al professionista coinvolto.
- una richiesta `PENDING` riserva logicamente lo slot rispetto a modifica e blocco manuale;
- il professionista non può alterare uno slot oggetto di richiesta pendente prima di aver gestito tale richiesta.

### 13.2 Ownership pianificata per moduli futuri

#### Area workout

- `WorkoutWeek`;
- `WorkoutDay`;
- `WorkoutExercise`;

dipenderanno logicamente da `WorkoutPlan`.

#### Area nutrition

- `NutritionWeek`;
- `NutritionDay`;
- `NutritionEntry`;

dipenderanno logicamente da `NutritionPlan`.

### 13.3 Regola generale

Le entità dipendenti:

- non hanno senso senza il proprio contenitore;
- devono essere accessibili solo attraverso risorse autorizzate;
- devono rispettare l’ownership logica della risorsa principale.

---

## 14. Regole di archivio e storico

### 14.1 Dati attualmente storicizzati o mantenuti logicamente

Nel backend attuale devono essere mantenuti nello storico almeno:

- collegamenti professionista-cliente disattivati;
- codici invito usati, scaduti o disattivati;
- slot availability mantenuti tramite stato e flag logico;
- richieste booking concluse, rifiutate o cancellate.

### 14.2 Dati futuri da storicizzare

Quando i relativi moduli verranno implementati, dovranno essere mantenuti nello storico anche:

- schede workout non più attive;
- piani alimentari non più attivi;
- misurazioni fisiche;
- feedback inviati dal cliente.

---

## 15. Regole logiche di unicità e coerenza

### 15.1 Regole attualmente implementate

Il backend attuale garantisce o controlla tramite persistence/service layer:

- una `email` utente univoca;
- un `InviteCode.code` univoco;
- un solo collegamento attivo per coppia professionista-cliente;
- massimo 3 professionisti attivi per cliente;
- impossibilità di auto-collegamento professionista-cliente;
- slot availability con intervallo temporale valido;
- slot creati o aggiornati solo con data iniziale futura;
- assenza di sovrapposizioni tra slot attivi dello stesso professionista;
- booking creabile solo tra cliente e professionista collegati;
- booking creabile solo su slot disponibile;
- assenza di richiesta `PENDING` duplicata sullo stesso slot;
- rispetto delle transizioni consentite della prenotazione;
- impossibilità di confermare nuovamente uno slot già `BOOKED`.
- impossibilità di modificare uno slot con richiesta booking `PENDING` attiva;
- impossibilità di bloccare manualmente uno slot con richiesta booking `PENDING` attiva;
- protezione delle operazioni concorrenti critiche su availability e booking per mantenere coerenti slot e richieste.

### 15.2 Regole pianificate per moduli futuri

Quando i relativi moduli verranno implementati, dovranno essere valutate anche:

- una sola scheda workout attiva per coppia personal trainer-cliente;
- un solo piano nutrizione attivo per coppia nutrizionista-cliente;
- vincoli di ownership su misurazioni e feedback.

---