# Validation Rules — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce le principali regole di validazione del sistema.

Lo scopo è chiarire:
- quali controlli applicare ai dati in ingresso
- quali regole di business devono bloccare le operazioni non valide
- quali vincoli devono essere verificati prima di creare, aggiornare o collegare entità

---

## 2. Principi generali

### 2.1 Due livelli di validazione
Nel progetto si distinguono due livelli principali di validazione:

- **validazioni strutturali**
- **validazioni di business**

### 2.2 Validazioni strutturali
Riguardano forma e presenza dei dati, ad esempio:
- campi obbligatori
- formato email
- lunghezza password
- valori null non consentiti

Queste validazioni possono essere gestite con:
- Bean Validation
- controlli DTO request

### 2.3 Validazioni di business
Riguardano le regole reali del dominio, ad esempio:
- massimo 3 professionisti per cliente
- divieto di self-link
- slot non sovrapposti
- impossibilità di usare un codice invito scaduto

Queste validazioni devono essere gestite nel:
- service layer

---

## 3. Validazioni registrazione professionista

### 3.1 Campi obbligatori
In fase di registrazione professionista devono essere obbligatori:
- `firstName`
- `lastName`
- `email`
- `password`
- `specialization`

### 3.2 Email
L’email deve:
- essere presente
- avere formato valido
- essere univoca nel sistema
- essere salvata in forma normalizzata, se previsto

### 3.3 Password
La password deve rispettare almeno queste regole:
- minimo **8 unità UTF-16** (`@Size(min=8)` / lunghezza `String` Java)
- massimo **72 byte in codifica UTF-8** con semantica Java (`String.getBytes(UTF_8)`, inclusi i replacement sui surrogate isolati)
- almeno **una lettera maiuscola ASCII** (`[A-Z]`)
- almeno **un numero ASCII** (`[0-9]`)
- almeno **un carattere speciale** fuori da `[A-Za-z0-9]`

Il limite massimo è calcolato sui byte UTF-8, non sul solo numero di unità UTF-16. Il backend rifiuta il valore oltre soglia prima dell’hashing e non tronca, normalizza, fa trim o trasforma la password. Il frontend della registrazione PROFESSIONAL pre-valida in modo coerente; il server resta autoritativo. Dettaglio client: [`docs/frontend/04-professional-onboarding-implementation.md`](frontend/04-professional-onboarding-implementation.md).

### 3.4 Stato iniziale account
Alla registrazione, sia l’account professionista sia il cliente devono nascere con:
- `accountStatus = PENDING_VERIFICATION`
- `emailVerified = false`

### 3.5 Blocco funzioni operative
Finché l’email non è verificata, l’utente non può effettuare login. Il professionista non può inoltre:
- generare codici invito
- collegare clienti
- utilizzare funzionalità operative riservate

---

## 4. Validazioni registrazione cliente con codice invito

### 4.1 Regola generale
Il cliente non può registrarsi liberamente.  
La registrazione cliente richiede un codice invito valido.

### 4.2 Password cliente
A livello di **contratto backend** la policy password del cliente è la stessa della registrazione professionista (§3.3): minimo 8 unità UTF-16, complessità ASCII reale, massimo 72 byte UTF-8 con semantica Java, senza trim/normalize/truncate lato server.

Il frontend di registrazione CLIENT non è ancora collegato/implementato secondo la maturity corrente (vedi [`docs/frontend/01-frontend-functional-map-mvp.md`](frontend/01-frontend-functional-map-mvp.md)); questa sezione non afferma maturity frontend.

### 4.3 Validazioni sul codice invito
Il codice deve:
- esistere
- essere associato a un professionista esistente
- non essere già usato
- non essere scaduto

### 4.4 Registrazione entro scadenza
L’utente che utilizza il codice invito deve completare la registrazione entro la scadenza del codice.

La scadenza è un `Instant` calcolato come 168 ore reali dal `Clock` applicativo, non come sette giorni civili; il passaggio DST non modifica la durata. Analogamente, il token di verifica email dura esattamente 24 ore reali e al confine `now == expiresAt` è già scaduto.

Se la registrazione non viene completata entro tale termine:
- il codice non è più valido
- la registrazione associata non deve essere considerata valida/completata

### 4.5 Consumo del codice
Il codice invito deve essere considerato usato quando:
- la registrazione cliente viene completata con successo

A quel punto:
- `used = true`
- `usedAt` valorizzato

### 4.6 Collegamento finale
Il collegamento cliente-professionista deve essere creato solo dopo:
- registrazione completata correttamente
- validazione finale del codice invito

La stessa transazione crea il cliente pending e il token email, crea il link attivo e consuma l'invito. Il link non rende il cliente leggibile finché `accountStatus` non diventa `ACTIVE`. Una conferma scaduta o fallita non ripristina l'invito e un fallimento della registrazione non deve lasciare cliente, link o token parziali.

### 4.7 Conferma email uniforme

La conferma usa `POST /api/v1/auth/email-verification/confirm` con un token non blank di massimo 500 caratteri nel body JSON. Il primo consumo valido porta l'utente a `ACTIVE`, imposta `emailVerified=true` e valorizza `usedAt`. Il secondo POST è idempotente soltanto se lo stato finale è coerente e non modifica `usedAt`. Un token inesistente produce 404; un token con `expiresAt <= now` produce `410 Gone`. Il precedente GET mutante non è più disponibile.

### 4.8 Reinvio senza enumerazione

Il reinvio usa `POST /api/v1/auth/email-verification/resend` con un'email obbligatoria, valida e lunga al massimo 100 caratteri. Dopo `trim` e lowercase, ogni input sintatticamente valido restituisce lo stesso `202 Accepted`: account inesistente, verificato, non idoneo o in cooldown non sono distinguibili. Solo `CLIENT` e `PROFESSIONAL` con profilo attivo, `PENDING_VERIFICATION` ed `emailVerified=false` possono generare un nuovo token.

Il cooldown è attivo quando `now < latestToken.createdAt + 60 secondi`; al boundary esatto il reinvio è consentito. Sotto lock pessimista sull'utente, tutti i precedenti token `used=false` vengono marcati `used=true` con `usedAt=now`, quindi viene creato un solo token da 24 ore. I token già usati non cambiano. Questa invalidazione riusa semanticamente `used/usedAt` perché lo schema corrente non contiene campi di revoca. Invito e `ProfessionalClientLink` non vengono modificati; token, email, stato e tempo residuo non compaiono nella risposta o nei log. La richiesta email viene pubblicata dopo la creazione del nuovo token e consumata solo dopo commit; errori del sender non annullano dati o risposta uniforme. `app.email.verification-page-url` è obbligatoria, assoluta e priva di query e fragment: HTTP è ammesso solo per loopback, mentre un host remoto richiede HTTPS. In modalità `SMTP` sono obbligatori mittente valido, host, porta e timeout positivi; con `auth=true` sono obbligatori anche username e password. Retry, outbox e rate limiting distribuito restano fuori perimetro.

---

## 5. Validazioni collegamento professionista-cliente

### 5.1 Cliente massimo 3 professionisti
Un cliente non può avere più di:
- **3 professionisti attivi**

### 5.2 No self-link
Il sistema non deve permettere che:
- un professionista si colleghi come cliente a sé stesso
- venga creato un collegamento logicamente riferito allo stesso account

### 5.3 No duplicati attivi
Tra lo stesso professionista e lo stesso cliente non può esistere:
- più di un collegamento attivo

### 5.4 Stato professionista
Solo un professionista con:
- `accountStatus = ACTIVE`
- email verificata
- profilo `active = true`
può collegare clienti tramite invito valido.

---

## 6. Validazioni AvailabilitySlot — Implementate

### 6.1 Soggetto autorizzato

Gli slot di disponibilità possono essere creati e gestiti solo da un professionista:

- autenticato;
- con `accountStatus = ACTIVE`;
- con email verificata;
- con profilo `active = true`;
- con specializzazione `PERSONAL_TRAINER`.

### 6.2 Intervallo temporale valido

Per ogni slot:

- `startDateTime` è obbligatorio in creazione;
- `endDateTime` è obbligatorio in creazione;
- `startDateTime` deve essere precedente a `endDateTime`;
- in creazione o aggiornamento, `startDateTime` deve essere nel futuro.

Non è consentito creare o aggiornare uno slot già scaduto.

### 6.2.1 Contratto temporale HTTP

I campi HTTP `startDateTime` ed `endDateTime` sono `OffsetDateTime` ISO-8601 con offset obbligatorio. La zona business autoritativa è `Europe/Rome`; esempi validi sono `2026-07-13T17:30:00+02:00` e `2026-01-13T17:30:00+01:00`.

Per ogni valore il backend consulta `ZoneRules.getValidOffsets` sull'ora civile:

- accetta il valore solo quando esiste un unico offset valido e coincide con quello ricevuto;
- rifiuta valori senza offset, `Z` o altri offset incoerenti;
- rifiuta il gap primaverile, senza spostare l'orario in avanti;
- rifiuta l'overlap autunnale anche se viene fornito uno dei due offset validi;
- accetta precisione massima al secondo e rifiuta frazioni non nulle, senza troncare o arrotondare.

Dopo la validazione individuale, `startDateTime` deve precedere `endDateTime` nel confronto tra i rispettivi `Instant`. I casi non validi producono `400`; le violazioni semanticamente parseabili usano `VALIDATION_ERROR`, mentre un formato non deserializzabile usa `MALFORMED_REQUEST`.

Entity, repository e query usano `Instant`; i valori sono persistiti in UTC su `DATETIME(6)`. Il mapper converte le request validate in istanti e ricostruisce le response con l'offset effettivo di `Europe/Rome`, senza dipendere dalla timezone JVM.

### 6.3 Nessuna sovrapposizione

Per lo stesso professionista non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

Nel caso di aggiornamento, il controllo overlap esclude lo slot che si sta modificando.

### Protezione da operazioni concorrenti

Il controllo di sovrapposizione è protetto anche in presenza di operazioni simultanee.

Durante la creazione o l’aggiornamento di uno slot, il backend acquisisce un lock pessimista in scrittura sul `ProfessionalProfile` proprietario.

Questa scelta serializza le operazioni Availability dello stesso professionista e impedisce che due richieste concorrenti possano entrambe superare il controllo overlap e salvare slot temporaneamente incompatibili.

### 6.4 Stato iniziale

Alla creazione uno slot nasce con:

- `status = AVAILABLE`;
- `active = true`.

### 6.5 Ownership

Il professionista può:

- leggere i propri slot;
- aggiornare solo i propri slot attivi;
- bloccare solo i propri slot attivi;
- sbloccare solo i propri slot attivi.

### 6.6 Aggiornamento slot

Uno slot può essere aggiornato solo se:

- esiste;
- appartiene al professionista autenticato;
- è attivo;
- è in stato `AVAILABLE`;
- il body contiene almeno un campo modificabile;
- il nuovo intervallo temporale è valido;
- il nuovo inizio è nel futuro;
- il nuovo intervallo non crea sovrapposizioni.
- non deve esistere una richiesta booking `PENDING` attiva collegata allo slot.
- lo slot non deve essere già stato coinvolto in alcuna richiesta booking, anche se successivamente rifiutata o cancellata.

Se lo slot possiede una richiesta booking `PENDING`, la data e l’orario proposti al cliente non possono essere modificati finché la richiesta non viene gestita.

Uno slot già collegato ad almeno una richiesta booking non può più essere ripianificato modificandone data o ora.

Questa regola protegge l’integrità storica delle prenotazioni: il booking deve continuare a riferirsi all’intervallo temporale originariamente scelto dal cliente.

Per rendere disponibile un nuovo giorno o orario, il professionista deve creare un nuovo slot availability.

### 6.7 Blocco e sblocco

Sono consentite solo le transizioni:

- `AVAILABLE -> BLOCKED`;
- `BLOCKED -> AVAILABLE`.

Non è consentito bloccare o sbloccare uno slot `BOOKED`.

Non è inoltre consentito bloccare manualmente uno slot `AVAILABLE` se esiste una richiesta booking `PENDING` attiva collegata.

In questo caso il professionista deve prima gestire la richiesta pendente tramite il flusso Booking previsto, ad esempio rifiutandola.

### 6.8 Lettura disponibilità lato cliente

Un cliente può leggere gli slot disponibili di un professionista solo se:

- è autenticato;
- ha profilo attivo;
- il professionista esiste ed è attivo;
- esiste un collegamento attivo cliente-professionista.

La lettura lato cliente restituisce solo slot:

- attivi;
- in stato `AVAILABLE`;
- con `startDateTime` nel futuro;
- senza una richiesta booking `PENDING` attiva collegata.

Gli slot rimasti `AVAILABLE` ma ormai scaduti non vengono esposti al cliente.

Gli slot formalmente `AVAILABLE` ma già interessati da una richiesta booking `PENDING` non vengono più mostrati come disponibilità prenotabili.

### 6.9 Coordinamento con booking pending

Uno slot può rimanere in stato `AVAILABLE` anche quando esiste una richiesta booking in stato `PENDING`, perché la prenotazione non è ancora stata confermata.

Tuttavia, in presenza di una richiesta `PENDING` attiva, lo slot è considerato logicamente impegnato rispetto alle operazioni manuali del professionista.

Di conseguenza, non sono consentiti:

- aggiornamento della data o dell’orario dello slot;
- blocco manuale dello slot.

Le operazioni di aggiornamento e blocco caricano lo slot con lock pessimista in scrittura e verificano l’assenza di richieste pendenti prima di applicare modifiche.

La presenza di una richiesta `PENDING` attiva incide anche sulla lettura lato cliente.

Uno slot interessato da una richiesta pendente:

- non può essere modificato;
- non può essere bloccato manualmente;
- non viene restituito tra le disponibilità prenotabili agli altri clienti.

### 6.10 Integrità storica dello slot

Uno slot già utilizzato in una richiesta booking mantiene immutabile il proprio intervallo temporale.

La regola vale quando la richiesta collegata è:

- `PENDING`;
- `CONFIRMED`;
- `REJECTED`;
- `CANCELLED`.

In particolare:

- con booking `PENDING`, lo slot non può essere modificato né bloccato manualmente;
- dopo un booking rifiutato o cancellato, lo slot può eventualmente ricevere nuove richieste sullo stesso intervallo;
- dopo qualsiasi richiesta booking, lo slot non può essere ripianificato modificandone data o ora.

La finalità è evitare che lo storico di una richiesta mostri un intervallo differente da quello selezionato dal cliente al momento della prenotazione.

---

## 7. Validazioni BookingRequest — Implementate

### 7.1 Soggetto autorizzato alla creazione

Una richiesta booking può essere creata solo da un cliente:

- autenticato;
- con account attivo;
- con profilo attivo.

### 7.2 Collegamento cliente-professionista

Il cliente può inviare una richiesta solo verso uno slot appartenente a un professionista:

- esistente;
- attivo;
- collegato attivamente al cliente.

### 7.3 Contratto attuale della richiesta

Nel backend attuale una richiesta booking viene creata a partire da:

- un singolo `availabilitySlotId`.

Di conseguenza, ogni booking creato tramite API contiene attualmente:

- un solo `BookingRequestItem`.

La struttura dati resta predisposta a una futura evoluzione multi-slot, ma tale comportamento non è implementato nell’API attuale.

### 7.4 Prenotabilità dello slot

Per creare una richiesta, lo slot selezionato deve:

- esistere;
- essere attivo;
- appartenere al professionista collegato;
- essere in stato `AVAILABLE`;
- avere `startDateTime` nel futuro;
- non avere già una richiesta `PENDING` attiva associata.

Uno slot `AVAILABLE` ma ormai scaduto non è prenotabile.

### 7.4.1 Riserva logica dello slot

La creazione di una richiesta booking `PENDING` non modifica immediatamente lo stato dello slot in `BOOKED`.

Lo slot resta `AVAILABLE` fino alla conferma del professionista, ma viene protetto rispetto alle operazioni manuali incompatibili.

Finché esiste una richiesta `PENDING` attiva sullo slot:

- non può essere creata una seconda richiesta `PENDING` sullo stesso slot;
- il professionista non può modificare lo slot;
- il professionista non può bloccare manualmente lo slot;
- lo slot non viene esposto al cliente come disponibilità prenotabile.

### 7.4.2 Integrità temporale dello storico booking

Quando una richiesta booking viene creata, lo slot selezionato entra nello storico della richiesta.

Anche dopo una transizione verso:

- `REJECTED`;
- `CANCELLED`;

lo slot non può essere ripianificato modificandone l’intervallo temporale.

Lo slot può essere riutilizzato per una nuova richiesta soltanto mantenendo invariati giorno e orario originari e rispettando tutte le altre regole di prenotabilità.

### 7.5 Nota della richiesta

La `note` è facoltativa.

Se presente:

- non può superare `1000` caratteri;
- viene normalizzata tramite rimozione degli spazi iniziali e finali;
- se dopo la normalizzazione è vuota, viene trattata come assente.

### 7.6 Stato iniziale

Alla creazione:

- `status = PENDING`;
- `active = true`.

### 7.7 Conferma richiesta

Una richiesta può essere confermata solo:

- dal professionista coinvolto;
- se si trova in stato `PENDING`;
- se lo slot collegato è ancora `AVAILABLE`;
- se lo slot collegato non è scaduto.

Alla conferma:

- booking `PENDING -> CONFIRMED`;
- slot `AVAILABLE -> BOOKED`.

Un booking pending con slot ormai scaduto non può essere confermato.

### 7.8 Rifiuto richiesta

Una richiesta può essere rifiutata solo:

- dal professionista coinvolto;
- se si trova in stato `PENDING`.

Al rifiuto:

- booking `PENDING -> REJECTED`;
- lo slot resta `AVAILABLE`.

### 7.9 Cancellazione richiesta

Il cliente coinvolto può cancellare:

- booking `PENDING`, lasciando lo slot `AVAILABLE`;
- booking `CONFIRMED`, riportando lo slot a `AVAILABLE`.

Il professionista coinvolto può cancellare:

- solo booking `CONFIRMED`, riportando lo slot a `AVAILABLE`.

Il professionista non può cancellare una richiesta `PENDING`: deve rifiutarla.

### 7.10 Ownership e dettaglio

Il dettaglio di una richiesta può essere letto solo:

- dal cliente coinvolto;
- dal professionista coinvolto.

Un utente estraneo alla richiesta non può visualizzarla né modificarne lo stato.
---

## 8. Validazioni WorkoutPlan — Pianificate, non implementate

### 8.1 Soggetto autorizzato
Una scheda workout può essere creata solo da un professionista con specializzazione:
- `PERSONAL_TRAINER`

### 8.2 Relazione valida
Il PT può creare una scheda workout solo per:
- un cliente collegato attivamente a lui

### 8.3 Unicità scheda attiva per coppia PT-cliente
Per una coppia:
- `personal trainer`
- `cliente`

può esistere una sola scheda workout attiva alla volta.

### 8.4 Eccezione multi-PT
Un cliente può comunque avere più schede workout attive contemporaneamente se appartengono a:
- personal trainer diversi
- tutti collegati validamente al cliente

### 8.5 Sostituzione scheda
Quando viene creata una nuova scheda workout per la stessa coppia PT-cliente:
- la precedente deve passare a `active = false`
- la nuova diventa l’unica attiva per quella coppia

### 8.6 Struttura minima coerente
Una scheda workout valida deve avere almeno:
- struttura coerente
- settimane/giorni correttamente associati
- almeno un contenuto utile se prevista come scheda completa

---

## 9. Validazioni NutritionPlan — Pianificate, non implementate

### 9.1 Soggetto autorizzato
Un piano alimentare può essere creato solo da un professionista con specializzazione:
- `NUTRITIONIST`

### 9.2 Relazione valida
Il nutrizionista può creare un piano solo per:
- un cliente collegato attivamente a lui

### 9.3 Unicità piano attivo
Per una coppia:
- `nutrizionista`
- `cliente`

può esistere un solo piano alimentare attivo alla volta.

### 9.4 Sostituzione piano
Quando viene creato un nuovo piano per la stessa coppia:
- il piano precedente passa a `active = false`
- il nuovo diventa l’unico attivo

### 9.5 Struttura minima coerente
Il piano alimentare deve avere una struttura coerente e collegamenti validi tra:
- piano
- settimane
- giorni
- contenuti giornalieri

---

## 10. Validazioni WorkoutFeedback — Pianificate, non implementate

### 10.1 Utente autorizzato
Il feedback workout può essere inviato solo da:
- un cliente autenticato

### 10.2 Relazione valida
Il cliente può inviare feedback workout solo verso:
- un PT a cui è collegato attivamente

### 10.3 Giorno valido
Il `WorkoutDay` indicato nel feedback deve:
- esistere
- appartenere a una scheda reale
- appartenere a una scheda del PT destinatario
- appartenere al cliente che invia il messaggio

### 10.4 Messaggio obbligatorio
Il campo `message` deve essere:
- obbligatorio
- non vuoto
- non composto solo da spazi

---

## 11. Validazioni NutritionFeedback — Pianificate, non implementate

### 11.1 Utente autorizzato
Il feedback nutrizione può essere inviato solo da:
- un cliente autenticato

### 11.2 Relazione valida
Il cliente può inviare feedback nutrizione solo verso:
- un nutrizionista a cui è collegato attivamente

### 11.3 Giorno valido
Il `NutritionDay` indicato nel feedback deve:
- esistere
- appartenere a un piano reale
- appartenere al nutrizionista destinatario
- appartenere al cliente che invia il messaggio

### 11.4 Messaggio obbligatorio
Il campo `message` deve essere:
- obbligatorio
- non vuoto
- non composto solo da spazi

---

## 12. Validazioni ClientMeasurement — Pianificate, non implementate

### 12.1 Cliente valido
Ogni misurazione deve riferirsi a:
- un cliente esistente

### 12.2 Campi obbligatori minimi
Per una `ClientMeasurement` devono essere obbligatori almeno:
- `client`
- `recordedAt`
- `weightKg`

### 12.3 Inserimento autorizzato
Una misurazione può essere inserita solo:
- dal cliente stesso
- da un professionista collegato attivamente a quel cliente

### 12.4 Coerenza logica
Il sistema non deve permettere a un professionista non collegato di:
- creare
- alterare
- registrare misurazioni per quel cliente

### 12.5 Storico
Le misurazioni rappresentano dati storici.  
Per questo:
- normalmente si aggiunge una nuova rilevazione
- non si sovrascrive una vecchia rilevazione come comportamento standard

---

## 13. Validazioni generali su campi testuali

### 13.1 Stringhe obbligatorie
I campi testuali obbligatori devono:
- essere presenti
- non essere vuoti
- non contenere solo spazi

### 13.2 Stringhe facoltative
I campi facoltativi possono essere null, ma se valorizzati devono essere trattati con:
- trim
- controllo lunghezza massima, se definita

### 13.3 URL facoltativi

I campi URL attualmente gestiti nel profilo professionista sono:

- `instagramUrl`;
- `websiteUrl`.

Nel `PATCH /api/v1/me/profile` seguono queste regole:

| Valore ricevuto | Comportamento |
|---|---|
| campo omesso oppure `null` | il valore già salvato non viene modificato |
| valore che inizia con `http://` oppure `https://` | l’URL viene accettato e salvato |
| stringa vuota oppure composta solo da spazi | l’URL già salvato viene rimosso e memorizzato come `null` |
| valore senza protocollo valido | la richiesta viene rifiutata per errore di validazione |

La rimozione tramite stringa vuota permette al frontend di offrire un form profilo completo, nel quale il professionista può eliminare un link precedentemente inserito.

Il campo `profileImageUrl` non rientra ancora in un flusso frontend/API dedicato di upload o aggiornamento immagine profilo; tale funzionalità resta futura.

### 13.4 Minimizzazione delle response Clients

La validità del collegamento autorizza l'accesso all'endpoint, ma non rende visibile l'intero `ClientProfile`.

- la lista `/api/v1/clients/my` ammette come chiavi pubbliche soltanto `id`, `firstName`, `lastName` e `profileImageUrl`;
- il dettaglio `/api/v1/clients/{clientId}` ammette le stesse chiavi e `primaryGoal`;
- i test HTTP confrontano l'insieme completo delle proprietà, senza dipendere dall'ordine JSON;
- PT e nutrizionista ricevono lo stesso payload minimo.

Le regole di registrazione e aggiornamento del profilo owner restano invariate. Questa è una regola di minimizzazione tecnica del contratto API e non costituisce una dichiarazione di conformità legale.

---

## 14. Validazioni generali su stati ed enum

### 14.1 Valori ammessi
I campi enum devono accettare solo:
- valori previsti dal sistema

### 14.2 Coerenza tra stato account e azioni
Un utente non può eseguire azioni operative riservate se:
- `accountStatus` non è `ACTIVE`
- l’email richiesta non è verificata
- il profilo applicativo è `active = false`

### 14.3 Coerenza specializzazione/funzionalità

Le operazioni devono essere coerenti con la specializzazione del professionista.

### Regole attualmente implementate

- solo `PERSONAL_TRAINER` può gestire availability;
- i booking operano su slot availability e quindi riguardano professionisti `PERSONAL_TRAINER`.

### Regole pianificate

- solo `PERSONAL_TRAINER` potrà gestire schede workout;
- solo `NUTRITIONIST` potrà gestire piani alimentari.

---

## 15. Dove applicare le validazioni

### 15.1 DTO / Bean Validation

Nel backend attuale il livello DTO gestisce controlli strutturali come:

- obbligatorietà dei campi;
- formato email;
- lunghezze massime;
- pattern password;
- date obbligatorie;
- valori numerici minimi;
- lunghezza massima della nota booking.
- formato URL dei campi `instagramUrl` e `websiteUrl` del profilo professionista;
- accettazione di valori vuoti per consentire la rimozione degli URL già salvati.

Esempi:

- registrazione professionista;
- registrazione cliente;
- creazione slot availability;
- creazione booking.

### 15.2 Service layer

Il service layer gestisce le regole business reali, tra cui:

- email già registrata;
- verifica e consumo codice invito;
- massimo 3 professionisti attivi per cliente;
- divieto di self-link;
- assenza di link attivi duplicati;
- stato attivo e verifica email del professionista;
- specializzazione valida per availability;
- intervalli temporali availability validi;
- creazione e aggiornamento slot solo nel futuro;
- assenza di sovrapposizioni slot;
- ownership sugli slot;
- lettura cliente solo per professionisti collegati;
- esclusione degli slot scaduti dalla lettura cliente;
- booking solo su slot disponibili e futuri;
- assenza di booking `PENDING` duplicati sullo stesso slot;
- normalizzazione della nota booking;
- transizioni booking consentite;
- blocco conferma booking con slot scaduto;
- aggiornamento coerente dello stato slot.
- protezione da overlap Availability concorrenti tramite lock sul professionista;
- lock sullo slot durante modifica e blocco quando possono esistere booking pendenti;
- blocco modifica slot con booking `PENDING` attivo;
- blocco manuale slot con booking `PENDING` attivo;
- coordinamento transazionale tra Availability e Bookings sullo stesso slot;
- protezione da doppia creazione concorrente di booking `PENDING`;
- protezione da transizioni concorrenti della stessa richiesta booking;
- protezione da conferme concorrenti sullo stesso slot.
- esclusione dalla lettura availability lato cliente degli slot con booking `PENDING` attivo;
- blocco della ripianificazione di slot già coinvolti in richieste booking;
- tutela dell’integrità temporale dello storico delle prenotazioni;
- normalizzazione dei valori vuoti di `instagramUrl` e `websiteUrl` in `null` durante l’aggiornamento profilo.

### 15.3 Database

A livello database sono richiesti almeno:

- vincoli di unicità;
- nullabilità coerente;
- foreign key corrette;
- struttura persistente coerente con entity e relazioni implementate.

### 15.4 Regole future

Le validazioni relative a:

- workout;
- nutrition;
- feedback;
- measurements;

restano pianificate e dovranno essere confermate solo durante i rispettivi sprint.

---

## 16. Errori applicativi rilevanti

Le seguenti situazioni sono gestite o devono essere gestite tramite errori applicativi chiari.

### Area autenticazione e registrazione

- email già registrata;
- credenziali non valide;
- account non attivo;
- email professionista non verificata;
- token verifica email inesistente, scaduto o già usato.

### Area invite e collegamenti

- codice invito inesistente;
- codice invito inattivo;
- codice invito già usato;
- codice invito scaduto;
- professionista non abilitato al collegamento;
- tentativo di self-link;
- collegamento attivo duplicato;
- superamento del limite massimo di professionisti attivi per cliente.

### Area availability

- professionista non autorizzato;
- specializzazione non consentita;
- intervallo temporale non valido;
- creazione o aggiornamento slot nel passato;
- sovrapposizione slot;
- modifica di slot non disponibile;
- blocco o sblocco in stato non consentito;
- accesso cliente a professionista non collegato.
- modifica di slot con richiesta booking `PENDING` attiva;
- blocco manuale di slot con richiesta booking `PENDING` attiva.
- ripianificazione tramite modifica data/ora di uno slot già coinvolto in una richiesta booking.

### Area booking

- cliente non autorizzato;
- booking verso professionista non collegato;
- slot non trovato;
- slot non disponibile;
- slot scaduto non prenotabile;
- booking pending duplicato sullo stesso slot;
- dettaglio richiesto da utente non coinvolto;
- conferma booking con slot scaduto;
- transizione di stato non consentita;
- cancellazione professionista di booking ancora `PENDING`.

### Contratto HTTP per la validazione

Le violazioni Bean Validation restituiscono `400 VALIDATION_ERROR` con messaggio generale e una lista `fieldErrors`. Ogni elemento contiene `field` (assente per un errore globale), `code` e `message`; i valori rifiutati, `objectName`, tipi Java e dettagli di binding non vengono esposti. Più violazioni sullo stesso campo sono preservate e ordinate in modo deterministico. Body JSON vuoto, sintatticamente invalido, con enum/data/tipo non leggibile, proprietà sconosciute, contenuto trailing o chiavi duplicate restituiscono invece `400 MALFORMED_REQUEST` senza dettagli del parser.

---

## 17. Decisioni confermate

Per Support Trainer risultano attualmente confermate le seguenti regole:

- password utenti con requisiti forti;
- professionista inizialmente in attesa di verifica email;
- cliente registrabile solo tramite codice invito valido;
- massimo 3 professionisti attivi per cliente;
- divieto di self-link;
- assenza di collegamenti attivi duplicati;
- availability riservata ai `PERSONAL_TRAINER`;
- slot availability creati o aggiornati solo nel futuro;
- slot availability non sovrapposti;
- cliente autorizzato a leggere availability solo di professionisti collegati;
- slot scaduti esclusi dalle disponibilità consultabili;
- booking creato attualmente su un singolo slot;
- booking consentito solo tra cliente e professionista collegati;
- booking non creabile né confermabile su slot scaduti;
- stati booking gestiti: `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`;
- ownership e transizioni booking controllate nel service layer.
- il controllo di sovrapposizione availability è protetto da lock pessimista sul professionista;
- uno slot con booking `PENDING` è logicamente riservato rispetto a modifica e blocco manuale;
- Availability e Bookings coordinano le operazioni concorrenti sullo stesso slot tramite lock pessimisti e validazioni nel service layer.
- uno slot con booking `PENDING` attivo non viene esposto al cliente come disponibilità prenotabile;
- uno slot già coinvolto in una richiesta booking non può essere ripianificato modificandone data o ora;
- per proporre una nuova disponibilità temporale dopo uno storico booking, il professionista deve creare un nuovo slot;
- la regola protegge la coerenza storica delle richieste già create.
- gli URL `instagramUrl` e `websiteUrl` del professionista sono facoltativi, ma se valorizzati devono iniziare con `http://` o `https://`;
- il professionista può rimuovere un URL già salvato inviando una stringa vuota nel form di aggiornamento profilo;
- il frontend dovrà distinguere tra campo non modificato (`null`/omesso) e richiesta esplicita di rimozione (`""`).

Restano pianificate, ma non ancora implementate, le regole relative a:

- workout plans;
- nutrition plans;
- feedback;
- measurements.
