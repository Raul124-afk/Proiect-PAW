# FRONTEND — interfața din browser

Directorul **frontend** este separat de backend în arborele IntelliJ.

| Tehnologie | Rol |
| --- | --- |
| HTML5 | Structura paginilor din `templates/`: autentificare, înregistrare, portal și erori. |
| Thymeleaf | Spring completează HTML-ul cu utilizatorul și tokenul CSRF înainte de trimiterea către browser. |
| CSS3 | `static/app.css` și `static/portal.css`: meniu, carduri, tabele, formulare, calendar și afișare pe telefon, folosind Grid și Flexbox. |
| JavaScript | `static/portal.js`: pagini, formulare, filtre, paginare, grafice, calendar și simulator. |
| Fetch API | Cereri JSON către backend și tratarea erorilor; CSRF pentru modificări. |
| WebSocket API | Primește măsurători în timp real. |
| SVG | Grafice scalabile și iconițe fără biblioteci externe. |

Pagina principală este `templates/portal.html`. Funcțiile pentru pagini și formulare sunt în `static/portal.js`.

Maven include aceste fișiere în JAR-ul Spring Boot. Aplicația se pornește din backend; nu este necesar Node.js. Șabloanele se deschid prin server, nu direct cu `file://`.

Datele vin din baza de date prin API. Graficul unei fișe afișează maximum 100 de măsurători din intervalul selectat, iar tabelul este paginat. Conținutul introdus de utilizatori este afișat cu escapare HTML.
