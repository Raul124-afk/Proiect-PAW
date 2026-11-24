# BACKEND — serverul Java

Directorul **backend** este separat de frontend în arborele IntelliJ.

- `src/main/java/ro/ubm/monitorizare_medicala/`: codul Java Spring Boot.
- `controller/`: pagini și API REST.
- `service/`: logica aplicației, accesul pe rol/pacient, alertele și simulatorul.
- `model/`: utilizatori, pacienți, măsurători, observații, programări, praguri și alerte.
- `repository/`: Spring Data JPA pentru PostgreSQL și H2.
- `security/` și `config/`: autentificare, JWT, CSRF și WebSocket.
- `src/main/resources/`: configurațiile aplicației.
- `src/test/java/`: teste de integrare.

Clasa de pornire: `MonitorizareMedicalaApplication`. Din rădăcină: `./mvnw -pl backend spring-boot:run -Dspring-boot.run.profiles=demo`. Instrucțiunile complete sunt în README-ul principal.
