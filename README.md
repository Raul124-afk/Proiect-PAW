# Monitorizare medicală — Proiect PAW

Portal educațional cu dashboard, pacienți, fișe individuale, grafice, măsurători, observații, alerte configurabile, calendar, administrarea conturilor și simulator live.

## Structura în IntelliJ

Deschide folderul principal sau `pom.xml` din rădăcină ca proiect Maven, folosind JDK 21. În panoul **Project** apar separat:

```text
backend/                  BACKEND — Java / Spring Boot
  pom.xml
  src/main/java/ro/ubm/monitorizare_medicala/
    controller/           Pagini și API REST
    service/              Logică, acces, alerte și simulator
    model/                Entitățile bazei de date
    repository/           Spring Data JPA
    security/ și config/  Autentificare, JWT, CSRF și WebSocket
  src/main/resources/     Configurații PostgreSQL / H2
  src/test/java/          Teste de integrare
frontend/                 FRONTEND — HTML / CSS / JavaScript
  templates/              Pagini HTML cu Thymeleaf
  static/                 Stiluri, JavaScript, SVG
  README.md               Explicația tehnologiilor frontend
.run/                     Configurații IntelliJ: DEMO și PostgreSQL
compose.yaml              PostgreSQL prin Docker
pom.xml                   Proiectul Maven principal
mvnw / mvnw.cmd           Maven Wrapper
```

Maven include sursele din `frontend/` în aplicația Spring Boot. Ambele părți pornesc împreună, la aceeași adresă. În IntelliJ, după actualizare, apasă **Reload All Maven Projects**.

## Pornire demo

Necesită JDK 21. Din rădăcina proiectului:

```bash
./mvnw -pl backend spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.arguments=--server.port=18080
```

Pe Windows folosește `mvnw.cmd`. Pe macOS poți deschide `porneste-demo.command`. În IntelliJ selectează **Monitorizare medicala - DEMO**, apoi Run. Deschide **http://localhost:18080** în Safari.

Profilul demo folosește H2 pe disc, ascultă doar local și creează date fictive:

| Rol | Utilizator | Parolă demonstrativă |
| --- | --- | --- |
| Administrator | `admin_demo` | `DemoAdmin2026!` |
| Medic | `medic` | `DemoMedical2026!` |
| Al doilea medic | `medic2_demo` | `DemoMedical2026!` |
| Pacient | `pacient_demo` | `DemoPacient2026!` |

Conturile demonstrative sunt create exclusiv în profilul `demo`. Parolele conturilor existente nu sunt suprascrise.

## Pornire PostgreSQL

Configurează variabilele în terminal sau în Run → Edit Configurations → Environment variables:

| Variabilă | Rol |
| --- | --- |
| `DB_URL` | Implicit `jdbc:postgresql://localhost:5432/medical_db` |
| `DB_USERNAME` | Implicit `postgres` |
| `DB_PASSWORD` | Parola bazei de date |
| `APP_USERNAME` / `APP_PASSWORD` | Contul inițial de medic; implicit utilizatorul `medic` |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | Contul inițial de administrator; implicit `admin`; creat numai dacă parola este configurată |
| `JWT_SECRET` | Opțional, minimum 32 de octeți; altfel cheia se regenerează la pornire |
| `PORT` | Implicit `8080` |

Parolele inițiale au minimum 12 caractere și maximum 72 de octeți UTF-8. Conturile existente își păstrează parolele și rolurile.

```bash
# După configurarea variabilelor de mediu:
docker compose up -d db
./mvnw -pl backend spring-boot:run
```

Maven nu încarcă automat fișiere `.env`; exportă variabilele în terminal sau setează-le în IntelliJ. `porneste.command` folosește implicit portul `18080`; configurația IntelliJ PostgreSQL folosește `8080`.

La prima actualizare, măsurătorile vechi sunt păstrate și asociate unor fișe „Pacient importat #…”, repartizate primului medic existent. Administratorul poate completa și repartiza fișele. Păstrează un backup al bazei existente înainte de actualizare.

## Pagini și roluri

- Dashboard: statistici, activitate pe 7 zile, măsurători recente și programări viitoare.
- Pacienți: căutare, adăugare, editare, arhivare reversibilă și repartizare.
- Fișa pacientului: date de contact, grafice, istoric, observații și praguri individuale.
- Măsurători: validare, filtre după pacient și dată, indicator afișat, paginare și export CSV (maximum 10.000 de rânduri).
- Alerte: generate din măsurători noi. Flux: Nouă → Verificată → Închisă; istoricul rămâne disponibil. Pragurile sunt demonstrative și configurabile.
- Programări: calendar lunar, agendă, durată, editare și stări. Suprapunerile pentru același medic sunt respinse. Ore locale, Europe/București.
- Administrare: creare de conturi și roluri, activare/dezactivare și repartizare. Parolele nu sunt afișate în liste.
- Simulator: doar fișe demonstrative, scenariu normal sau variabil, interval 2–30 secunde, maximum 5 minute sau 100 de valori per sesiune.
- Live: WebSocket transmite numai datele pacienților repartizați medicului; administratorii văd toate fișele. Logoutul și revocarea accesului sunt verificate la transmitere.

Medicul gestionează pacienții repartizați. Pacientul vede numai propria fișă, observațiile și măsurătorile proprii și poate solicita/anula programări. Administratorul gestionează toate fișele și conturile. Un medic cu pacienți repartizați nu poate fi dezactivat înainte de repartizarea lor; schimbarea medicului este blocată dacă există programări viitoare active.

## Tehnologii

Backend: Java 21, Spring Boot 4.1.2, Spring Security, Spring Data JPA/Hibernate, PostgreSQL/H2, WebSocket, Maven și JUnit.

Frontend: HTML5, CSS3 (Grid/Flexbox și responsive), JavaScript, Thymeleaf, Fetch API, WebSocket API și SVG pentru grafice și iconițe. Nu este necesar un server Node.js. Vezi `frontend/README.md` pentru explicații.

API-ul web folosește sesiune și CSRF la `/web-api/portal/**`. Măsurătorile se creează la `/web-api/vitals`. API-ul JWT rămâne la `/api/auth/**` și `/api/vitals/**`, cu verificări de acces la pacient pe server.

## Testare și build

```bash
./mvnw test
./mvnw package
java -jar backend/target/monitorizare-medicala-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo --server.port=18080
```

21 de teste de integrare acoperă autentificarea, JWT, CSRF, validarea, izolarea între roluri și pacienți, arhivarea, alertele, suprapunerile programărilor, administrarea, exportul, simulatorul și WebSocket.

Fișierele generate (`target/`), bazele locale (`data/`), `.env`, logurile și setările personale IntelliJ sunt excluse din Git.
