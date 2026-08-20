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
- relazione personal trainer-disponibilità tramite `WeeklyAvailabilityRule` e occurrence materializzate in `AvailabilitySlot`;
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

La relazione tra personal trainer e disponibilità è implementata tramite `WeeklyAvailabilityRule` e occurrence/window materializzate in `AvailabilitySlot`.

### 6.2 Ambito della relazione

Le disponibilità sono gestite solo per professionisti con specializzazione:

- `PERSONAL_TRAINER`.

### 6.3 Cardinalità logica

Un personal trainer può avere molte `WeeklyAvailabilityRule`; ciascuna regola può generare molte occurrence `AvailabilitySlot`.

Ogni occurrence appartiene a un solo personal trainer e conserva, quando presente, il riferimento alla regola settimanale che l'ha generata.

### 6.4 Regole temporali implementate

Ogni slot deve rispettare queste regole:

- `startDateTime < endDateTime`;
- in creazione o aggiornamento, `startDateTime` deve essere nel futuro.

### 6.5 Regola di non sovrapposizione

Per lo stesso personal trainer non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

La creazione e l’aggiornamento degli slot sono protetti da lock pessimista sul professionista, così il controllo resta coerente anche in presenza di richieste concorrenti.

### 6.6 Stato legacy e prenotabilità corrente

Il campo legacy dello slot può contenere `AVAILABLE`, `BOOKED` o `BLOCKED`, ma il lifecycle binario `AVAILABLE ↔ BOOKED` è storico/superseded e non determina la capacità corrente.

La prenotabilità deriva invece da occurrence/window attiva e futura, blocco, capacità configurata, occupancy dell'intervallo e combinazioni autoritative esposte in `bookableOptions`. Il workflow Booking non muta uno stato globale dello slot a `BOOKED`.

### 6.7 Ownership e gestione

Il personal trainer autenticato può:

- creare e gestire le proprie regole settimanali;
- leggere le occurrence materializzate;
- modificare, disattivare o bloccare le proprie disponibilità nel rispetto di snapshot Booking, capacity e validazioni di impatto.

### 6.8 Lettura lato cliente

Un cliente può leggere gli slot disponibili di un professionista solo se:

- il cliente è attivo;
- il professionista è attivo;
- esiste un collegamento attivo tra cliente e professionista.

La lettura lato cliente espone occurrence materializzate attive, future e non bloccate. Le sole combinazioni prenotabili sono quelle presenti in `bookableOptions`, calcolate server-side in base a finestre, durate consentite, overlap e capacità residua.

Una richiesta `PENDING` non nasconde necessariamente l'intera occurrence: occupa capacità soltanto sul proprio intervallo snapshot e le altre combinazioni restano esposte quando conservano capacità residua.

### 6.9 Occupancy delle occurrence

`PENDING` e `CONFIRMED` occupano capacità sull'intervallo snapshot; `REJECTED` e `CANCELLED` la liberano. Non esiste una riserva globale basata sulla presenza di un solo `PENDING` e nessuna transizione Booking muta l'intera occurrence a `BOOKED`.

Le operazioni Availability che incidono su Booking esistenti preservano gli snapshot e applicano le validazioni di impatto e capacità previste dal service layer.

### 6.10 Integrità storica dello slot dopo una richiesta booking

Quando uno slot viene collegato ad almeno una richiesta booking, il relativo intervallo temporale entra nello storico della prenotazione.

Anche se la richiesta viene successivamente rifiutata o cancellata, lo slot non può più essere ripianificato modificandone data o ora.

L'occurrence può ricevere nuove richieste sullo stesso intervallo temporale se conserva capacità residua o se una precedente richiesta `REJECTED`/`CANCELLED` l'ha liberata, nel rispetto delle altre regole applicative.

Per proporre una nuova disponibilità in un giorno o orario differente, il personal trainer deve creare un nuovo `AvailabilitySlot`.

Questa regola impedisce che lo storico di una richiesta mostri date diverse da quelle originariamente selezionate dal cliente.

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

Nel contratto API attuale una `BookingRequest` viene creata a partire da una combinazione presente in `bookableOptions`:

- `availabilitySlotId` dell'occurrence materializzata;
- `startDateTime`;
- `durationMinutes`.

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
- rappresenta un'occurrence/window materializzata, futura e non bloccata;
- contiene la combinazione richiesta fra le `bookableOptions` autoritative;
- conserva capacità residua per l'intero intervallo;
- non si sovrappone a un Booking occupante dello stesso cliente con quel professionista.

### 7.7 Stati booking implementati

Una `BookingRequest` può trovarsi in uno dei seguenti stati:

- `PENDING`;
- `CONFIRMED`;
- `REJECTED`;
- `CANCELLED`.

### 7.8 Transizioni implementate

| Azione | Attore autorizzato | Stato iniziale | Stato finale | Effetto sulla capacità snapshot |
|---|---|---|---|---|
| confirm | professionista coinvolto | `PENDING` | `CONFIRMED` | occupancy invariata |
| reject | professionista coinvolto | `PENDING` | `REJECTED` | capacità liberata |
| cancel | cliente coinvolto | `PENDING` | `CANCELLED` | capacità liberata |
| cancel | cliente coinvolto | `CONFIRMED` | `CANCELLED` | capacità liberata |
| cancel | professionista coinvolto | `CONFIRMED` | `CANCELLED` | capacità liberata |

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

### 7.10 Integrità di capacity e overlap

Il backend impedisce la creazione oltre la capacità configurata e l'overlap con un Booking occupante dello stesso cliente presso quel professionista. La conferma non consuma un posto ulteriore, perché sia `PENDING` sia `CONFIRMED` sono stati occupanti.

### 7.11 Occupancy durante `PENDING`

La creazione di una `BookingRequest` in stato `PENDING` occupa un posto sul solo intervallo snapshot e non modifica lo stato globale dell'occurrence a `BOOKED`.

Ulteriori richieste sono ammesse soltanto per combinazioni ancora presenti in `bookableOptions` e con capacità residua. Le modifiche Availability applicano le proprie guardie di impatto senza alterare lo snapshot storico già salvato nel `BookingRequestItem`.

### 7.12 Immutabilità temporale dello slot nello storico booking

Uno slot già collegato a una `BookingRequest` mantiene immutabile il proprio intervallo temporale.

La regola vale indipendentemente dallo stato raggiunto dalla richiesta:

- `PENDING`;
- `CONFIRMED`;
- `REJECTED`;
- `CANCELLED`.

Dopo una richiesta rifiutata o cancellata, lo slot può eventualmente essere nuovamente prenotato sullo stesso intervallo temporale, ma non può essere ripianificato modificandone data o ora.

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
- solo il professionista proprietario può modificarlo, bloccarlo o sbloccarlo;
- uno slot già coinvolto in una richiesta booking non può essere ripianificato modificandone l’intervallo temporale.

#### Area booking

- una `BookingRequest` appartiene a un solo `ClientProfile` e a un solo `ProfessionalProfile`;
- un `BookingRequestItem` dipende logicamente dalla propria `BookingRequest`;
- il dettaglio booking è accessibile solo agli utenti coinvolti;
- conferma e rifiuto competono al professionista coinvolto;
- cancellazione compete al cliente coinvolto o, se già confermata, anche al professionista coinvolto;
- una richiesta `PENDING` occupa capacità sul proprio intervallo snapshot senza riservare globalmente l'intera occurrence;
- una richiesta booking preserva il riferimento temporale originario dello slot anche dopo rifiuto o cancellazione.

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
- slot availability mantenuti tramite stato e flag logico, con intervallo temporale immutabile dopo il primo coinvolgimento in una richiesta booking;
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
- availability gestita solo da professionisti `PERSONAL_TRAINER`;
- regole settimanali con occurrence/window materializzate, capacity configurabile e controllo overlap;
- esclusione delle occurrence scadute o bloccate dalla prenotabilità cliente;
- esposizione autoritativa delle sole combinazioni con capacità residua tramite `bookableOptions`;
- conservazione degli snapshot Booking quando una disponibilità viene modificata, disattivata o bloccata;
- booking creabile solo tra cliente e professionista collegati, su slot di un `PERSONAL_TRAINER`;
- booking creabile solo su occurrence/window attiva, futura, non bloccata e con capacità residua;
- `PENDING` e `CONFIRMED` occupano capacità, mentre `REJECTED` e `CANCELLED` la liberano;
- rispetto delle transizioni consentite della prenotazione;
- assenza di mutation globale dello slot a `BOOKED` durante le transizioni Booking;
- protezione tramite lock pessimisti delle operazioni concorrenti critiche su availability e booking.

### 15.2 Regole pianificate per moduli futuri

Quando i relativi moduli verranno implementati, dovranno essere valutate anche:

- una sola scheda workout attiva per coppia personal trainer-cliente;
- un solo piano nutrizione attivo per coppia nutrizionista-cliente;
- vincoli di ownership su misurazioni e feedback.
