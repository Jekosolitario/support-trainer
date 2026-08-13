# Availability Domain Contract v1

## Stato

Contratto corrente implementato per il vertical slice **Personal Trainer — Weekly Availability Management**.

## Settimana tipo

Solo un Professional operativo con specializzazione `PERSONAL_TRAINER` può gestire le proprie `WeeklyAvailabilityRule`. Ogni regola definisce:

- giorno della settimana;
- finestra civile con ora di inizio e fine;
- una o più durate consentite;
- luogo testuale facoltativo;
- capacità concorrente della finestra;
- data iniziale della ricorrenza.

Gli estremi della finestra e gli inizi prenotabili sono allineati a intervalli di 15 minuti. Ogni durata deve essere compresa fra 15 e 180 minuti, essere multipla di 15 e rientrare interamente nella finestra. Le durate sono memorizzate nella tabella figlia normalizzata `weekly_availability_rule_durations`.

L'inizio deve precedere la fine, la capacità deve essere almeno uno e due regole attive dello stesso giorno non possono sovrapporsi. Le fasce adiacenti sono ammesse.

## Occorrenze e rolling horizon

La regola è la source of truth ricorrente. `AvailabilityMaterializationService` crea una sola occorrenza-finestra per data, non una riga per ogni combinazione inizio/durata, da oggi fino a oggi più sei mesi inclusi. La finestra viene rinnovata:

- subito dopo la creazione o modifica di una regola;
- prima delle letture Availability;
- ogni giorno tramite job schedulato.

Le occorrenze sono persistite come `Instant` UTC e risolte dalla data/ora civile nella zona business `Europe/Rome`. Gap e overlap DST sono entrambi rifiutati: il sistema non normalizza un orario inesistente e non sceglie implicitamente uno dei due offset di un orario ambiguo.

Il vincolo univoco `(weekly_rule_id, start_date_time)` e il lock condiviso sul professionista rendono la materializzazione idempotente e coordinata con create, update e deactivate. Scheduler e sincronizzazione on-demand trasportano solo gli identificativi candidati: dopo il lock la regola attiva viene riletta con lock e ricaricata dal database, così un'entity eventualmente precaricata nella persistence context non può riattivare una configurazione superata o disattivata. Lo scheduler materializza ogni candidato in una transazione dedicata.

## Selezione Client e Booking

La response Client identifica l'`occurrenceId`, espone la finestra, le durate consentite, `startIntervalMinutes = 15`, luogo e capacità. Il Client sceglie:

- occorrenza;
- `startDateTime` con offset `Europe/Rome`;
- `durationMinutes`.

Il server deriva la fine e verifica nuovamente all'interno della transazione che inizio e durata siano allineati e consentiti, che l'intervallo rientri nella finestra e che non attraversi un gap o overlap DST.

Le occurrence generate da una `WeeklyAvailabilityRule` sono l'unica sorgente ammessa per nuove prenotazioni. `startDateTime` e `durationMinutes` sono sempre obbligatori e devono formare una combinazione compatibile con la regola. Gli slot manuali legacy con `weeklyRuleId == null` restano persistiti per migrazione e storico, ma non sono discoverable né selezionabili per una nuova Booking Request; la create restituisce lo stesso `404 AVAILABILITY_SLOT_NOT_FOUND` di uno slot assente o non accessibile.

## Capacità, overlap e concorrenza

`PENDING` e `CONFIRMED` occupano una unità per tutto il rispettivo intervallo snapshot; `REJECTED` e `CANCELLED` liberano capacità. La capacità è il massimo numero di Client contemporaneamente presenti in ogni istante della finestra, non un contatore globale dell'occorrenza.

Una combinazione è prenotabile quando l'occorrenza è attiva, futura, non bloccata, l'intervallo è valido e l'occupancy temporale resta inferiore alla capacità. La creazione Booking:

- acquisisce il lock del Client e dell'occorrenza in ordine stabile;
- ricalcola overlap e occupancy nella stessa transazione;
- impedisce allo stesso Client prenotazioni temporali sovrapposte;
- crea il `PENDING` solo se rimane capacità.

La race per l'ultimo posto produce al massimo un successo. Due richieste concorrenti sovrapposte dello stesso Client producono al massimo un successo. Intervalli adiacenti sono ammessi.

La conferma cambia solo il Booking da `PENDING` a `CONFIRMED`: entrambi occupano un posto, quindi l'occorrenza non diventa globalmente `BOOKED`.

## Modifiche, disattivazione e blocco

Update e deactivate sono immediate: non accettano una data di efficacia arbitraria. L'impatto è calcolato sulle finestre future dalla data/ora corrente. Se esistono Booking `PENDING` o `CONFIRMED`, `changeReason` è obbligatorio e l'audit viene conservato in `availability_rule_changes`; il relativo `effective_from` storico è la data business corrente.

Le occorrenze future non più prodotte vengono disattivate, non eliminate. Le occorrenze e i Booking passati non sono riscritti. Nessuna modifica sposta automaticamente un Client e la capacità non può scendere sotto la massima occupancy concorrente esistente.

Il blocco di una singola occorrenza:

- è ammesso solo per una finestra futura;
- preserva i Booking esistenti;
- richiede una motivazione se coinvolge Booking occupanti;
- registra l'operazione in `availability_slot_changes`.

Lo sblocco è anch'esso limitato alle occorrenze future e viene auditato. Il `PATCH /api/v1/availability/{slotId}` legacy non può ripianificare un'occorrenza generata da una regola settimanale.

Gli snapshot del Booking conservano in `booking_request_items` inizio, fine e luogo scelti: modifiche successive alla regola o all'occorrenza non alterano lo storico.

## API

| Metodo | Path | Uso |
|---|---|---|
| `POST` | `/api/v1/availability/weekly-rules` | Crea una finestra ricorrente con durate multiple e materializza il calendario |
| `GET` | `/api/v1/availability/weekly-rules/my` | Elenca le regole attive del PT |
| `GET` | `/api/v1/availability/weekly-rules/{ruleId}/impact` | Anticipa i Booking coinvolti da una modifica immediata |
| `PUT` | `/api/v1/availability/weekly-rules/{ruleId}` | Sostituisce immediatamente la configurazione futura |
| `PATCH` | `/api/v1/availability/weekly-rules/{ruleId}/deactivate` | Disattiva immediatamente la regola preservando lo storico |
| `GET` | `/api/v1/professionals/{professionalId}/availability` | Restituisce al Client solo finestre con almeno una combinazione prenotabile |
| `PATCH` | `/api/v1/availability/{slotId}/block` | Blocca una singola occorrenza; accetta `changeReason` quando necessario |
| `PATCH` | `/api/v1/availability/{slotId}/unblock` | Rimuove l'eccezione futura |
| `POST` | `/api/v1/bookings` | Prenota `availabilitySlotId`, `startDateTime` e `durationMinutes` |

Gli endpoint manuali `POST /api/v1/availability` e `PATCH /api/v1/availability/{slotId}` restano temporaneamente presenti per compatibilità con il contratto precedente, ma non sono usati dalla UI settimanale e non costituiscono il modello principale.

## Minimizzazione dati Client

La response Client contiene soltanto `occurrenceId`, `windowStart`, `windowEnd`, `allowedDurations`, `startIntervalMinutes`, `location`, `capacity` e `bookableOptions`. Ogni opzione raggruppa per `startDateTime` le sole durate ancora prenotabili rispetto sia alla capacità globale sia agli overlap `PENDING` o `CONFIRMED` del Client chiamante con lo stesso professionista. Il server carica in batch gli intervalli `PENDING` e `CONFIRMED` del professionista e calcola in memoria le combinazioni usando la stessa policy temporale della create Booking; non esegue query per occurrence, start o durata. Non espone regola, occupancy, capacità residua, numero o identità degli altri Client, note o stato operativo interno. Una finestra senza opzioni viene omessa.

Il caricamento iniziale PT sincronizza l'horizon una sola volta tramite la lettura delle occurrence; l'elenco delle regole è una lettura pura. Le response PT riusano la stessa fotografia batch dell'occupancy per tutte le occurrence restituite.

## Fuori scope

Restano esclusi Nutritionist Availability, notifiche, rescheduling, waitlist, pagamenti, sedi strutturate, calendari esterni e ricorrenze Booking.
