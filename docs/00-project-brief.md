# Project Brief — Support Trainer

## 1. Nome del progetto
**Nome:** Support Trainer

---

## 2. Descrizione breve
Support Trainer è una web app pensata per professionisti del benessere, come personal trainer e nutrizionisti, che hanno bisogno di gestire i propri clienti in modo più ordinato ed efficiente.

L’app permette ai professionisti di organizzare disponibilità, richieste di appuntamento e schede personalizzate, mentre i clienti possono prenotare sessioni, consultare i propri programmi e seguire più facilmente il percorso assegnato.

---

## 3. Scopo del progetto
Questo progetto nasce con i seguenti obiettivi:

- consolidare le competenze in:
  - Java
  - Spring Boot
  - Hibernate / JPA
  - REST API
  - MySQL
  - HTML
  - CSS
  - JavaScript
- costruire un’app completa e realmente funzionante
- documentare ogni fase dello sviluppo in modo ordinato
- capire meglio il flusso reale di un progetto full stack
- arrivare, se possibile, a una versione pubblicabile online

---

## 4. Problema che risolve
Molti personal trainer e professionisti simili gestiscono clienti, appuntamenti e programmi di lavoro in modo frammentato, spesso tramite chiamate, messaggi e documenti sparsi.

Questo crea diversi problemi:
- perdita di tempo nella gestione delle disponibilità
- difficoltà nell’organizzare appuntamenti e modifiche
- poca chiarezza nella condivisione delle schede
- maggiore rischio di errori, sovrapposizioni o comunicazioni confuse

Vale la pena risolvere questo problema perché una gestione più ordinata migliora sia il lavoro del professionista sia l’esperienza del cliente.

---

## 5. Target utenti
### Utenti principali
- Personal trainer
- Clienti
- Nutrizionisti

### Relazioni previste
- un cliente può essere collegato fino a **3 professionisti**
- tra questi possono esserci personal trainer e nutrizionisti

### Livello di esperienza degli utenti
Gli utenti non sono necessariamente esperti di tecnologia, quindi l’app dovrà essere semplice, chiara e facile da usare.

### Contesto d’uso
L’app sarà usata in ambito lavorativo e personale, soprattutto per:
- organizzare appuntamenti
- consultare disponibilità
- visualizzare programmi assegnati
- monitorare informazioni utili al percorso del cliente

---

## 6. Obiettivi principali
L’app dovrà permettere di:

1. consentire al professionista di impostare la propria disponibilità lavorativa
2. permettere al cliente di inviare richieste di prenotazione sugli slot disponibili
3. permettere al professionista di confermare o rifiutare le richieste ricevute
4. consentire al professionista di invitare nuovi clienti tramite codice temporaneo
5. bloccare la registrazione del cliente se non possiede un codice valido
6. permettere al professionista di creare e assegnare schede personalizzate
7. consentire al cliente di visualizzare la propria scheda mensile suddivisa in 4 settimane
8. permettere al cliente di aprire il dettaglio dell’allenamento del singolo giorno
9. permettere a cliente e professionista di gestire il proprio stato generale quando necessario
10. permettere al professionista di gestire il collegamento con i propri clienti

---

## 7. MVP (prima versione minima funzionante)
Per la prima versione funzionante, le funzionalità essenziali saranno:

- [ ] registrazione e login dei professionisti
- [ ] creazione cliente tramite codice di invito temporaneo
- [ ] registrazione cliente consentita solo con codice valido
- [ ] collegamento tra cliente e professionista
- [ ] gestione disponibilità del professionista
- [ ] invio richiesta di prenotazione da parte del cliente
- [ ] conferma o rifiuto della richiesta da parte del professionista
- [ ] creazione di una scheda di allenamento mensile
- [ ] visualizzazione della scheda da parte del cliente
- [ ] dettaglio giornaliero dell’allenamento con struttura tabellare

### Struttura iniziale della scheda
La scheda mensile sarà organizzata in:
- settimana 1
- settimana 2
- settimana 3
- settimana 4

Ogni settimana conterrà i giorni relativi al programma.
I giorni potranno essere distinti visivamente, ad esempio:
- verde = giorno libero
- rosso = giorno di allenamento

Aprendo il giorno di allenamento, il cliente visualizzerà una tabella con dati come:
- esercizio
- serie e ripetizioni
- intensità
- recupero
- tecniche aggiuntive
- descrizione esercizio
- carichi / ripetizioni registrate
- note

---

## 8. Fuori scope (non nella prima versione)
Per evitare di rendere la prima versione troppo complessa, queste funzionalità restano fuori scope inizialmente:

- [ ] grafici avanzati dei progressi
- [ ] notifiche push o email automatiche
- [ ] chat interna in tempo reale
- [ ] gestione pagamenti o abbonamenti
- [ ] caricamento file avanzato
- [ ] statistiche dettagliate e dashboard evolute
- [ ] logica avanzata per assenze, ferie e sostituzioni
- [ ] gestione completa multi-professionista con permessi molto dettagliati

---

## 9. Stack tecnologico previsto

### Backend
- Java
- Spring Boot
- Spring Data JPA
- Hibernate
- Spring Security
- MySQL

### Frontend
- HTML
- CSS
- JavaScript Vanilla

### Strumenti di supporto
- Git / GitHub
- Postman
- MySQL Workbench
- VS Code
- IntelliJ IDEA

---

## 10. Architettura iniziale prevista
Il frontend sarà separato dal backend.

Il frontend invierà richieste HTTP alle API REST esposte dal backend.  
Il backend gestirà autenticazione, logica applicativa, validazione, accesso al database e regole di business.  
I dati verranno salvati in MySQL e restituiti al frontend in formato JSON.

---

## 11. Risultato finale desiderato
Il progetto finale dovrebbe essere:

- un’app completa nelle sue funzionalità principali
- funzionante in locale
- testabile da utenti reali
- strutturata in modo ordinato
- ben documentata
- possibilmente pubblicata online in una prima versione usable

---

## 12. Obiettivi di apprendimento personali
Attraverso questo progetto voglio:

- imparare meglio controller, service, repository, DTO e gestione degli endpoint
- capire meglio il flusso completo del backend in una vera web app
- consolidare Spring Boot, REST API, business logic, validazione e gestione eccezioni
- migliorare nella comunicazione tra frontend e backend
- capire meglio autenticazione, sicurezza e organizzazione professionale del codice

---

## 13. Note iniziali
### Idea generale
Realizzare una piattaforma gestionale per professionisti del benessere e i loro clienti, con particolare attenzione a organizzazione, chiarezza e usabilità.

### Dubbi principali
- da dove iniziare correttamente
- come impostare bene la struttura del progetto
- come trasformare l’idea in funzionalità reali e sostenibili
- come gestire correttamente sicurezza, relazioni tra utenti e logica di prenotazione

### Vincoli tecnici
- conoscenza ancora parziale di Spring Security
- poca esperienza pratica nella progettazione completa di una web app
- necessità di procedere un passo alla volta con documentazione chiara

### Priorità iniziale
Capire il flusso corretto di progettazione e sviluppo, costruendo il progetto in modo ordinato senza fretta.