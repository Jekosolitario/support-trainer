# Functional Scope — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce in modo chiaro:
- i tipi di utente del sistema
- cosa può fare ogni utente
- le regole funzionali principali
- i confini della prima versione (v1)

---

## 2. Tipi di utente

### 2.1 Ruoli di accesso
Il sistema prevede i seguenti ruoli principali:
- **CLIENT**
- **PROFESSIONAL**

### 2.2 Specializzazione del professionista
Un utente con ruolo **PROFESSIONAL** può avere una delle seguenti specializzazioni:
- **PERSONAL_TRAINER**
- **NUTRITIONIST**

### 2.3 Nota progettuale
La separazione tra **ruolo** e **specializzazione** consente di:
- gestire la sicurezza in modo più semplice
- riutilizzare la stessa struttura base per più professionisti
- differenziare le funzionalità business senza duplicare la logica di autenticazione

---

## 3. Regole di registrazione

### 3.1 Registrazione professionista
Il professionista può registrarsi liberamente al sistema.

Dopo la registrazione deve però confermare il proprio indirizzo email tramite verifica account.

Fino alla conferma dell’email, il professionista non può utilizzare le funzionalità operative principali, in particolare:
- generare codici invito
- collegare nuovi clienti
- usare funzioni riservate dell’area professionista

### 3.1.1 Stato iniziale del professionista
Dopo la registrazione, l’account professionista può trovarsi in uno stato iniziale di verifica, ad esempio:
- **PENDING_VERIFICATION**
- **ACTIVE**

Solo nello stato **ACTIVE** il professionista può utilizzare pienamente la piattaforma.

### 3.2 Registrazione cliente
Il cliente non può registrarsi liberamente.  
Può completare la registrazione solo se possiede un **codice invito valido** generato da un professionista.

### 3.3 Collegamento cliente-professionista
Il collegamento tra cliente e professionista avviene **dopo** la registrazione completata con successo.  
Questo evita di collegare al professionista un utente non ancora registrato correttamente.

### 3.4 Regola di sicurezza sul collegamento
Il sistema non deve permettere che un professionista si colleghi come cliente a sé stesso.

Di conseguenza:
- un professionista non può usare un proprio codice invito per creare un collegamento verso sé stesso
- il collegamento deve essere bloccato se professionista e cliente coincidono logicamente come stesso account utente

---

## 4. Relazioni tra utenti

### 4.1 Cliente e professionisti
Un cliente può essere collegato fino a **3 professionisti**.

### 4.2 Tipologie di collegamento
I professionisti collegati al cliente possono essere:
- personal trainer
- nutrizionisti

### 4.3 Gestione del collegamento
Ogni collegamento cliente-professionista deve permettere di distinguere chiaramente:
- quale professionista ha creato contenuti
- quali contenuti appartengono a quale professionista
- quali funzionalità sono disponibili in base alla specializzazione del professionista

---

## 5. Ambito funzionale del personal trainer

Il personal trainer deve poter:

- registrarsi al sistema
- confermare il proprio account tramite email
- accedere pienamente alle funzionalità solo dopo la verifica
- generare codici invito per nuovi clienti
- visualizzare i propri clienti collegati
- impostare e modificare la propria disponibilità
- ricevere richieste di prenotazione dai clienti
- confermare o rifiutare le richieste di prenotazione
- creare schede di allenamento
- assegnare schede di allenamento ai clienti
- visualizzare eventuali segnalazioni inviate dai clienti sulle schede
- aggiornare il proprio stato

---

## 6. Ambito funzionale del nutrizionista

Il nutrizionista deve poter:

- registrarsi al sistema
- confermare il proprio account tramite email
- accedere pienamente alle funzionalità solo dopo la verifica
- generare codici invito per nuovi clienti
- visualizzare i propri clienti collegati
- creare piani alimentari
- assegnare piani alimentari ai clienti
- visualizzare eventuali segnalazioni inviate dai clienti sui piani
- aggiornare il proprio stato

### Nota
Nella prima versione il nutrizionista **non gestisce prenotazioni tramite app**.  
Gli appuntamenti con il nutrizionista vengono gestiti esternamente.

---

## 7. Ambito funzionale del cliente

Il cliente deve poter:

- registrarsi solo tramite codice invito valido
- accedere al sistema
- visualizzare i professionisti a cui è collegato
- visualizzare le schede di allenamento assegnate dal personal trainer
- visualizzare i piani alimentari assegnati dal nutrizionista
- aprire il dettaglio giornaliero della scheda o del piano
- inviare una segnalazione riferita a uno specifico giorno
- richiedere la prenotazione di una fascia oraria disponibile del personal trainer
- visualizzare lo stato della propria richiesta di prenotazione
- aggiornare il proprio stato personale

---

## 8. Regole sulle prenotazioni

### 8.1 Ambito prenotazioni
Le prenotazioni tramite app riguardano **solo il personal trainer**.

### 8.2 Flusso prenotazione
Il flusso previsto è il seguente:
1. il personal trainer imposta le proprie disponibilità
2. il cliente visualizza gli slot disponibili
3. il cliente invia una richiesta di prenotazione
4. il personal trainer riceve la richiesta
5. il personal trainer decide se confermare o rifiutare

### 8.3 Stati minimi della prenotazione
Le richieste di prenotazione possono avere almeno questi stati:
- **PENDING**
- **CONFIRMED**
- **REJECTED**
- **CANCELLED** *(opzionale in v1, se implementato)*

---

## 9. Regole su schede e piani

### 9.1 Schede di allenamento
Le schede di allenamento sono create dal **personal trainer**.

### 9.2 Piani alimentari
I piani alimentari sono creati dal **nutrizionista**.

### 9.3 Visualizzazione lato cliente
Il cliente può solo visualizzare i contenuti assegnati e inviare eventuali segnalazioni o richieste di modifica.

### 9.4 Segnalazioni del cliente
La segnalazione deve essere collegata:
- al professionista corretto
- al contenuto corretto
- al giorno specifico selezionato dal cliente

---

## 10. Struttura funzionale iniziale dei contenuti

### 10.1 Struttura generale
Sia la scheda di allenamento sia il piano alimentare avranno una struttura mensile composta da:
- settimana 1
- settimana 2
- settimana 3
- settimana 4

### 10.2 Giorni
Ogni settimana conterrà i giorni associati al programma.

### 10.3 Stato visivo del giorno
I giorni potranno essere rappresentati visivamente, ad esempio:
- **verde** = giorno libero
- **rosso** = giorno con contenuto assegnato

### 10.4 Dettaglio del giorno
Cliccando su un giorno con contenuto assegnato, si apre il dettaglio della giornata.

Per la scheda di allenamento, il dettaglio può contenere campi come:
- esercizio
- serie e ripetizioni
- intensità
- recupero
- tecniche aggiuntive
- descrizione esercizio
- carichi / ripetizioni registrate
- note

Per il piano alimentare, la struttura sarà simile ma con campi specifici diversi, da definire in un documento successivo.

---

## 11. Stati utente

### 11.1 Stati del professionista
Il professionista può assumere uno dei seguenti stati:
- **DISPONIBILE**
- **ASSENTE**
- **FERIE**
- **MALATTIA**

### 11.2 Stati del cliente
Il cliente può assumere uno dei seguenti stati:
- **ATTIVO**
- **INFORTUNATO**
- **PAUSA**

---

## 12. Confini della prima versione (v1)

La v1 include:
- registrazione professionista
- registrazione cliente con codice invito
- collegamento cliente-professionista
- disponibilità del personal trainer
- richieste di prenotazione cliente -> personal trainer
- conferma/rifiuto prenotazioni
- creazione e visualizzazione schede di allenamento
- creazione e visualizzazione piani alimentari
- invio segnalazioni del cliente su giorno specifico
- gestione stato utente

La v1 non include:
- chat real time
- notifiche push
- pagamenti
- dashboard statistiche avanzate
- grafici complessi dei progressi
- gestione appuntamenti del nutrizionista tramite app

---

## 13. Note aperte per i documenti successivi
Restano da definire nei prossimi step:
- entità principali del database
- relazioni tra entità
- struttura tecnica di schede e piani
- dettagli del codice invito
- regole di sicurezza e permessi
- API principali