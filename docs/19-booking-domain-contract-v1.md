# Booking Domain Contract V1

Documento normativo corrente del workflow Booking Client ↔ Personal Trainer. In caso di conflitto con note storiche di sprint o audit legate a commit precedenti, questo documento e il codice corrente prevalgono.

## 1. Ambito e outcome

Un Client collegato consulta la Availability di un `PERSONAL_TRAINER`, seleziona una combinazione restituita in `bookableOptions` e crea un Booking `PENDING`. Il PT può confermare oppure rifiutare con motivazione. Un Booking `CONFIRMED` può essere cancellato dal Client o dal PT con motivazione. Entrambi consultano liste, dettaglio e storico.

Booking di Nutritionist, reschedule, notifiche, pagamenti, realtime, `COMPLETED`, no-show, filtri e paginazione non appartengono a V1.

## 2. Stati e tempo

Gli unici stati persistiti sono `PENDING`, `CONFIRMED`, `REJECTED` e `CANCELLED`.

Il frontend deriva soltanto per presentazione:

- `UPCOMING` quando `now < scheduledStart`;
- `IN_PROGRESS` quando `scheduledStart <= now < scheduledEnd`;
- `PAST` quando `now >= scheduledEnd`.

`PENDING + PAST` è presentato come “Richiesta scaduta”; `CONFIRMED + PAST` come “Appuntamento passato”. Lo storico passato resta accessibile e leggibile, con prominenza visiva ridotta.

Per ogni mutation `bookingEnd = MAX(BookingRequestItem.scheduledEnd)`. La mutation può procedere, se il lifecycle lo consente, soltanto quando `now < bookingEnd`; a fine esatta e oltre restituisce `409 BOOKING_REQUEST_ENDED`. Confirm, reject e cancel sono quindi consentiti durante `IN_PROGRESS`. Lo slot live non definisce il limite temporale della mutation.

## 3. Transizioni e motivazioni

| Attore | Transizione              | Reason       |
| ------ | ------------------------ | ------------ |
| PT     | `PENDING -> REJECTED`    | obbligatoria |
| PT     | `CONFIRMED -> CANCELLED` | obbligatoria |
| Client | `PENDING -> CANCELLED`   | facoltativa  |
| Client | `CONFIRMED -> CANCELLED` | obbligatoria |

La reason è plain text, viene trimmata, whitespace diventa `null` e il massimo è 1000 caratteri. Quando è obbligatoria deve essere non null e non blank. Un'assenza condizionalmente invalida produce `400 VALIDATION_ERROR` e un field error `reason`.

`BookingRequest.status` non ha setter pubblico. `BookingRequest.reject(rejectedAt, rejectionReason)` rifiuta reason null/blank e `BookingRequest.cancel(cancelledAt, cancellationReason, cancelledBy)` rifiuta actor null; i metodi impostano insieme status, timestamp e metadata. JPA può comunque idratare i null legacy senza esporre una nuova transizione invalida.

## 4. Actor e compatibility

`BookingCancellationActor` è un enum dedicato con `CLIENT` e `PROFESSIONAL`. È determinato esclusivamente dal server in base al principal; non è accettato dal payload.

V9 aggiunge nullable:

- `rejection_reason VARCHAR(1000)`;
- `cancellation_reason VARCHAR(1000)`;
- `cancelled_by VARCHAR(32)`.

Non esiste backfill. Un record legacy `REJECTED` può avere `rejectionReason = null`; un record legacy `CANCELLED` può avere `cancellationReason = null` e `cancelledBy = null`. La UI non inventa motivazione o attore.

## 5. API

- `POST /api/v1/bookings`: Client, crea da `availabilitySlotId`, `startDateTime`, `durationMinutes`, nota nullable;
- `GET /api/v1/bookings/client`: lista Client;
- `GET /api/v1/bookings/professional`: inbox PT;
- `GET /api/v1/bookings/{id}`: dettaglio partecipante;
- `PATCH /api/v1/bookings/{id}/confirm`: PT, nessun body;
- `PATCH /api/v1/bookings/{id}/reject`: PT, body obbligatorio `{ "reason": "..." }`;
- `PATCH /api/v1/bookings/{id}/cancel`: Client/PT, body reason opzionale a livello HTTP e requiredness di dominio.

Il cancel supporta body assente, `{}`, `{"reason":null}`, whitespace e reason valida. `BookingSummaryResponse` resta minimale. `BookingDetailResponse` include `rejectionReason`, `cancellationReason` e `cancelledBy`, tutti nullable.

## 6. Authorization e neutralità

Ogni capability Booking Professional — lista, detail, confirm, reject e cancel — richiede `PERSONAL_TRAINER`. Un `NUTRITIONIST` riceve `403 BOOKING_SPECIALIZATION_NOT_ALLOWED` prima dell'inferenza sulla Booking. Un PT valido su risorsa inesistente o estranea riceve `404 BOOKING_REQUEST_NOT_FOUND`, indistinguibile.

Ownership, principal attivo, session auth, CSRF e role checking restano server-side. Il payload non può assegnare actor, status, participant o professional id. Reason è renderizzata come testo React e mai tramite `dangerouslySetInnerHTML`.

## 7. Concorrenza

Restano invariati lock pessimisti, transaction boundary, last-seat protection, overlap Client e semantica di capacità. `PENDING` e `CONFIRMED` occupano capacità; `REJECTED` e `CANCELLED` la liberano. Reason, actor e guard temporale vengono applicati all'interno della transazione esistente.

## 8. Frontend

Tutte le response Booking attraversano decoder runtime fail-closed. Enum sconosciuti, timestamp malformati e schedule incoerenti causano decoding failure. Per il detail multi-item, start/end/duration aggregati sono rispettivamente `MIN(item.start)`, `MAX(item.end)` e `SUM(item.duration)`: eventuali gap fra item sono validi. Il summary, privo degli item, non impone che lo span equivalga alla durata. Il decoder business `OffsetDateTime` è condiviso con Availability.

La pagina Availability Client consuma esclusivamente `bookableOptions` e usa l'identità `occurrenceId + startDateTime + durationMinutes`; `occurrenceId` viene inviato come `availabilitySlotId`. Nessun calcolo frontend ricostruisce bookability da capacity, finestre o occupancy.

I GET usano `AbortController`, generation fencing, cleanup mount e retry. Create e mutation detail sono legate a route identity, generation e mount, abortite al cambio route/unmount e ignorano anche `catch`/`finally` stale; le pagine usano inoltre un remount keyed. Le mutation usano lock sincrono, disabilitano i controlli e non applicano stato ottimistico. Su `409` non avviene retry della mutation: il detail corrente viene ricaricato soltanto se route e request sono ancora pertinenti. Una create `404 AVAILABILITY_SLOT_NOT_FOUND` o `409` invalida la selezione e ricarica Availability senza secondo POST.

## 9. Copy decisionale

Client:

- actor `CLIENT`: “Annullata da te”;
- actor `PROFESSIONAL`: “Annullata dal Personal Trainer”.

PT:

- actor `CLIENT`: “Annullata dal cliente”;
- actor `PROFESSIONAL`: “Annullata da te”.

Actor legacy null: “Annullata”. La rejection reason è mostrata a entrambe le parti quando presente.
