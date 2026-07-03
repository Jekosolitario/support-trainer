# Riepilogo lavoro Codex — Copertura test Auth e Invite

## Contesto

Questo documento riassume il lavoro svolto sul branch `test-codex` tramite Codex, usando una copia del progetto principale `Support Trainer`.

L’obiettivo della sessione era testare Codex in modo sicuro, limitando gli interventi ai soli test automatici e senza modificare il codice di produzione.

## Risultato generale

Nel primo blocco sono stati aggiunti 9 test di integrazione dedicati ad Auth e sono stati isolati 2 test esistenti tramite profilo `test`.

Nel blocco successivo sono stati aggiunti 5 test di integrazione dedicati a Invite, ruoli e access control.

È stato poi aggiunto un ulteriore blocco di 8 test dedicati al contratto pubblico di validazione dell'invito e allo stato del professionista proprietario. Complessivamente sono quindi stati aggiunti 22 test di integrazione.

Sono stati infine aggiunti 9 test di integrazione dedicati al pacchetto Client / Professional access control. Complessivamente sono quindi stati aggiunti 31 test di integrazione.

Sono stati inoltre aggiunti 2 test di integrazione dedicati alle lacune MVP residue del pacchetto Availability. Complessivamente sono quindi stati aggiunti 33 test di integrazione; il file Availability contiene ora 14 test a livello service.

Sono stati infine aggiunti 4 test di integrazione dedicati alle lacune MVP residue del pacchetto Booking. Complessivamente sono quindi stati aggiunti 37 test di integrazione; il file Booking contiene ora 17 test a livello service.

Sono stati inoltre aggiunti 7 test di integrazione dedicati alle lacune MVP residue del pacchetto Profile. Complessivamente sono quindi stati aggiunti 44 test di integrazione; `MeServiceIntegrationTest` contiene ora 9 test.

Non sono state apportate modifiche al codice di produzione.

## Test aggiunti

### Verifica email

Sono stati aggiunti test per i seguenti scenari:

* token inesistente;
* token già usato;
* token scaduto.

Comportamenti verificati:

* token inesistente → `404 Not Found`, `EMAIL_VERIFICATION_TOKEN_NOT_FOUND`;
* token già usato → `400 Bad Request`, `EMAIL_VERIFICATION_TOKEN_ALREADY_USED`;
* token scaduto → `400 Bad Request`, `EMAIL_VERIFICATION_TOKEN_EXPIRED`.

File coinvolto:

```text
backend/src/test/java/it/zuperman/support_trainer/AuthControllerEmailVerificationIntegrationTest.java
```

## Login

Sono stati aggiunti test per i seguenti scenari:

* flusso completo registrazione → verifica email → login;
* login con password errata;
* login prima della verifica email.

Comportamenti verificati:

* login corretto → `200 OK`, presenza di `accessToken` e `refreshToken`;
* password errata → `401 Unauthorized`, `AUTHENTICATION_ERROR`;
* login prima della verifica email → `403 Forbidden`, `ACCOUNT_NOT_ACTIVE`.

File creato:

```text
backend/src/test/java/it/zuperman/support_trainer/AuthControllerLoginIntegrationTest.java
```

## Registrazione

Sono stati aggiunti test per i seguenti scenari:

* registrazione professionista con email duplicata;
* registrazione cliente con invito inesistente;
* registrazione cliente con invito valido.

Comportamenti verificati:

* email professionista duplicata → `409 Conflict`, `EMAIL_ALREADY_REGISTERED`;
* invito cliente inesistente → `404 Not Found`, `INVITE_CODE_NOT_FOUND`;
* invito cliente valido → `201 Created`, cliente salvato, account attivo, collegamento professionista-cliente creato e invito consumato.

File creato:

```text
backend/src/test/java/it/zuperman/support_trainer/AuthControllerRegistrationIntegrationTest.java
```

## Invite, validate-invite e access control

### Autorizzazioni endpoint Invite

È stata aggiunta una suite di integrazione basata su autenticazione e JWT reali.

Comportamenti verificati:

* utente anonimo:
  * `GET /api/v1/invites` → `401 Unauthorized`;
  * `POST /api/v1/invites` → `401 Unauthorized`;
* utente `CLIENT` autenticato:
  * `GET /api/v1/invites` → `403 Forbidden`;
  * `POST /api/v1/invites` → `403 Forbidden`;
* utente `PROFESSIONAL` autenticato, attivo e verificato:
  * `POST /api/v1/invites` → `201 Created`;
  * `GET /api/v1/invites` → `200 OK`.

File creato:

```text
backend/src/test/java/it/zuperman/support_trainer/InviteControllerAuthorizationIntegrationTest.java
```

### Registrazione cliente tramite invito

La copertura della registrazione cliente verifica:

* registrazione con invito valido;
* rifiuto di un invito inesistente;
* creazione del collegamento professionista-cliente;
* consumo dell'invito dopo la registrazione;
* rifiuto del riutilizzo dello stesso invito per un secondo cliente con `400 Bad Request`.

Il test sul riutilizzo usa il flusso reale: registrazione e verifica del professionista, login con JWT, creazione dell'invito e registrazione dei due clienti.

File modificato:

```text
backend/src/test/java/it/zuperman/support_trainer/AuthControllerRegistrationIntegrationTest.java
```

### Ownership dell'elenco inviti

È stato aggiunto un test che crea due professionisti e un invito per ciascuno, quindi richiama `GET /api/v1/invites` come primo professionista.

Il test verifica che la risposta:

* contenga l'invito del professionista autenticato;
* non contenga l'invito dell'altro professionista;
* contenga esattamente un solo codice nello scenario isolato.

File modificato:

```text
backend/src/test/java/it/zuperman/support_trainer/InviteControllerAuthorizationIntegrationTest.java
```

### Validazione pubblica dell'invito

È stata aggiunta una suite dedicata all'endpoint pubblico:

```text
POST /api/v1/auth/register/client/validate-invite
```

Comportamenti verificati:

* invito valido → `200 OK`;
* invito inesistente → `404 Not Found`, `INVITE_CODE_NOT_FOUND`;
* invito già usato → `400 Bad Request`, `INVITE_CODE_ALREADY_USED`;
* invito scaduto → `400 Bad Request`, `INVITE_CODE_EXPIRED`;
* invito inattivo → `400 Bad Request`, `INVITE_CODE_NOT_ACTIVE`;
* proprietario inattivo → `400 Bad Request`, `INVITE_CODE_NOT_ACTIVE`;
* proprietario con email non verificata → `400 Bad Request`, `INVITE_CODE_NOT_ACTIVE`;
* proprietario con account non `ACTIVE` → `400 Bad Request`, `INVITE_CODE_NOT_ACTIVE`.

La validazione pubblica è ora coperta direttamente e non soltanto attraverso il flusso di registrazione cliente. Il test sul caso valido verifica inoltre che la validazione non consumi l'invito.

Validazione e registrazione sono mantenute come coperture separate perché esercitano contratti differenti: la prima controlla lo stato corrente senza consumare il codice, mentre la seconda lo rivalida, crea il collegamento e lo marca come usato.

File creato:

```text
backend/src/test/java/it/zuperman/support_trainer/AuthControllerInviteValidationIntegrationTest.java
```

## Client / Professional access control

È stata aggiunta e verificata una suite di integrazione dedicata agli endpoint:

```text
GET /api/v1/clients/my
GET /api/v1/professionals/my
GET /api/v1/clients/{clientId}
GET /api/v1/professionals/{professionalId}
```

Comportamenti verificati:

* matrice ruoli base sugli endpoint `/my`, con rifiuto degli utenti anonimi e dei ruoli non autorizzati e accesso consentito al ruolo corretto;
* ownership dei dettagli, consentiti soltanto all'utente collegato alla risorsa richiesta;
* isolamento delle liste, per cui il professionista vede soltanto i propri clienti e il cliente soltanto i propri professionisti;
* esclusione dei collegamenti inattivi da entrambe le liste `/my`;
* utilizzo dei flussi reali di registrazione, verifica email, login JWT, invito e registrazione cliente.

File creato:

```text
backend/src/test/java/it/zuperman/support_trainer/ClientProfessionalAuthorizationIntegrationTest.java
```

Non sono state apportate modifiche al codice di produzione. Il pacchetto Client / Professional access control è considerato chiudibile per l'MVP.

## Availability

La suite di integrazione a livello service dedicata ad Availability contiene ora 14 test.

Comportamenti verificati:

* creazione valida di uno slot;
* prevenzione della sovrapposizione di slot dello stesso professionista;
* esclusione dei professionisti con specializzazione `NUTRITIONIST`;
* lettura availability da parte del cliente collegato e rifiuto del cliente non collegato;
* esclusione degli slot scaduti e degli slot con booking `PENDING`;
* blocco e sblocco degli slot;
* rifiuto dell'aggiornamento di uno slot non disponibile;
* protezioni in presenza di booking pendente o storico;
* ownership delle mutazioni, impedendo a un professionista di aggiornare, bloccare o sbloccare lo slot di un altro professionista;
* percorso positivo creazione slot → aggiornamento parziale valido → presenza corretta in `getMyAvailabilitySlots()`.

File modificato:

```text
backend/src/test/java/it/zuperman/support_trainer/AvailabilityServiceIntegrationTest.java
```

Non sono state apportate modifiche al codice di produzione. Il pacchetto Availability è considerato chiudibile per l'MVP.

## Booking

La suite di integrazione a livello service dedicata a Booking contiene ora 17 test.

Comportamenti verificati:

* creazione di una booking request `PENDING` su slot disponibile;
* rifiuto del cliente non collegato;
* isolamento delle liste restituite da `getClientBookingRequests()` e `getProfessionalBookingRequests()`;
* protezione del dettaglio booking e rifiuto del cliente estraneo;
* ownership delle mutazioni, impedendo a un professionista estraneo di confermare o rifiutare booking altrui e a un utente estraneo di cancellarle;
* conferma della booking con passaggio dello slot a `BOOKED`;
* rifiuto della booking con slot mantenuto `AVAILABLE`;
* cancellazione lato cliente di booking `PENDING` e `CONFIRMED`;
* divieto di cancellazione di una booking `PENDING` da parte del professionista;
* rifiuto della creazione su slot scaduto, appartenente a un nutrizionista, `BLOCKED` o già `BOOKED`;
* rifiuto di una seconda richiesta `PENDING` sullo stesso slot.

File modificato:

```text
backend/src/test/java/it/zuperman/support_trainer/BookingServiceIntegrationTest.java
```

Non sono state apportate modifiche al codice di produzione. Il pacchetto Booking è considerato chiudibile per l'MVP.

## Profile

La suite `MeServiceIntegrationTest` dedicata a Profile contiene ora 9 test.

Comportamenti verificati:

* `getMyProfile()` per utenti `CLIENT` e `PROFESSIONAL`;
* `getMyAccount()` per utenti `CLIENT` e `PROFESSIONAL`;
* mapping corretto dei campi specifici per ruolo;
* aggiornamento parziale positivo dei profili cliente e professionista;
* rifiuto dei campi appartenenti all'altro ruolo con `PROFILE_FIELDS_NOT_ALLOWED`;
* aggiornamento dello stato operativo con valori validi per cliente e professionista;
* rifiuto di uno stato operativo non valido con `INVALID_OPERATIONAL_STATUS`;
* rimozione degli URL professionista tramite valori vuoti;
* validazione degli URL professionista privi di protocollo `http://` o `https://`.

File modificato:

```text
backend/src/test/java/it/zuperman/support_trainer/MeServiceIntegrationTest.java
```

Non sono state apportate modifiche al codice di produzione. Il pacchetto Profile è considerato chiudibile per l'MVP.

## Isolamento test con profilo test

È stato aggiunto `@ActiveProfiles("test")` a due test esistenti:

```text
backend/src/test/java/it/zuperman/support_trainer/SupportTrainerApplicationTests.java
backend/src/test/java/it/zuperman/support_trainer/UserPersistenceTest.java
```

Questo evita che i test usino accidentalmente il database MySQL locale e forza l’utilizzo della configurazione H2 prevista per l’ambiente di test.

## File toccati

```text
backend/src/test/java/it/zuperman/support_trainer/SupportTrainerApplicationTests.java
backend/src/test/java/it/zuperman/support_trainer/UserPersistenceTest.java
backend/src/test/java/it/zuperman/support_trainer/AuthControllerEmailVerificationIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/AuthControllerLoginIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/AuthControllerRegistrationIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/AuthControllerInviteValidationIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/InviteControllerAuthorizationIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/ClientProfessionalAuthorizationIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/AvailabilityServiceIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/BookingServiceIntegrationTest.java
backend/src/test/java/it/zuperman/support_trainer/MeServiceIntegrationTest.java
```

## Verifica

I test mirati sono stati eseguiti manualmente dopo i singoli blocchi di lavoro.

Per il pacchetto Client / Professional access control sono stati eseguiti il test mirato e la suite completa Maven. Entrambi sono passati sia nella copia sia nel progetto originale.

Anche il pacchetto Availability è stato verificato con test mirati e suite completa Maven. Entrambi sono passati sia nella copia sia nel progetto originale.

Anche il pacchetto Booking è stato verificato con test mirati e suite completa Maven. Entrambi sono passati sia nella copia sia nel progetto originale.

Anche il pacchetto Profile è stato verificato con test mirati e suite completa Maven. Entrambi sono passati sia nella copia sia nel progetto originale.

Comandi utilizzati:

```powershell
./mvnw test -Dtest=ClientProfessionalAuthorizationIntegrationTest
./mvnw test
```

La suite completa Maven è quindi passata dopo le integrazioni Auth, Invite, validate-invite, Client / Professional access control, Availability, Booking e Profile. Il numero totale dei test eseguiti non viene indicato perché non è stato verificato tramite log durante questo aggiornamento documentale.

## Rischi residui

* Alcuni test dipendono da `errorCode` specifici. Questo è utile per proteggere il contratto API, ma richiederà aggiornamenti se il contratto degli errori cambierà.
* Alcune fixture sono duplicate intenzionalmente per evitare refactoring prematuri nei test.
* Il test della registrazione cliente con invito valido prepara direttamente professionista e invito tramite repository. Questo mantiene il test focalizzato sulla registrazione cliente, ma non copre la generazione reale dell'invito.
* Gli inviti funzionano come bearer token: chi possiede un codice valido può utilizzarlo una volta.
* L'invito non è associato a una specifica email destinataria.
* La registrazione tramite invito considera immediatamente verificata l'email del cliente.
* La validazione pubblica restituisce `professionalId` e scadenza dell'invito.
* La protezione dal doppio utilizzo concorrente dipende dal lock database.
* Il rischio di regressione che permetta a un professionista di leggere inviti altrui è ora coperto dal test di ownership.
* Il riutilizzo di un invito già consumato è ora coperto da un test end-to-end.
* Il contratto principale dell'endpoint pubblico di validazione, inclusi gli stati non validi del proprietario, è coperto direttamente e il caso valido verifica che l'invito non venga consumato.
* Per il pacchetto Client / Professional restano come hardening futuro i dettagli con link disattivato, i profili destinazione non validi, gli ID inesistenti e una matrice di autorizzazione più estesa sugli endpoint di dettaglio.
* Per il pacchetto Availability restano come hardening futuro una matrice HTTP MockMvc/JWT specifica, i link inattivi, i profili non validi, gli ID inesistenti, i boundary temporali e i test di concorrenza. Non costituiscono bug o blocchi per l'MVP.
* Per il pacchetto Booking restano come hardening futuro una matrice HTTP MockMvc/JWT specifica, la cancellazione `CONFIRMED` lato professionista, gli ID inesistenti, le transizioni ripetute, gli stati profilo/link inattivi e i test di concorrenza. Non costituiscono bug o blocchi per l'MVP.
* I filtri per stato e il motivo testuale di rifiuto restano feature future non attive del pacchetto Booking.
* Per il pacchetto Profile restano come hardening futuro una matrice HTTP MockMvc/JWT specifica per `/api/v1/me/**`, account e profili inattivi, email professionista non verificata e una matrice completa delle validazioni DTO, inclusi boundary su data futura, altezza e limiti testuali. Non costituiscono bug o blocchi per l'MVP.
* L'upload dell'immagine profilo resta una feature futura non attiva.
* Restano fuori da questa fase test avanzati di concorrenza, input malformati, rate limiting e hardening operativo.

## Valutazione finale

Il branch `test-codex` migliora in modo significativo la copertura delle aree Auth, Invite, ruoli, access control, validazione pubblica degli inviti, Client / Professional, Availability, Booking e Profile senza modificare il comportamento applicativo.

Il pacchetto Invite / validate-invite / access control è considerato chiudibile per questa fase MVP. Le lacune residue riguardano robustezza avanzata, concorrenza e hardening futuro, non il flusso MVP fondamentale.

Anche il pacchetto Client / Professional access control è considerato chiudibile per l'MVP. Le lacune residue individuate sono classificate come hardening futuro e non bloccano il perimetro attuale.

Il pacchetto Availability è considerato chiudibile per l'MVP. Le lacune residue riguardano esclusivamente hardening futuro e non bloccano il flusso MVP fondamentale.

Il pacchetto Booking è considerato chiudibile per l'MVP. Le lacune residue riguardano esclusivamente hardening futuro e non bloccano il flusso MVP fondamentale.

Il pacchetto Profile è considerato chiudibile per l'MVP. Le lacune residue riguardano esclusivamente hardening futuro e non bloccano il flusso MVP fondamentale.

Le modifiche sono considerate sicure perché:

* sono limitate ai test;
* non toccano codice di produzione;
* usano il profilo `test`;
* usano flussi reali di registrazione, verifica email e autenticazione JWT per gli endpoint Invite;
* verificano direttamente gli stati principali dell'invito e del professionista proprietario;
* verificano ownership, isolamento delle liste e collegamenti attivi per gli endpoint Client / Professional;
* verificano le regole principali di Availability, incluse ownership delle mutazioni e aggiornamento parziale positivo;
* verificano le regole principali di Booking, incluse ownership, isolamento delle liste e integrazione con gli stati Availability;
* verificano lettura, aggiornamento parziale, separazione dei campi per ruolo e stato operativo del pacchetto Profile;
* sono state verificate con test mirati e suite completa.
