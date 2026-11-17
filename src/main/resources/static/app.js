// 1. Starea paginii: token CSRF, conexiune live și măsurătorile afișate.
let csrf;
let socket;
let stopped = false;
let measurements = new Map(); // Cheia este ID-ul: aceeași măsurătoare nu apare de două ori.
let patientFilter = '';
let historyRequestNumber = 0;

function element(id) {
    return document.getElementById(id);
}

function showError(error) {
    element('message').textContent = error.message;
}

// 2. Cereri HTTP către controller-ele Java.
async function request(url, options = {}) {
    const response = await fetch(url, options);
    if (response.status === 401) {
        stopped = true;
        location.href = '/login.html';
        throw new Error('Sesiune expirată.');
    }
    if (!response.ok) {
        let message = `Cererea a eșuat (${response.status}).`;
        try {
            const error = await response.json();
            if (error.message) message = error.message;
        } catch {
            // Unele erori de server nu conțin JSON; păstrăm mesajul de mai sus.
        }
        throw new Error(message);
    }
    return response.json();
}

// 3. Actualizarea tabelului. textContent afișează text, fără să execute HTML.
function matchesFilter(measurement) {
    return patientFilter === '' || String(measurement.patientId) === patientFilter;
}

function addMeasurement(measurement) {
    if (matchesFilter(measurement)) {
        measurements.set(measurement.id, measurement);
    }
}

function newestFirst(first, second) {
    const dateOrder = second.timestamp.localeCompare(first.timestamp);
    if (dateOrder !== 0) return dateOrder;
    return second.id - first.id;
}

function renderTable() {
    const rows = Array.from(measurements.values());
    rows.sort(newestFirst);
    const latest = rows.slice(0, 100);

    measurements.clear();
    element('vitalsTable').replaceChildren();
    for (const measurement of latest) {
        measurements.set(measurement.id, measurement);
        const row = document.createElement('tr');
        const values = [
            new Date(measurement.timestamp).toLocaleString('ro-RO'),
            measurement.patientId,
            measurement.heartRate + ' BPM',
            measurement.oxygenLevel + '%',
            measurement.bloodPressure,
            measurement.temperature + ' °C'
        ];
        for (const value of values) {
            const cell = document.createElement('td');
            cell.textContent = value;
            row.append(cell);
        }
        element('vitalsTable').append(row);
    }
    element('empty').hidden = latest.length > 0;
}

async function loadHistory() {
    // Dacă filtrul se schimbă rapid, ignorăm răspunsurile vechi care sosesc târziu.
    const requestNumber = ++historyRequestNumber;
    let url = '/web-api/vitals/all';
    if (patientFilter !== '') url = '/web-api/vitals/patient/' + patientFilter;

    const history = await request(url);
    if (requestNumber !== historyRequestNumber) return;
    for (const measurement of history) addMeasurement(measurement);
    renderTable();
}

// 4. WebSocket: serverul trimite singur măsurătorile noi către browser.
function connectWebSocket() {
    if (stopped) return;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(protocol + '//' + location.host + '/ws-medical');

    socket.onopen = async function () {
        element('status').textContent = '● Conectat · actualizare în timp real';
        try {
            // Recuperăm inclusiv datele salvate cât timp conexiunea era întreruptă.
            await loadHistory();
        } catch (error) {
            showError(error);
        }
    };
    socket.onmessage = function (event) {
        const measurement = JSON.parse(event.data);
        addMeasurement(measurement);
        renderTable();
    };
    socket.onclose = function () {
        element('status').textContent = 'Reconectare la fluxul live…';
        if (!stopped) setTimeout(reconnect, 3000);
    };
    socket.onerror = function () {
        socket.close();
    };
}

async function reconnect() {
    try {
        await request('/web-api/me'); // Verificăm dacă sesiunea mai este autentificată.
    } catch (error) {
        showError(error);
    }
    if (!stopped) connectWebSocket();
}

// 5. Formularul trimite JSON prin POST, împreună cu tokenul CSRF.
async function saveMeasurement(event) {
    event.preventDefault(); // Nu reîncărcăm pagina când trimitem formularul.
    element('send').disabled = true;
    element('message').textContent = '';
    const measurement = {
        patientId: Number(element('patientId').value),
        heartRate: Number(element('heartRate').value),
        oxygenLevel: Number(element('oxygenLevel').value),
        bloodPressure: element('bloodPressure').value,
        temperature: Number(element('temperature').value)
    };
    try {
        const saved = await request('/web-api/vitals', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
            body: JSON.stringify(measurement)
        });
        addMeasurement(saved);
        renderTable();
        element('message').textContent = 'Măsurătoare salvată.';
    } catch (error) {
        showError(error);
    } finally {
        element('send').disabled = false;
    }
}

async function filterHistory(event) {
    event.preventDefault();
    const value = element('filter').value;
    patientFilter = value === '' ? '' : String(Number(value));
    measurements.clear();
    renderTable();
    try {
        await loadHistory();
        element('message').textContent = '';
    } catch (error) {
        showError(error);
    }
}

// 6. Legăm evenimentele de funcții și pornim pagina.
element('vitalsForm').addEventListener('submit', saveMeasurement);
element('filterForm').addEventListener('submit', filterHistory);
element('logout').addEventListener('submit', function () {
    stopped = true;
    if (socket) socket.close();
});

async function initialize() {
    try {
        // Thymeleaf a completat deja utilizatorul și tokenul formularului de logout.
        csrf = await request('/web-api/csrf');
        element('send').disabled = false;
        await loadHistory();
        connectWebSocket();
    } catch (error) {
        showError(error);
    }
}
initialize();
