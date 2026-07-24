# Istruzioni condivise per gli agenti

## Ambito del repository

- Queste istruzioni valgono per l'intero repository.
- `backend/` contiene l'applicazione Java 21/Spring Boot e i relativi test e migrazioni; `frontend/` contiene l'applicazione React/TypeScript/Vite; `docs/` contiene la documentazione funzionale e tecnica.
- Considerare codice, configurazioni e documentazione corrente pertinente come fonti di verità. I documenti di sprint conclusi, i resoconti Codex e le certificazioni legate a commit specifici sono fonti storiche, non workflow operativi correnti.

## Copia di lavoro e perimetro

- Operare esclusivamente nella copia di collaudo esplicitamente designata per l'intervento. Prima di modificare file, verificare il top-level del repository; se il checkout autorizzato non è chiaro, fermarsi e chiedere conferma.
- Usare un branch dedicato per ogni intervento e verificare che sia il branch autorizzato.
- Rispettare rigorosamente il perimetro richiesto. Non introdurre refactoring, pulizie, aggiornamenti o modifiche collaterali non necessari.

## Sicurezza Git

- Prima di modificare file, verificare almeno repository, branch, HEAD, working tree, staging, file non tracciati ed eventuali operazioni Git in corso.
- Non sovrascrivere, rimuovere o includere modifiche preesistenti senza autorizzazione esplicita.
- Scrivere i messaggi di commit in italiano.
- Non creare commit e non eseguire push senza autorizzazione esplicita.
- Non eseguire senza autorizzazione esplicita `merge`, `rebase`, `cherry-pick`, `reset`, `restore`, `clean`, force push, eliminazione di branch, rimozione di file o altre operazioni distruttive o che riscrivono la storia.

## Fonti di verità e contratti

- Quando requisiti o dati non sono sufficienti, ispezionare il codice e la documentazione pertinente prima di proporre o implementare una soluzione.
- Non inventare endpoint, payload, campi, contratti API o comportamenti backend. Distinguere sempre ciò che è implementato da ciò che è pianificato o soltanto ipotetico.
- Non simulare nel frontend funzionalità o contratti assenti nel backend.

## Standard frontend

- Usare npm come unico package manager frontend e rispettare `frontend/package-lock.json`.
- Mantenere TypeScript in modalità rigorosa. Non aggirare il type system con `any`, assertion, soppressioni o riduzioni dei controlli senza una necessità tecnica localizzata e motivata.
- Sviluppare le interfacce mobile-first.
- Trattare l'accessibilità come requisito: preservare semantica, uso da tastiera, focus visibile, associazioni dei form e stati comprensibili senza dipendere soltanto dal colore.

## Verifiche e consegna

- Eseguire controlli, lint, formattazione, build e test coerenti con i file e i comportamenti modificati. Non dichiarare verifiche non eseguite.
- Al termine riportare stato Git, sintesi del diff, verifiche e test eseguiti con i relativi esiti, oltre a problemi, limiti o rischi ancora aperti.
