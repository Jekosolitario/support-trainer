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
- minimo **8 caratteri**
- almeno **una lettera maiuscola**
- almeno **un numero**
- almeno **un carattere speciale**

### 3.4 Stato iniziale account
Alla registrazione, l’account professionista deve nascere con:
- `accountStatus = PENDING_VERIFICATION`
- `emailVerified = false`

### 3.5 Blocco funzioni operative
Finché l’email non è verificata, il professionista non può:
- generare codici invito
- collegare clienti
- utilizzare funzionalità operative riservate

---

## 4. Validazioni registrazione cliente con codice invito

### 4.1 Regola generale
Il cliente non può registrarsi liberamente.  
La registrazione cliente richiede un codice invito valido.

### 4.2 Password cliente
La password del cliente deve rispettare almeno queste regole:
- minimo **8 caratteri**
- almeno **una lettera maiuscola**
- almeno **un numero**
- almeno **un carattere speciale**

### 4.3 Validazioni sul codice invito
Il codice deve:
- esistere
- essere associato a un professionista esistente
- non essere già usato
- non essere scaduto

### 4.4 Registrazione entro scadenza
L’utente che utilizza il codice invito deve completare la registrazione entro la scadenza del codice.

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
Solo un professionista con account:
- `ACTIVE`
- email verificata

può collegare clienti tramite invito valido.

---

## 6. Validazioni AvailabilitySlot

### 6.1 Specializzazione corretta
Gli slot di disponibilità possono essere creati solo da un professionista con specializzazione:
- `PERSONAL_TRAINER`

### 6.2 Intervallo temporale valido
Per ogni slot:
- `startDateTime` deve essere precedente a `endDateTime`

### 6.3 Nessuna sovrapposizione
Per lo stesso personal trainer non devono esistere slot attivi sovrapposti nello stesso intervallo temporale.

### 6.4 Stato iniziale slot
Alla creazione, salvo casi particolari:
- `status = AVAILABLE`

### 6.5 Coerenza professionista
Il professionista che crea lo slot deve:
- esistere
- essere attivo
- avere ruolo e specializzazione coerenti

---

## 7. Validazioni BookingRequest

### 7.1 Cliente collegato al PT
Il cliente può inviare una richiesta di prenotazione solo verso un personal trainer:
- a cui è collegato attivamente

### 7.2 Coerenza della richiesta
Ogni `BookingRequest` deve riferirsi a:
- un solo cliente
- un solo personal trainer

### 7.3 Validazione degli slot richiesti
Tutti gli slot richiesti devono:
- esistere
- essere `AVAILABLE`
- appartenere allo stesso personal trainer della richiesta
- non essere scaduti logicamente
- non essere già confermati da altre richieste

### 7.4 Richiesta con almeno uno slot
Una `BookingRequest` deve contenere:
- almeno un `BookingRequestItem`

### 7.5 Stato iniziale richiesta
Alla creazione:
- `status = PENDING`

### 7.6 Conferma richiesta
Quando la richiesta viene confermata:
- tutti gli slot collegati diventano `BOOKED`

### 7.7 Rifiuto richiesta
Quando la richiesta viene rifiutata:
- gli slot restano o tornano `AVAILABLE`, se non bloccati da altre logiche

### 7.8 Integrità finale
Uno slot già `BOOKED` non può essere confermato di nuovo in una richiesta diversa.

---

## 8. Validazioni WorkoutPlan

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

## 9. Validazioni NutritionPlan

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

## 10. Validazioni WorkoutFeedback

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

## 11. Validazioni NutritionFeedback

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

## 12. Validazioni ClientMeasurement

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
Campi come:
- `instagramUrl`
- `websiteUrl`
- `profileImageUrl`

se presenti devono avere formato coerente con URL valido o path gestito dall’applicazione.

---

## 14. Validazioni generali su stati ed enum

### 14.1 Valori ammessi
I campi enum devono accettare solo:
- valori previsti dal sistema

### 14.2 Coerenza tra stato account e azioni
Un utente con account non attivo o non verificato non può eseguire azioni operative riservate.

### 14.3 Coerenza specializzazione/funzionalità
Le operazioni devono essere coerenti con la specializzazione del professionista:
- solo `PERSONAL_TRAINER` per workout e prenotazioni
- solo `NUTRITIONIST` per piani alimentari

---

## 15. Dove applicare le validazioni

### 15.1 DTO / Bean Validation
Qui vanno bene:
- obbligatorietà campi
- formato email
- pattern password
- not blank
- range base
- controlli semplici

### 15.2 Service Layer
Qui devono stare le regole di business, ad esempio:
- massimo 3 professionisti
- validità codice invito
- no self-link
- no duplicati attivi
- slot non sovrapposti
- coerenza richiesta-slot-professionista
- unicità scheda/piano attivo per coppia
- validità feedback rispetto ai contenuti assegnati

### 15.3 Database
A livello database vanno previsti almeno:
- vincoli di unicità
- nullabilità coerente
- foreign key corrette

---

## 16. Regole da trasformare in eccezioni applicative
Le seguenti situazioni devono produrre errori applicativi chiari:

- email già registrata
- password non conforme
- codice invito inesistente
- codice invito scaduto
- codice invito già usato
- tentativo di quarto professionista per lo stesso cliente
- tentativo di self-link
- tentativo di creare slot sovrapposti
- tentativo di prenotare slot non disponibili
- tentativo di creare scheda/piano senza collegamento valido
- tentativo di inviare feedback su contenuti non appartenenti al cliente

---

## 17. Decisioni confermate
Per Support Trainer si confermano le seguenti regole:

- password utenti con requisiti forti
- professionista inizialmente `PENDING_VERIFICATION`
- cliente registrabile solo tramite codice invito valido
- registrazione cliente valida solo entro la scadenza del codice
- massimo 3 professionisti attivi per cliente
- no self-link
- prenotazioni solo con PT collegato
- slot validi, disponibili e non sovrapposti
- una scheda workout attiva per coppia PT-cliente
- più schede workout possibili per lo stesso cliente solo se di PT diversi
- un solo piano nutrizione attivo per coppia nutrizionista-cliente
- feedback consentiti solo su contenuti realmente assegnati
- misurazioni inseribili solo da soggetti autorizzati e mantenute nello storico

---