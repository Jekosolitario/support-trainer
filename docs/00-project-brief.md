# Project Brief — Support Trainer

## 1. Nome del progetto
**Nome:** Support Trainer

---

## 2. Descrizione breve

Support Trainer è una web app pensata per professionisti del benessere, in particolare personal trainer e nutrizionisti, che hanno bisogno di gestire i propri clienti in modo ordinato ed efficiente.

Il backend attualmente implementato consente già di:

- registrare professionisti e clienti tramite un flusso controllato;
- collegare clienti e professionisti tramite codice invito;
- gestire profilo e stato operativo degli utenti;
- gestire disponibilità del personal trainer;
- creare e governare richieste di prenotazione tra cliente e personal trainer.

L’evoluzione futura del prodotto prevede anche schede di allenamento, piani alimentari, feedback e monitoraggio progressi.

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

## 3.1 Stato reale del progetto

### Backend implementato

Nel backend risultano completati:

- autenticazione JWT;
- registrazione professionista;
- verifica email professionista;
- codice invito;
- registrazione cliente tramite invito valido;
- collegamento automatico professionista-cliente;
- profilo/account utente;
- lettura clienti e professionisti collegati;
- modulo Availability;
- modulo Bookings.

### Workflow operativo già disponibile

Il backend supporta oggi il seguente flusso completo:

professionista registrato e verificato
-> invito cliente
-> registrazione cliente collegato
-> creazione slot availability
-> lettura slot disponibile da parte del cliente
-> richiesta booking
-> conferma / rifiuto / cancellazione

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

## 6.1 Obiettivi già raggiunti nel backend

L’app consente già di:

1. registrare professionisti con verifica email;
2. registrare clienti esclusivamente tramite codice invito valido;
3. creare automaticamente il collegamento tra cliente e professionista;
4. leggere e aggiornare il proprio profilo/account;
5. leggere clienti e professionisti collegati;
6. permettere al personal trainer di impostare la propria disponibilità lavorativa;
7. permettere al cliente collegato di consultare slot disponibili e non scaduti;
8. permettere al cliente di inviare richieste di prenotazione su un singolo slot disponibile;
9. permettere al professionista di confermare o rifiutare le richieste ricevute;
10. permettere cancellazioni booking secondo le transizioni consentite;
11. mantenere coerente lo stato degli slot rispetto al ciclo booking.

## 6.2 Obiettivi ancora da raggiungere

Restano da sviluppare:

1. gestione delle schede di allenamento;
2. gestione dei piani alimentari;
3. feedback del cliente;
4. monitoraggio progressi e misurazioni;
5. eventuali API dedicate alla gestione manuale dei collegamenti;
6. frontend reale integrato con il backend;
7. completamento delle funzionalità tecniche necessarie al deploy.

---

## 7. MVP — Stato di avanzamento

## 7.1 Funzionalità backend completate

- [x] registrazione e login dei professionisti;
- [x] verifica email del professionista;
- [x] creazione cliente tramite codice invito temporaneo;
- [x] registrazione cliente consentita solo con codice valido;
- [x] collegamento tra cliente e professionista;
- [x] gestione profilo/account;
- [x] lettura relazioni cliente-professionista;
- [x] gestione disponibilità del personal trainer;
- [x] lettura disponibilità da parte del cliente collegato;
- [x] invio richiesta prenotazione da parte del cliente;
- [x] conferma o rifiuto richiesta da parte del professionista;
- [x] cancellazione richiesta booking secondo le regole previste;
- [x] test automatici principali per Availability e Bookings.

## 7.2 Funzionalità ancora da implementare per completare la v1 applicativa

- [ ] integrazione frontend reale con il backend;
- [ ] creazione di una scheda di allenamento;
- [ ] visualizzazione della scheda da parte del cliente;
- [ ] dettaglio giornaliero dell’allenamento;
- [ ] eventuale gestione feedback;
- [ ] eventuale gestione misurazioni e progressi;
- [ ] completamento delle funzionalità tecniche necessarie al deploy.

## 7.3 Nota sul perimetro attuale

Il backend possiede già un primo flusso operativo completo basato su disponibilità e prenotazioni.

Le schede workout e le funzionalità di contenuto personalizzato restano parte dell’evoluzione successiva del prodotto e non risultano ancora implementate.

### Ipotesi futura per la scheda workout

La futura scheda mensile potrà essere organizzata in:

- settimana 1;
- settimana 2;
- settimana 3;
- settimana 4.

Ogni settimana potrà contenere i giorni relativi al programma, con dettaglio giornaliero composto da informazioni come:

- esercizio;
- serie e ripetizioni;
- intensità;
- recupero;
- tecniche aggiuntive;
- descrizione esercizio;
- carichi o ripetizioni registrate;
- note.

Questa struttura resta una proposta funzionale futura, da confermare nello sprint dedicato al modulo Workout.

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

## 10. Architettura del progetto

Il progetto adotta un’architettura con frontend separato dal backend.

### Backend attuale

Il backend è realizzato con:

- Java;
- Spring Boot;
- Spring Security;
- JWT;
- Spring Data JPA / Hibernate;
- MySQL.

Il backend gestisce:

- autenticazione;
- autorizzazione;
- logica applicativa;
- validazioni;
- accesso ai dati;
- regole business;
- API REST in formato JSON.

### Frontend

Il frontend è previsto con:

- HTML;
- CSS;
- JavaScript Vanilla.

Nell’audit attuale non sono stati analizzati file frontend integrati al backend: il lavoro verificato riguarda codice backend, configurazione, test e documentazione.

---

## 11. Risultato finale desiderato

Il progetto finale dovrà essere:

- completo nelle funzionalità scelte per la prima versione;
- funzionante in locale;
- dotato di backend e frontend realmente integrati;
- testabile da utenti reali;
- strutturato in modo ordinato;
- coperto da test sui flussi principali;
- documentato in modo coerente con il codice reale;
- possibilmente pubblicato online in una prima versione utilizzabile.

### Risultato già raggiunto

Il backend possiede già una base concreta e funzionante per:

- autenticazione e gestione utenti;
- relazioni cliente-professionista;
- disponibilità;
- prenotazioni.

---

## 12. Obiettivi di apprendimento personali
Attraverso questo progetto voglio:

- imparare meglio controller, service, repository, DTO e gestione degli endpoint
- capire meglio il flusso completo del backend in una vera web app
- consolidare Spring Boot, REST API, business logic, validazione e gestione eccezioni
- migliorare nella comunicazione tra frontend e backend
- capire meglio autenticazione, sicurezza e organizzazione professionale del codice

---

## 13. Note iniziali e contesto storico
Questa sezione conserva il contesto da cui è nato il progetto.  
Diversi dubbi iniziali sono già stati affrontati attraverso la realizzazione del backend fino ai moduli Availability e Bookings.

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