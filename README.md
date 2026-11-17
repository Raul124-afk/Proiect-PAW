# Proiect PAW — Monitorizare medicală

Aplicație educațională pentru autentificarea utilizatorilor și monitorizarea semnelor vitale. Interfața web și backendul sunt servite de aceeași aplicație Spring Boot.

## Tehnologii

- Java 21, Spring Boot 4.1.2 și Maven
- Spring Security: sesiuni pentru interfața web și JWT pentru API
- Thymeleaf, HTML, CSS și JavaScript
- Spring Data JPA și PostgreSQL; H2 pentru demonstrația locală și teste
- WebSocket pentru actualizarea măsurătorilor în timp real

## Structura proiectului

```text
src/main/java/ro/ubm/monitorizare_medicala/
  controller/   Cereri web și API
  service/      Logica aplicației
  model/        Entități
  repository/   Acces la baza de date
  config/       Configurarea aplicației
  security/     Autentificare și JWT
src/main/resources/
  templates/    Pagini HTML Thymeleaf
  static/       CSS și JavaScript
src/test/java/  Teste de integrare
```

## Pornire rapidă, fără PostgreSQL

Este necesar JDK 21. Maven Wrapper este inclus în proiect.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Pe Windows, folosește `mvnw.cmd` în loc de `./mvnw`.

Deschide <http://localhost:8080>. Profilul `demo` folosește o bază H2 locală în `data/` și creează contul demonstrativ:

- Utilizator: `medic`
- Parolă: `DemoMedical2026!`

Aceste date sunt exclusiv pentru demonstrația locală. Dacă portul 8080 este ocupat, oprește instanța deja pornită sau setează variabila de mediu `PORT` la alt port.

## Pornire cu PostgreSQL

Profilul implicit folosește PostgreSQL. Configurează variabilele de mediu în terminal sau în configurația Run din IntelliJ:

| Variabilă | Descriere |
| --- | --- |
| `DB_URL` | Implicit `jdbc:postgresql://localhost:5432/medical_db` |
| `DB_USERNAME` | Implicit `postgres` |
| `DB_PASSWORD` | Parola bazei de date |
| `APP_USERNAME` | Utilizatorul inițial; implicit `medic` |
| `APP_PASSWORD` | Parola contului inițial, minimum 12 caractere; contul se creează dacă nu există |
| `JWT_SECRET` | Opțional, minimum 32 de octeți; dacă lipsește, se generează o cheie temporară la fiecare pornire |
| `PORT` | Portul HTTP; implicit `8080` |

Pentru PostgreSQL prin Docker, setează `DB_PASSWORD` în mediul terminalului, apoi rulează:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

Maven nu încarcă automat fișiere `.env`; variabilele aplicației trebuie să existe în mediul procesului sau în configurația Run din IntelliJ.

În IntelliJ, rulează clasa `MonitorizareMedicalaApplication`. Proiectul include și configurația `Monitorizare medicala - PostgreSQL` în `.run/`.

Pe macOS, `porneste.command` pornește aplicația pe portul `18080` dacă `PORT` nu este setat. Pentru a recompila înainte de lansare, rulează `./mvnw package`.

## Teste

```bash
./mvnw test
```

Testele folosesc implicit H2 în memorie și verifică autentificarea, autorizarea, validarea măsurătorilor, JWT, protecția CSRF și WebSocket.

## Interfețe

- `/login.html` — autentificare
- `/inregistrare` — înregistrarea unui cont de pacient
- `/` — panoul de monitorizare pentru medic
- `/cont` — pagina contului
- `/web-api/vitals` — măsurători prin sesiunea web
- `/api/auth/login` și `/api/auth/eu` — autentificare și utilizator curent prin JWT
- `/api/vitals` — măsurători prin JWT
- `/ws-medical` — actualizări WebSocket

Repository-ul conține sursele, testele și configurațiile necesare. Fișierele generate (`target/`), bazele de date locale (`data/`), fișierele `.env` și setările personale ale IDE-ului sunt excluse prin `.gitignore`.
