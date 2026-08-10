# Relazione CLIENT ↔ PROFESSIONAL — Implementation

## 1. Scope

Il vertical slice implementa la consultazione delle relazioni già esistenti in entrambe le direzioni:

- un utente `PROFESSIONAL` consulta la lista dei Client collegati e il dettaglio condiviso di un Client;
- un utente `CLIENT` consulta la lista dei Professional collegati e il dettaglio di un Professional.

Il flusso è in sola lettura: non crea, modifica o chiude collegamenti.

## 2. Routing

Le quattro route private reali sono:

- `/app/professional/clients`;
- `/app/professional/clients/:clientId`;
- `/app/client/professionals`;
- `/app/client/professionals/:professionalId`.

Le voci di navigazione `Clienti` e `Professionisti` puntano alle rispettive liste e usano lo stato attivo del router.

## 3. Authorization

Tutte le route sono sotto `RequireAuth`. Le route Professional applicano `RequireRole(PROFESSIONAL)` e accettano sia `PERSONAL_TRAINER` sia `NUTRITIONIST`; non richiedono una specializzazione specifica. Le route Client applicano `RequireRole(CLIENT)` e sono isolate dai ruoli Professional.

Le guard frontend governano la navigazione, ma l'autorizzazione effettiva resta backend e richiede una relazione attiva e profili leggibili.

## 4. API

Il frontend usa quattro endpoint:

- `GET /api/v1/clients/my`;
- `GET /api/v1/clients/{clientId}`;
- `GET /api/v1/professionals/my`;
- `GET /api/v1/professionals/{professionalId}`.

Le chiamate usano la foundation HTTP session-based, `AbortSignal` e `invalidateOn401: true`.

## 5. Client shared contract

La lista Client espone `id`, `firstName`, `lastName` e `profileImageUrl`.

Il dettaglio Client condiviso espone esattamente:

- `id`;
- `firstName`;
- `lastName`;
- `profileImageUrl`;
- `primaryGoal`;
- `operationalStatus`;
- `birthDate`;
- `heightCm`;
- `gender`.

PT e nutrizionisti ricevono lo stesso contratto.

## 6. Explicitly excluded Client data

Il contratto condiviso non espone:

- `medicalNotes`;
- `injuryNotes`;
- `notes`;
- email o altri dati account;
- flag tecnici, audit o dati interni del collegamento.

Le note sono persistite nel profilo owner, ma la loro esclusione dal contratto corrente è intenzionale. Un'eventuale condivisione futura richiede una decisione specifica sulle informazioni sensibili; questo documento non stabilisce che non potranno mai essere condivise.

## 7. Professional contract

La summary Professional contiene `id`, `firstName`, `lastName`, `profileImageUrl`, `specialization`, `operationalStatus` e `active`.

Il dettaglio aggiunge `phoneNumber`, `bio`, `workplaceName`, `city`, `instagramUrl` e `websiteUrl`.

`active` è un campo tecnico validato dal decoder, ma non è presentato nella UI.

## 8. Runtime validation

Le response sono trattate come input non fidato e decodificate prima dell'uso. I decoder verificano oggetti e array JSON, ID interi positivi e safe, enum ammessi, date `LocalDate` ISO reali, numeri finiti, booleani, stringhe e nullability. L'output viene ricostruito tramite whitelist: proprietà aggiuntive del backend non entrano nei modelli di pagina. Un payload di successo non conforme produce `UnexpectedResponseError`.

## 9. Lifecycle

Liste e dettagli usano `AbortController`, una generation monotona e un fence sulla risorsa corrente. Retry same-tick, cambio route A→B, risposte stale, unmount e remount di `StrictMode` non possono applicare dati o errori di una richiesta superata. `AbortError` non viene presentato come errore utente.

## 10. Security

- `401` usa l'invalidazione centralizzata della sessione, senza logout locale;
- `403` resta distinto e non viene neutralizzato;
- le risorse assenti o fuori scope producono un `404` neutro (`CLIENT_NOT_FOUND` o `PROFESSIONAL_NOT_FOUND`) senza mostrare messaggi backend grezzi;
- i parametri route invalidi non generano richieste HTTP;
- i link esterni sono resi soltanto se usano `http` o `https`;
- il browser non persiste JWT, Bearer token o altri token di autenticazione.

## 11. UX

Entrambi i flussi coprono loading, lista popolata, empty state, errore con retry e dettaglio. Le pagine usano heading gerarchici, un unico `h1`, semantica di lista, `dl` per i dettagli, `status`/`alert`, navigazione da tastiera, focus visibile e avatar accessibili. Layout e testi sono mobile-first e gestiscono nomi, bio, workplace e URL lunghi senza dipendere da nuovi breakpoint specifici.

## 12. Tests

Le suite mirate sono:

- `clientsApi.test.ts`: contratto lista/dettaglio Client, decoder, ID, enum, data, nullability, whitelist, abort e semantica HTTP;
- `professionalsApi.test.ts`: contratto Professional, campi nullable, whitelist, abort e semantica HTTP;
- `ProfessionalClientsPages.test.tsx`: lista/dettaglio, PT/NUT, stati UX, retry, route validation, lifecycle, StrictMode e accessibilità;
- `ClientProfessionalsPages.test.tsx`: lista/dettaglio, contatti e link sicuri, stati UX, lifecycle, StrictMode e accessibilità;
- `AppRoutes.test.tsx`: integrazione delle quattro route e isolamento per ruolo.

La regression completa backend e frontend e i quality gate restano parte della certificazione del vertical slice.

## 13. Out of scope

Restano fuori dal vertical slice:

- condivisione delle note sensibili;
- Availability e Booking frontend;
- dashboard con dati;
- ricerca, filtri, sorting e paginazione;
- gestione manuale dei collegamenti;
- workout, nutrition, measurements, upload avatar, notifiche e chat.

## 14. Future progression

Availability e Booking sono candidati naturali per un vertical slice successivo perché riusano relazioni, ruoli e dettagli già disponibili. Non sono implementati né attivati da questo intervento e richiedono una pianificazione separata.
