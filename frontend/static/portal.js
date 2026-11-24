'use strict';
const $ = (id) => document.getElementById(id);
const h = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const path = document.body.dataset.path;
const base = '/web-api/portal';
const state = {me:null, patients:[], request:0, page:0, patientPage:0, metric:'heartRate', from:'', to:'', patientId:new URLSearchParams(location.search).get('patientId') || '', q:new URLSearchParams(location.search).get('q') || '', archived:false, alertStatus:'ACTIVE', month:new Date(new Date().getFullYear(),new Date().getMonth(),1), day:'', simulationPatient:'', simulationInterval:3, scenario:'NORMAL'};
let editorSave, toastTimer, socket, refreshTimer, stopped = false;
const roleNames = {ROLE_ADMIN:'Administrator', ROLE_MEDIC:'Medic', ROLE_PATIENT:'Pacient'};
const statuses = {NOUA:'Nouă',VERIFICATA:'Verificată',INCHISA:'Închisă',PROGRAMATA:'Programată',EFECTUATA:'Efectuată',ANULATA:'Anulată'};
const metrics = {heartRate:['Puls','BPM'],oxygenLevel:['Saturație oxigen','%'],temperature:['Temperatură','°C'],bloodPressure:['Tensiune arterială','mmHg']};
const staff = () => state.me?.role !== 'ROLE_PATIENT';
const admin = () => state.me?.role === 'ROLE_ADMIN';
const date = (value, time = false) => value ? new Date(value).toLocaleString('ro-RO', time ? {day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'} : {day:'2-digit',month:'short',year:'numeric'}) : '—';
const isoDay = (d) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const dateTimeInput = (value) => value ? String(value).slice(0,16) : '';
const initials = (name) => String(name || 'M').split(/\s+/).slice(0,2).map(x=>x[0]).join('').toUpperCase();
const icons = {
 dashboard:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
 patients:'<circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5v2"/>',
 vitals:'<path d="M2 12h5l3-8 4 16 3-8h5"/>',
 alerts:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
 calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 2v6m10-6v6M3 11h18m-13 4h2m4 0h2m-8 3h2"/>',
 users:'<path d="M9 21v-3a6 6 0 0 0-6-6M18 12l3 2v4l-3 2-3-2v-4z"/><circle cx="8" cy="6" r="3"/><path d="m18 9 0 3m0 8v3m-6-9 3 1m6 2 3 1"/>',
 simulation:'<path d="m9 3 3 4-3 4M4 7h8m3 6-3 4 3 4m5-4h-8"/>',
 profile:'<circle cx="12" cy="8" r="4"/><path d="M4 22v-2a8 8 0 0 1 16 0v2"/>',
 plus:'<path d="M12 5v14M5 12h14"/>'
};
const icon = (name, cls='nav-icon') => `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.vitals}</svg>`;
const button = (label, action, id='', secondary=false) => `<button class="button${secondary?' secondary':''}" data-action="${action}"${id!==''?` data-id="${h(id)}"`:''}>${h(label)}</button>`;
const pill = (status) => `<span class="pill ${['NOUA','PROGRAMATA'].includes(status)?'amber':['INCHISA','EFECTUATA'].includes(status)?'teal':status==='ANULATA'?'':'blue'}">${h(statuses[status] || status)}</span>`;
const demoPill = (p) => `${p.demo?'<span class="pill blue">Demonstrativ</span>':''}${p.archived?'<span class="pill">Arhivat</span>':''}`;
const field = (name,label,value='',type='text',extra='') => `<div class="field"><label for="f-${name}">${h(label)}</label><input id="f-${name}" name="${name}" type="${type}" value="${h(value)}" ${extra}></div>`;
const select = (name,label,options,value='',extra='') => `<div class="field"><label for="f-${name}">${h(label)}</label><select id="f-${name}" name="${name}" ${extra}>${options.map(([v,t])=>`<option value="${h(v)}" ${String(v)===String(value)?'selected':''}>${h(t)}</option>`).join('')}</select></div>`;
const patientOptions = (all=true) => [...(all?[['','Toți pacienții']]:[]),...state.patients.filter(p=>!p.archived).map(p=>[p.id,`${p.fullName}${p.demo?' · demo':''}`])];
const empty = (title,description,action='') => `<div class="empty"><span class="empty-symbol" aria-hidden="true">◇</span><h3>${h(title)}</h3><p>${h(description)}</p>${action}</div>`;
const heading = (title,description,actions='',eyebrow='SPAȚIUL TĂU DE LUCRU') => `<div class="page-heading"><div><p class="eyebrow">${h(eyebrow)}</p><h1>${h(title)}</h1><p>${h(description)}</p></div><div class="heading-actions">${actions}</div></div>`;
const stat = (label,value,description,kind='vitals',unit='',warn=false) => `<div class="stat${warn?' warn':''}"><div class="stat-label">${h(label)}</div>${icon(kind,'stat-icon')}<div class="stat-value">${h(value)}${unit?`<small>${h(unit)}</small>`:''}</div><div class="stat-description">${h(description)}</div></div>`;
function toast(message,error=false) {clearTimeout(toastTimer);$('toast').textContent=message;$('toast').classList.toggle('error',error);$('toast').setAttribute('role',error?'alert':'status');$('toast').hidden=false;if(!error)toastTimer=setTimeout(()=>$('toast').hidden=true,4500);}
async function api(url,options={}) {
 const headers={...options.headers};
 if(options.body!==undefined){headers['Content-Type']='application/json';headers[document.querySelector('meta[name="_csrf_header"]').content]=document.querySelector('meta[name="_csrf"]').content;options.body=JSON.stringify(options.body);}
 const response=await fetch(url.startsWith('/web-api/')?url:base+url,{...options,headers});
 if(response.status===401){stopped=true;location.href='/login.html';throw new Error('Sesiunea a expirat.');}
 if(!response.ok){let error;try{error=await response.json();}catch{}throw new Error(error?.message || (response.status===403?'Nu ai permisiunea pentru această acțiune.':`Cererea a eșuat (${response.status}).`));}
 return response.json();
}
function openEditor(title,html,onSave,label='Salvează') {
 editorSave=onSave;$('dialogTitle').textContent=title;$('dialogFields').innerHTML=html;$('dialogError').textContent='';$('dialogSave').textContent=label;$('dialogSave').disabled=false;$('editor').showModal();
}
function filtersParams(patientId=state.patientId) {const q=new URLSearchParams();if(patientId)q.set('patientId',patientId);if(state.from)q.set('from',state.from);if(state.to)q.set('to',state.to);return q;}
function exportLink(patientId=state.patientId) {return `<a class="button secondary" href="${base}/measurements.csv?${filtersParams(patientId)}">Exportă CSV</a>`;}
function measurementTable(rows,metric='all',showPatient=true) {
 if(!rows.length)return empty('Încă nu există măsurători','Adaugă o măsurătoare sau pornește o simulare pentru un pacient demonstrativ.');
 const keys=metric==='all'?Object.keys(metrics):[metric];
 return `<div class="table-wrap"><table><thead><tr><th>Data și ora</th>${showPatient?'<th>Pacient</th>':''}${keys.map(k=>`<th>${h(metrics[k][0])}</th>`).join('')}</tr></thead><tbody>${rows.map(v=>`<tr><td>${h(date(v.timestamp,true))}</td>${showPatient?`<td><a href="/pacienti/${v.patientId}">${h(v.patientName || `Pacient #${v.patientId}`)}</a></td>`:''}${keys.map(k=>`<td>${h(v[k])} <span class="muted micro">${h(metrics[k][1])}</span></td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
}
function chart(rows,metric='heartRate',counts=false) {
 if(!rows.length)return '<div class="chart-empty">Graficul va apărea după prima măsurătoare.</div>';
 const ordered=[...rows].sort((a,b)=>String(a.timestamp||a.date).localeCompare(String(b.timestamp||b.date)));
 const series=counts?[{color:'#178678',values:ordered.map(r=>Number(r.count)),label:'Măsurători'}]:metric==='bloodPressure'?[{color:'#178678',values:ordered.map(r=>Number(r.bloodPressure.split('/')[0])),label:'Sistolică'},{color:'#9da5d6',values:ordered.map(r=>Number(r.bloodPressure.split('/')[1])),label:'Diastolică'}]:[{color:'#178678',values:ordered.map(r=>Number(r[metric])),label:metrics[metric][0]}];
 const vals=series.flatMap(s=>s.values);let low=counts?0:Math.floor(Math.min(...vals)-2),high=Math.ceil(Math.max(...vals)+(counts?1:2));if(high===low)high=low+1;
 const left=42,top=20,width=630,height=160,x=i=>left+(ordered.length===1?width/2:i*width/(ordered.length-1)),y=v=>top+height-(v-low)*height/(high-low);
 const grid=Array.from({length:5},(_,i)=>{const val=low+(high-low)*i/4;return `<line class="chart-grid" x1="${left}" x2="${left+width}" y1="${y(val)}" y2="${y(val)}"/><text x="${left-10}" y="${y(val)+3}" text-anchor="end">${Number(val.toFixed(metric==='temperature'?1:0))}</text>`;}).join('');
 const paths=series.map((s,j)=>{const points=s.values.map((v,i)=>`${x(i)},${y(v)}`).join(' ');return `${j===0?`<polygon points="${x(0)},${top+height} ${points} ${x(ordered.length-1)},${top+height}" fill="${s.color}" opacity=".06"/>`:''}<polyline points="${points}" fill="none" stroke="${s.color}" stroke-width="2.4" stroke-linejoin="round"/>${s.values.map((v,i)=>`<circle cx="${x(i)}" cy="${y(v)}" r="${rows.length>30?2:3}" fill="white" stroke="${s.color}" stroke-width="1.7"><title>${h(date(ordered[i].timestamp||ordered[i].date,true))} · ${h(s.label)}: ${v}</title></circle>`).join('')}`;}).join('');
 const singleDay=!counts&&ordered.every(r=>String(r.timestamp).slice(0,10)===String(ordered[0].timestamp).slice(0,10));
 const indices=[...new Set([0,Math.floor((ordered.length-1)/3),Math.floor((ordered.length-1)*2/3),ordered.length-1])];
 return `<svg class="chart" viewBox="0 0 700 215" role="img" aria-label="${h(counts?'Numărul măsurătorilor pe zi':metrics[metric][0]+' în timp')}">${grid}${paths}${indices.map(i=>`<text x="${x(i)}" y="205" text-anchor="middle">${h(new Date(ordered[i].timestamp||ordered[i].date).toLocaleString('ro-RO',singleDay?{hour:'2-digit',minute:'2-digit',second:'2-digit'}:{day:'2-digit',month:'short'}))}</text>`).join('')}</svg><div class="chart-label">${series.map(s=>`<span class="chart-legend"><i class="legend-line" style="background:${s.color}"></i>${h(s.label)}</span>`).join('')}</div>`;
}
function pagination(total,page,size,action) {const pages=Math.ceil(total/size);return `<div class="pagination"><span>${total?`${page*size+1}–${Math.min((page+1)*size,total)} din ${total}`:'0 înregistrări'}</span><div class="button-row"><button class="button secondary small" data-action="${action}" data-id="${page-1}" ${page===0?'disabled':''}>Anterior</button><span>${pages?page+1:0} / ${pages}</span><button class="button secondary small" data-action="${action}" data-id="${page+1}" ${page+1>=pages?'disabled':''}>Următor</button></div></div>`;}
function configureNavigation() {
 const links=staff()?[['/','dashboard','Dashboard'],['/pacienti','patients','Pacienți'],['/masuratori','vitals','Măsurători'],['/alerte','alerts','Alerte'],['/programari','calendar','Programări'],['/simulator','simulation','Simulator'],...(admin()?[['/administrare','users','Administrare']]:[]),['/cont','profile','Contul meu']]:[['/cont','profile','Contul meu'],['/programari','calendar','Programări']];
 $('navigation').innerHTML=links.map(([url,kind,label])=>`<a href="${url}" class="${path===url||(url==='/pacienti'&&path.startsWith('/pacienti/'))?'active':''}" ${path===url?'aria-current="page"':''}>${icon(kind)}${label}</a>`).join('');
 $('roleLabel').textContent=roleNames[state.me.role];$('avatar').textContent=initials(state.me.fullName||state.me.username);$('demoBanner').hidden=!state.me.demoMode;$('alertLink').hidden=!staff();$('today').textContent=new Date().toLocaleDateString('ro-RO',{weekday:'long',day:'numeric',month:'long',year:'numeric'});
 $('alertLink').innerHTML=icon('alerts');
 $('sectionLabel').textContent=links.find(([url])=>url===path||(url==='/pacienti'&&path.startsWith('/pacienti/')))?.[2] || 'Portal medical';
}
async function dashboardPage() {
 const d=await api('/dashboard');
 const mini=d.upcoming.map(a=>`<div class="appointments-mini"><div class="date-tile"><small>${h(new Date(a.scheduledAt).toLocaleDateString('ro-RO',{month:'short'}))}</small>${new Date(a.scheduledAt).getDate()}</div><div><a href="/pacienti/${a.patientId}"><strong>${h(a.patientName)}</strong></a><p>${h(new Date(a.scheduledAt).toLocaleTimeString('ro-RO',{hour:'2-digit',minute:'2-digit'}))} · ${h(a.durationMinutes)} min</p><p>${h(a.reason)}</p></div></div>`).join('');
 return heading('O privire de ansamblu','Pacienții, măsurătorile și activitatea ta, actualizate în timp real.',button('+ Măsurătoare nouă','new-vital'),'BINE AI VENIT, '+(state.me.fullName||state.me.username).toUpperCase())+
 `<div class="grid stats">${stat('Pacienți activi',d.patients,'Fișe în evidența ta','patients')}${stat('Măsurători înregistrate',d.measurements,'Istoricul disponibil','vitals')}${stat('Alerte de urmărit',d.activeAlerts,'Noi și verificate','alerts','',true)}${stat('Programări viitoare',d.appointments,'Consultații programate','calendar')}</div>
 <div class="grid two-columns"><section class="card"><div class="card-head"><div><h2>Activitatea ultimelor 7 zile</h2><p>Măsurători înregistrate zilnic pentru pacienții tăi.</p></div><span class="pill teal">Ultima săptămână</span></div>${chart(d.trend,'heartRate',true)}</section><section class="card"><div class="card-head"><div><h2>Urmează în calendar</h2><p>Următoarele consultații.</p></div><a class="text-button" href="/programari">Vezi calendarul</a></div>${mini||empty('Calendar liber','Nu ai programări viitoare.',button('Adaugă programare','new-appointment','',true))}${d.activeAlerts?`<div class="attention-box">Ai ${d.activeAlerts} alerte de urmărit. <a href="/alerte">Deschide alertele</a></div>`:''}</section></div>
 <section class="card"><div class="card-head"><div><h2>Măsurători recente</h2><p>Ultimele valori primite, inclusiv din simulator.</p></div><a class="text-button" href="/masuratori">Vezi tot istoricul</a></div>${measurementTable(d.latest)}</section>`;
}
async function patientsPage() {
 const list=await api(`/patients?q=${encodeURIComponent(state.q)}&archived=${state.archived}`);state.patients=list;
 const size=10;if(state.patientPage*size>=list.length)state.patientPage=0;const rows=list.slice(state.patientPage*size,(state.patientPage+1)*size);
 return heading('Pacienți','Fișe individuale, date de contact și repartizarea către medic.',button('+ Adaugă pacient','new-patient'))+
 `<section class="card"><form class="filters" data-filter="patients">${field('q','Caută un pacient',state.q,'search','placeholder="Nume, ID sau email"')}${select('archived','Fișe afișate',[['false','Pacienți activi'],['true','Pacienți arhivați']],String(state.archived))}<button class="button secondary">Caută</button></form>${rows.length?`<div class="table-wrap"><table><thead><tr><th>Pacient</th><th>Contact</th><th>Medic responsabil</th><th>Tip fișă</th><th>Acțiuni</th></tr></thead><tbody>${rows.map(p=>`<tr><td><a class="patient-name" href="/pacienti/${p.id}"><span class="avatar">${h(initials(p.fullName))}</span><span><strong>${h(p.fullName)}</strong><small>#${p.id} · ${p.age??'—'} ani</small></span></a></td><td>${h(p.phone||'—')}<br><span class="micro muted">${h(p.email)}</span></td><td>${h(p.doctorName)}</td><td>${demoPill(p)||'<span class="pill teal">Activ</span>'}</td><td><div class="row-actions"><button class="text-button" data-action="edit-patient" data-id="${p.id}">Editează</button><button class="text-button" data-action="archive-patient" data-id="${p.id}">${p.archived?'Reactivează':'Arhivează'}</button></div></td></tr>`).join('')}</tbody></table></div>${pagination(list.length,state.patientPage,size,'patients-page')}`:empty(state.q?'Niciun rezultat':'Nicio fișă în această listă',state.q?'Încearcă alt nume sau șterge termenul de căutare.':'Adaugă primul pacient pentru a înregistra măsurători.',button('Adaugă pacient','new-patient','',true))}</section>`;
}
function measurementFilters(patientId=state.patientId,record=false) {return `<form class="filters" data-filter="${record?'record':'measurements'}">${record?'':select('patientId','Pacient',patientOptions(),patientId)}${field('from','De la',state.from,'date')}${field('to','Până la',state.to,'date')}${select('metric','Indicator afișat',record?Object.entries(metrics).map(([k,v])=>[k,v[0]]):[['all','Toți indicatorii'],...Object.entries(metrics).map(([k,v])=>[k,v[0]])],state.metric)}<button class="button secondary">Aplică filtrele</button><button type="button" class="text-button" data-action="reset-filters">Resetează</button></form>`;}
async function measurementsPage() {
 state.patients=await api('/patients');const q=filtersParams();q.set('page',state.page);q.set('size',20);const result=await api('/measurements?'+q);
 return heading('Măsurători','Înregistrează, filtrează și exportă istoricul semnelor vitale.',button('+ Măsurătoare nouă','new-vital'))+
 `<section class="card">${measurementFilters()}<div class="card-head"><div><h2>Istoricul măsurătorilor</h2><p>${result.total} înregistrări pentru filtrele selectate.</p></div>${exportLink()}</div>${measurementTable(result.items,state.metric==='heartRate'&&state.initialMeasurements?'all':state.metric)}${pagination(result.total,state.page,20,'measurements-page')}</section>`;
}
function ruleText(r) {return `Puls: ${r.heartRateMin}–${r.heartRateMax} BPM · SpO₂: minimum ${r.oxygenMin}%<br>Temperatură: ${r.temperatureMin}–${r.temperatureMax} °C<br>Tensiune: sistolică ${r.systolicMin}–${r.systolicMax}; diastolică ${r.diastolicMin}–${r.diastolicMax} mmHg`;}
async function recordPage(id,isOwn=false) {
 const d=await api('/patients/'+id),p=d.patient;state.record=p;
 const q=filtersParams(id);q.set('page',state.page);q.set('size',20);const result=await api('/measurements?'+q);
 const chartQ=filtersParams(id);chartQ.set('size',100);const chartData=await api('/measurements?'+chartQ);
 const alerts=await api('/alerts?patientId='+id);const latest=d.vitals[0];
 return `${!isOwn?'<div class="breadcrumb"><a href="/pacienti">Pacienți</a> / Fișa pacientului</div>':''}`+heading(isOwn?'Contul meu':p.fullName,isOwn?`Bine ai venit, ${p.fullName}. Aici găsești propriile date și programări.`:'Evoluție, istoric și observații, reunite în aceeași fișă.',`${staff()?button('Editează fișa','edit-patient',id,true):''}${staff()&&!p.archived?button('+ Măsurătoare','new-vital',id):''}`,'FIȘĂ PACIENT · #'+id)+
 `${p.archived?'<div class="archive-notice">Această fișă este arhivată. Istoricul rămâne disponibil; reactivează fișa pentru înregistrări noi.</div>':''}<div class="grid stats">${stat('Puls',latest?.heartRate??'—',latest?date(latest.timestamp,true):'Fără măsurători','vitals','BPM')}${stat('Saturație oxigen',latest?.oxygenLevel??'—','Ultima valoare înregistrată','vitals','%')}${stat('Temperatură',latest?.temperature??'—','Ultima valoare înregistrată','vitals','°C')}${stat('Tensiune arterială',latest?.bloodPressure??'—','Ultima valoare înregistrată','vitals','mmHg')}</div>
 <div class="grid two-columns"><section class="card"><div class="card-head"><div><h2>Evoluția semnelor vitale</h2><p>Ultimele 100 de măsurători din intervalul selectat.</p></div></div>${measurementFilters(id,true)}${chart(chartData.items,state.metric==='all'?'heartRate':state.metric)}</section><section class="card"><div class="profile-title"><span class="avatar large">${h(initials(p.fullName))}</span><div><h2>${h(p.fullName)}</h2><div class="button-row">${demoPill(p)}</div></div></div><dl class="profile-info"><div><dt>Data nașterii</dt><dd>${h(date(p.birthDate))}</dd></div><div><dt>Vârstă / sex</dt><dd>${h(p.age??'—')} ani · ${h(p.sex||'—')}</dd></div><div><dt>Telefon</dt><dd>${h(p.phone||'—')}</dd></div><div><dt>Email</dt><dd>${h(p.email||'—')}</dd></div><div><dt>Medic responsabil</dt><dd>${h(p.doctorName)}</dd></div><div><dt>Cont web</dt><dd>${h(p.username||'Fără cont asociat')}</dd></div></dl><div class="section-gap"><h3>Praguri de monitorizare</h3><div class="rule-summary">${ruleText(d.rules)}</div><p class="hint">Praguri demonstrative, configurate pentru această fișă. Modificarea lor se aplică măsurătorilor viitoare.</p>${staff()&&!p.archived?button('Configurează pragurile','edit-rules',id,true):''}</div></section></div>
 <section class="card"><div class="card-head"><div><h2>Istoric măsurători</h2><p>${result.total} înregistrări pentru intervalul selectat.</p></div>${exportLink(id)}</div>${measurementTable(result.items,'all',false)}${pagination(result.total,state.page,20,'measurements-page')}</section>
 <div class="grid equal-columns section-gap"><section class="card"><div class="card-head"><div><h2>Observații medicale</h2><p>Însemnările medicului responsabil.</p></div></div>${staff()&&!p.archived?`<form data-form="note"><label for="noteBody">Adaugă o observație</label><textarea id="noteBody" name="body" minlength="3" maxlength="4000" placeholder="Scrie observația pentru acest pacient…" required></textarea><button class="button small section-gap">Salvează observația</button></form>`:''}${d.notes.map(n=>`<article class="note"><p>${h(n.body)}</p><small>${h(n.author)} · ${h(date(n.createdAt,true))}</small></article>`).join('')||'<p class="hint section-gap">Nu există observații în această fișă.</p>'}</section><section class="card"><div class="card-head"><div><h2>Alertele pacientului</h2><p>${alerts.filter(a=>a.status!=='INCHISA').length} alerte de urmărit.</p></div>${staff()?'<a class="text-button" href="/alerte?patientId='+id+'">Vezi toate</a>':''}</div>${alerts.slice(0,5).map(a=>`<article class="note"><div class="button-row">${pill(a.status)}<span class="hint">${h(date(a.createdAt,true))}</span></div><p>${h(a.message)}</p></article>`).join('')||empty('Nicio alertă','Valorile înregistrate nu au declanșat alerte pentru pragurile configurate.')}<a class="button secondary" href="/programari">Deschide programările</a></section></div>`;
}
async function ownPage() {
 if(state.me.role!=='ROLE_PATIENT')return heading('Contul meu','Datele contului cu care ești autentificat.')+`<section class="card"><div class="profile-title"><span class="avatar large">${h(initials(state.me.fullName||state.me.username))}</span><div><h2>${h(state.me.fullName||state.me.username)}</h2><p>${h(roleNames[state.me.role])}</p></div></div><dl class="profile-info"><div><dt>Utilizator</dt><dd>${h(state.me.username)}</dd></div><div><dt>Rol</dt><dd>${h(roleNames[state.me.role])}</dd></div></dl><a class="button section-gap" href="/">Deschide dashboardul</a></section>`;
 if(!state.me.patientId)return heading('Contul meu','Contul tău este activ.')+`<section class="card">${empty('Fișa se pregătește','Administratorul poate asocia contului tău o fișă de pacient.')}</section>`;
 return recordPage(state.me.patientId,true);
}
async function alertsPage() {
 const list=await api('/alerts'+(state.patientId?'?patientId='+state.patientId:''));state.alerts=list;
 const filtered=list.filter(a=>state.alertStatus==='ACTIVE'?a.status!=='INCHISA':!state.alertStatus||a.status===state.alertStatus);
 return heading('Alerte','Urmărește depășirile pragurilor configurate și notează verificarea lor.',`<a class="button secondary" href="/simulator">Deschide simulatorul</a>`)+`<section class="card"><form class="filters" data-filter="alerts">${select('status','Stare',[['ACTIVE','De urmărit'],['NOUA','Noi'],['VERIFICATA','Verificate'],['INCHISA','Închise'],['','Toate']],state.alertStatus)}<button class="button secondary">Aplică filtrul</button>${state.patientId?'<button class="text-button" type="button" data-action="all-alerts">Toți pacienții</button>':''}</form><p class="hint">Flux de lucru: Nouă → Verificată → Închisă. O alertă închisă rămâne în istoric.</p></section><div class="grid section-gap">${filtered.map(a=>`<article class="card alert-card ${a.status==='INCHISA'?'closed':''}"><div class="card-head"><div><h2><a href="/pacienti/${a.patientId}">${h(a.patientName)}</a></h2><p>${h(a.metric)} · Măsurătoarea #${a.measurementId}</p></div>${pill(a.status)}</div><p>${h(a.message)}</p><div class="alert-meta"><span>Creată ${h(date(a.createdAt,true))}</span>${a.reviewedBy?`<span>${h(a.reviewedBy)} · ${h(date(a.updatedAt,true))}</span>`:''}</div><div class="alert-actions"><button class="text-button" data-action="edit-rules" data-id="${a.patientId}">Pragurile pacientului</button>${a.status==='NOUA'?button('Marchează verificată','verify-alert',a.id,true):a.status==='VERIFICATA'?button('Închide alerta','close-alert',a.id,true):'<span class="micro muted">Închisă și păstrată în istoric</span>'}</div></article>`).join('')||`<section class="card">${empty('Nu există alerte în această listă','Alertele apar automat atunci când o măsurătoare depășește pragurile configurate.')}</section>`}</div>`;
}
async function appointmentsPage() {
 state.patients=await api('/patients');
 const first=new Date(state.month);first.setDate(first.getDate()-((first.getDay()+6)%7));const end=new Date(first);end.setDate(end.getDate()+41);
 const list=await api(`/appointments?from=${isoDay(first)}&to=${isoDay(end)}`);state.appointments=list;
 const agenda=list.filter(a=>state.day?a.scheduledAt.startsWith(state.day):new Date(a.scheduledAt).getMonth()===state.month.getMonth());
 const cells=Array.from({length:42},(_,i)=>{const day=new Date(first);day.setDate(day.getDate()+i);const key=isoDay(day),items=list.filter(a=>a.scheduledAt.startsWith(key));return `<div class="calendar-day ${day.getMonth()!==state.month.getMonth()?'outside':''} ${key===isoDay(new Date())?'today':''} ${state.day===key?'selected':''}"><button class="day-number" data-action="calendar-day" data-id="${key}" aria-label="${h(date(key))}">${day.getDate()}</button>${items.slice(0,2).map(a=>`<button class="calendar-event ${a.status==='ANULATA'?'cancelled':''}" data-action="edit-appointment" data-id="${a.id}" title="${h(a.patientName+' · '+a.reason)}">${a.scheduledAt.slice(11,16)} ${h(staff()?a.patientName:a.reason)}</button>`).join('')}${items.length>2?`<button class="text-button micro" data-action="calendar-day" data-id="${key}">+${items.length-2} programări</button>`:''}</div>`;}).join('');
 return heading('Programări','Calendarul consultațiilor și organizarea timpului disponibil.',button('+ Programare nouă','new-appointment'))+`<div class="grid two-columns"><section class="card"><div class="card-head"><div class="calendar-controls"><button class="button secondary small" data-action="calendar-prev">Luna anterioară</button><h2>${h(state.month.toLocaleDateString('ro-RO',{month:'long',year:'numeric'}))}</h2><button class="button secondary small" data-action="calendar-next">Luna următoare</button></div><button class="text-button" data-action="calendar-today">Astăzi</button></div><div class="calendar-weekdays">${['Lun','Mar','Mie','Joi','Vin','Sâm','Dum'].map(d=>`<span>${d}</span>`).join('')}</div><div class="calendar-grid">${cells}</div><div class="calendar-legend"><span>Ore locale · Europe/București</span><span>Suprapunerile sunt verificate la salvare.</span></div></section><section class="card"><div class="card-head"><div><h2>${state.day?h(date(state.day)):'Agenda lunii'}</h2><p>${agenda.length} programări în selecție.</p></div>${state.day?'<button class="text-button" data-action="calendar-all">Toată luna</button>':''}</div>${agenda.map(a=>`<div class="agenda-item"><span class="agenda-time">${a.scheduledAt.slice(11,16)}</span><div class="agenda-info"><div class="agenda-date">${h(date(a.scheduledAt))}</div><strong>${h(a.patientName)}</strong><p>${h(a.reason)}<br>${h(a.doctorName)} · ${a.durationMinutes} min</p><div class="button-row">${pill(a.status)}<button class="text-button" data-action="edit-appointment" data-id="${a.id}">${staff()?'Editează':'Detalii'}</button></div></div></div>`).join('')||empty('Nicio programare','Alege o altă zi sau adaugă o programare nouă.')}</section></div>`;
}
async function administrationPage() {
 const users=await api('/users');state.users=users;
 return heading('Administrare','Conturi, roluri și repartizarea pacienților către medici.',button('+ Creează cont','new-user'))+`<section class="card"><div class="card-head"><div><h2>Utilizatorii aplicației</h2><p>${users.length} conturi. Parolele nu sunt afișate și nu sunt trimise în liste.</p></div></div><div class="table-wrap"><table><thead><tr><th>Utilizator</th><th>Rol</th><th>Stare</th><th>Acțiuni</th></tr></thead><tbody>${users.map(u=>`<tr><td><div class="patient-name"><span class="avatar">${h(initials(u.fullName))}</span><span><strong>${h(u.fullName)}</strong><small>${h(u.username)}</small></span></div></td><td><span class="pill ${u.role==='ROLE_ADMIN'?'blue':u.role==='ROLE_MEDIC'?'teal':''}">${h(roleNames[u.role])}</span></td><td><span class="pill ${u.enabled?'teal':''}">${u.enabled?'Activ':'Dezactivat'}</span></td><td><div class="row-actions"><button class="text-button" data-action="edit-user" data-id="${u.id}">Editează contul</button>${u.patientId?`<button class="text-button" data-action="edit-patient" data-id="${u.patientId}">Repartizează medic</button>`:''}</div></td></tr>`).join('')}</tbody></table></div><div class="attention-box">Conturile noi cu rol de pacient primesc automat o fișă. Repartizează medicul din această listă sau din pagina Pacienți. Înainte de dezactivarea unui medic, repartizează pacienții lui altui medic.</div></section>`;
}
async function simulatorPage() {
 state.patients=await api('/patients');const demos=state.patients.filter(p=>p.demo&&!p.archived);if(!state.simulationPatient&&demos.length)state.simulationPatient=String(demos[0].id);
 const runs=await api('/simulation');state.runs=runs;const selected=runs.find(r=>String(r.patientId)===state.simulationPatient);
 let values=[];if(state.simulationPatient)values=(await api('/measurements?patientId='+state.simulationPatient+'&size=20')).items;
 return heading('Simulator în timp real','Generează măsurători fictive și urmărește graficele, istoricul și alertele.',button('+ Pacient demonstrativ','new-demo','',true))+
 `<div class="grid two-columns"><section class="card"><div class="card-head"><div><h2>Configurează simularea</h2><p>Disponibilă doar pentru fișele marcate ca demonstrative.</p></div><span class="pill blue">Date fictive</span></div>${demos.length?`<form data-form="simulation">${select('patientId','Pacient demonstrativ',demos.map(p=>[p.id,p.fullName]),state.simulationPatient)}<div class="form-grid">${select('scenario','Scenariu',[['NORMAL','Valori în intervalul demonstrativ'],['VARIABIL','Variabil · cu depășiri periodice']],state.scenario)}${field('intervalSeconds','Interval între măsurători (secunde)',state.simulationInterval,'number','min="2" max="30" required')}</div><p class="hint">O sesiune durează cel mult 5 minute și produce maximum 100 de măsurători. Scenariul variabil generează o depășire la fiecare a patra măsurătoare. Pragurile personalizate pot schimba alertele rezultate.</p><button class="button" ${selected?.running?'disabled':''}>Pornește simularea</button></form>`:empty('Adaugă un pacient demonstrativ','Fișele obișnuite nu pot fi folosite de simulator.',button('Creează fișă demo','new-demo','',true))}</section><section class="card"><div class="sim-indicator ${selected?.running?'running-indicator':''}">${icon('simulation')}</div><h2>${selected?.running?'Simulare în desfășurare':'Simulator pregătit'}</h2><p>${selected?`Inițiată de ${h(selected.owner)} · ${h(date(selected.startedAt,true))}`:'Pornește o sesiune pentru a demonstra fluxul live.'}</p><div class="sim-number">${selected?.generated??0}<span class="micro muted"> măsurători în sesiune</span></div><p class="hint">${h(selected?.reason||'Măsurătorile se salvează în baza de date și sunt transmise prin WebSocket.')}</p>${selected?.running&&(admin()||selected.owner===state.me.username)?button('Oprește simularea','stop-simulation',selected.patientId,true):''}<div class="section-gap"><a class="text-button" href="/alerte">Urmărește alertele</a></div></section></div>
 <section class="card"><div class="card-head"><div><h2>Puls · fluxul pacientului selectat</h2><p>Ultimele 20 de măsurători. Poți deschide aceeași fișă și într-o a doua filă.</p></div>${state.simulationPatient?`<a class="text-button" href="/pacienti/${state.simulationPatient}">Deschide fișa</a>`:''}</div>${chart(values)}</section>
 <section class="card section-gap"><div class="card-head"><h2>Sesiunile recente</h2></div>${runs.length?`<div class="table-wrap"><table><thead><tr><th>Pacient</th><th>Scenariu</th><th>Stare</th><th>Valori generate</th><th>Acțiune</th></tr></thead><tbody>${runs.map(r=>`<tr><td>${h(state.patients.find(p=>p.id===r.patientId)?.fullName||r.patientId)}</td><td>${h(r.scenario==='VARIABIL'?'Variabil':'Normal')}</td><td><span class="pill ${r.running?'teal':''}">${r.running?'Activă':'Oprită'}</span></td><td>${r.generated}</td><td>${r.running&&(admin()||r.owner===state.me.username)?`<button class="text-button" data-action="stop-simulation" data-id="${r.patientId}">Oprește</button>`:h(r.reason||'—')}</td></tr>`).join('')}</tbody></table></div>`:empty('Nicio sesiune încă','Sesiunile inițiate vor apărea aici.')}</section>`;
}
async function render(quiet=false) {
 const request=++state.request;
 try {
  let content;
  if(path==='/'||path==='/index.html')content=await dashboardPage();
  else if(path==='/pacienti')content=await patientsPage();
  else if(/^\/pacienti\/\d+$/.test(path))content=await recordPage(Number(path.split('/').pop()));
  else if(path==='/masuratori')content=await measurementsPage();
  else if(path==='/alerte')content=await alertsPage();
  else if(path==='/programari')content=await appointmentsPage();
  else if(path==='/administrare')content=await administrationPage();
  else if(path==='/simulator')content=await simulatorPage();
  else content=await ownPage();
  if(request!==state.request)return;
  if(state.markup!==content){$('content').innerHTML=content;state.markup=content;}$('content').setAttribute('aria-busy','false');
  document.title=$('content').querySelector('h1')?.textContent+' · Monitorizare medicală';
 }catch(error){if(quiet)toast(error.message,true);else $('content').innerHTML=`<div class="inline-error" role="alert"><h2>Nu am putut încărca pagina</h2><p>${h(error.message)}</p><button class="button secondary" data-action="retry">Încearcă din nou</button></div>`;$('content').setAttribute('aria-busy','false');}
}
function scheduleRefresh() {clearTimeout(refreshTimer);refreshTimer=setTimeout(()=>{if(!$('editor').open&&!document.activeElement.matches('input,textarea,select'))render(true);},400);}
function connect() {
 if(stopped)return;
 socket=new WebSocket(`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}/ws-medical`);
 socket.onopen=()=>{$('liveStatus').textContent='Flux live conectat';$('liveStatus').classList.add('connected');scheduleRefresh();};
 socket.onmessage=()=>scheduleRefresh();
 socket.onclose=()=>{$('liveStatus').textContent='Reconectare…';$('liveStatus').classList.remove('connected');if(!stopped)setTimeout(async()=>{try{await api('/me');connect();}catch(error){toast(error.message,true);}},5000);};
 socket.onerror=()=>socket.close();
}

async function patientEditor(id,demo=false) {
 const existing=id?(await api('/patients/'+id)).patient:{};const doctors=await api('/doctors');const users=admin()?await api('/users'):[];
 const html=`<div class="form-grid">${field('fullName','Nume complet',existing.fullName,'text','minlength="2" maxlength="100" required')}${field('birthDate','Data nașterii',existing.birthDate,'date',`max="${isoDay(new Date())}"`)}${select('sex','Sex',[['','Nespecificat'],['Feminin','Feminin'],['Masculin','Masculin'],['Altul','Altul']],existing.sex)}${field('phone','Telefon',existing.phone,'tel','maxlength="30"')}${field('email','Email',existing.email,'email','maxlength="120"')}${select('doctorId','Medic responsabil',[...(admin()?[['','Nerepartizat']]:[]),...doctors.map(d=>[d.id,d.fullName||d.username])],existing.doctorId||(!admin()?state.me.id:''))}${admin()?select('userId','Cont de pacient asociat',[['','Fără cont web'],...users.filter(u=>u.role==='ROLE_PATIENT'&&u.enabled&&(!u.patientId||u.id===existing.userId)).map(u=>[u.id,u.username])],existing.userId||''):''}</div>${id?`<p class="hint">${existing.demo?'Fișă demonstrativă.':'Fișă obișnuită. Tipul unei fișe existente nu se schimbă.'}</p>`:`<label class="check-label"><input type="checkbox" name="demo" ${demo?'checked':''}> Fișă demonstrativă, disponibilă în simulator</label>`}`;
 openEditor(id?'Editează pacientul':'Adaugă pacient',html,async values=>{const body={...values,birthDate:values.birthDate||null,doctorId:values.doctorId?Number(values.doctorId):null,userId:admin()?(values.userId?Number(values.userId):null):(existing.userId??null),demo:id?existing.demo:values.demo==='on'};await api('/patients'+(id?'/'+id:''),{method:id?'PUT':'POST',body});},id?'Salvează modificările':'Creează fișa');
}
async function vitalEditor(id) {
 const ps=await api('/patients');state.patients=ps;
 if(!ps.length){toast('Adaugă mai întâi un pacient.',true);return patientEditor();}
 openEditor('Înregistrează o măsurătoare',`${select('patientId','Pacient',patientOptions(false),id||state.patientId||ps[0].id,'required')}<div class="form-grid">${field('heartRate','Puls (BPM)',75,'number','min="1" max="300" required')}${field('oxygenLevel','SpO₂ (%)',98,'number','min="0" max="100" required')}${field('temperature','Temperatură (°C)',36.6,'number','min="20" max="50" step="0.1" required')}${field('bloodPressure','Tensiune (mmHg)','120/80','text','pattern="[0-9]{2,3}/[0-9]{2,3}" required')}</div><p class="hint">Data și ora sunt înregistrate automat la salvare.</p>`,async v=>{await api('/web-api/vitals',{method:'POST',body:{patientId:Number(v.patientId),heartRate:Number(v.heartRate),oxygenLevel:Number(v.oxygenLevel),temperature:Number(v.temperature),bloodPressure:v.bloodPressure}});},'Salvează măsurătoarea');
}
async function ruleEditor(id) {
 const d=await api('/patients/'+id),r=d.rules;
 const definitions=[['heartRateMin','Puls minim',1,299,1],['heartRateMax','Puls maxim',2,300,1],['oxygenMin','SpO₂ minim (%)',0,100,1],['temperatureMin','Temperatură minimă (°C)',20,49,.1],['temperatureMax','Temperatură maximă (°C)',21,50,.1],['systolicMin','Tensiune sistolică minimă',1,299,1],['systolicMax','Tensiune sistolică maximă',2,300,1],['diastolicMin','Tensiune diastolică minimă',1,199,1],['diastolicMax','Tensiune diastolică maximă',2,200,1]];
 openEditor('Praguri · '+d.patient.fullName,`<p class="hint">Aceste praguri demonstrative se aplică măsurătorilor viitoare. Alertele deja create rămân în istoric.</p><div class="form-grid">${definitions.map(([key,label,min,max,step])=>field(key,label,r[key],'number',`min="${min}" max="${max}" step="${step}" required`)).join('')}</div>`,async values=>{await api('/patients/'+id+'/rules',{method:'PUT',body:Object.fromEntries(Object.entries(values).map(([k,v])=>[k,Number(v)]))});});
}
async function appointmentEditor(id) {
 const existing=id?state.appointments.find(a=>a.id===Number(id)):null;state.patients=await api('/patients');
 if(!state.patients.length){toast('Nu există o fișă disponibilă pentru programare.',true);return;}
 const fallback=`${state.day||isoDay(new Date(Date.now()+86400000))}T10:00`;
 if(!staff()&&existing){openEditor('Detalii programare',`<p><strong>${h(existing.reason)}</strong></p><p>${h(date(existing.scheduledAt,true))} · ${existing.durationMinutes} minute<br>${h(existing.doctorName)}</p><p>${pill(existing.status)}</p><p class="hint">Pacientul poate anula programările active. Reprogramarea se face de către medic.</p>`,async()=>{await api('/appointments/'+id,{method:'PUT',body:{...existing,status:'ANULATA'}});},existing.status==='PROGRAMATA'?'Anulează programarea':'Închide');if(existing.status!=='PROGRAMATA')editorSave=async()=>{};return;}
 openEditor(existing?'Editează programarea':'Programare nouă',`${select('patientId','Pacient',patientOptions(false),existing?.patientId||state.me.patientId||state.record?.id||state.patients[0].id,existing?'disabled':'required')}<div class="form-grid">${field('scheduledAt','Data și ora',dateTimeInput(existing?.scheduledAt||fallback),'datetime-local','required')}${field('durationMinutes','Durata (minute)',existing?.durationMinutes||30,'number','min="10" max="180" step="5" required')}</div>${field('reason','Motivul consultației',existing?.reason||'','text','minlength="3" maxlength="500" required')}${existing?select('status','Stare',[['PROGRAMATA','Programată'],['EFECTUATA','Efectuată'],['ANULATA','Anulată']],existing.status):''}<p class="hint">Medicul este preluat din fișa pacientului. Intervalele suprapuse sunt respinse la salvare.</p>`,async v=>{await api('/appointments'+(id?'/'+id:''),{method:id?'PUT':'POST',body:{...v,patientId:existing?.patientId||Number(v.patientId),durationMinutes:Number(v.durationMinutes),status:v.status||'PROGRAMATA'}});});
}
async function userEditor(id) {
 const u=id?state.users.find(u=>u.id===Number(id)):{};
 openEditor(id?'Editează contul':'Creează un cont',`<div class="form-grid">${field('fullName','Nume complet',u.fullName,'text','minlength="2" maxlength="100" required')}${field('username','Utilizator',u.username,'text',id?'disabled':'minlength="3" maxlength="50" pattern="[A-Za-z0-9_.-]+" autocomplete="off" required')}${select('role','Rol',[['ROLE_PATIENT','Pacient'],['ROLE_MEDIC','Medic'],['ROLE_ADMIN','Administrator']],u.role||'ROLE_PATIENT')}${!id?field('password','Parolă inițială (minimum 12 caractere)','','password','minlength="12" maxlength="72" autocomplete="new-password" required'):''}</div><label class="check-label"><input name="enabled" type="checkbox" ${u.enabled===false?'':'checked'}> Cont activ</label><p class="hint">${id?'Parola și numele de utilizator rămân neschimbate.':'Conturile de pacient primesc automat o fișă; repartizează apoi un medic.'}</p>`,async v=>{await api('/users'+(id?'/'+id:''),{method:id?'PUT':'POST',body:{...v,enabled:v.enabled==='on'}});});
}

document.addEventListener('click',async event=>{
 const target=event.target.closest('[data-action]');if(!target||target.disabled)return;
 event.preventDefault();const action=target.dataset.action,id=target.dataset.id;
 try {
  if(action==='reload-session'){location.reload();return;}
  if(action==='new-patient')return await patientEditor();if(action==='new-demo')return await patientEditor(null,true);if(action==='edit-patient')return await patientEditor(Number(id));
  if(action==='new-vital')return await vitalEditor(id);if(action==='edit-rules')return await ruleEditor(Number(id));
  if(action==='new-appointment')return await appointmentEditor();if(action==='edit-appointment')return await appointmentEditor(Number(id));
  if(action==='new-user')return await userEditor();if(action==='edit-user')return await userEditor(Number(id));
  if(action==='archive-patient'){const p=state.patients.find(p=>p.id===Number(id));openEditor(p.archived?'Reactivează fișa':'Arhivează fișa',`<p>${h(p.fullName)}</p><p class="hint">${p.archived?'Fișa va fi disponibilă pentru măsurători și programări noi.':'Fișa și întregul istoric rămân în baza de date. Arhivarea se poate anula din lista pacienților arhivați.'}</p>`,async()=>{await api('/patients/'+id+'/archive?archived='+!p.archived,{method:'PUT',body:{}});},p.archived?'Reactivează':'Arhivează');return;}
  if(action==='verify-alert'||action==='close-alert'){target.disabled=true;await api('/alerts/'+id+'/status',{method:'PUT',body:{status:action==='verify-alert'?'VERIFICATA':'INCHISA'}});toast('Starea alertei a fost actualizată.');}
  if(action==='patients-page')state.patientPage=Number(id);
  if(action==='measurements-page')state.page=Number(id);
  if(action==='reset-filters'){state.from='';state.to='';state.patientId='';state.metric=path==='/masuratori'?'all':'heartRate';state.page=0;}
  if(action==='all-alerts')state.patientId='';
  if(action==='calendar-prev'){state.month=new Date(state.month.getFullYear(),state.month.getMonth()-1,1);state.day='';}
  if(action==='calendar-next'){state.month=new Date(state.month.getFullYear(),state.month.getMonth()+1,1);state.day='';}
  if(action==='calendar-today'){state.month=new Date(new Date().getFullYear(),new Date().getMonth(),1);state.day=isoDay(new Date());}
  if(action==='calendar-day')state.day=id;if(action==='calendar-all')state.day='';
  if(action==='stop-simulation'){await api('/simulation/'+id+'/stop',{method:'POST',body:{}});toast('Simularea a fost oprită.');}
  await render();
 }catch(error){toast(error.message,true);target.disabled=false;}
});
document.addEventListener('submit',async event=>{
 const form=event.target;if(form.id==='logout'){stopped=true;socket?.close();return;}
 if(form.id==='editorForm'){
  event.preventDefault();$('dialogSave').disabled=true;$('dialogError').textContent='';
  try{await editorSave(Object.fromEntries(new FormData(form)));$('editor').close();toast('Modificările au fost salvate.');await render();}catch(error){$('dialogError').textContent=error.message;}finally{$('dialogSave').disabled=false;}return;
 }
 if(!form.dataset.filter&&!form.dataset.form)return;event.preventDefault();
 const v=Object.fromEntries(new FormData(form));
 if(form.dataset.filter){
  if(form.dataset.filter==='patients'){state.q=v.q;state.archived=v.archived==='true';state.patientPage=0;}
  if(['measurements','record'].includes(form.dataset.filter)){state.patientId=v.patientId??state.patientId;state.from=v.from;state.to=v.to;state.metric=v.metric;state.page=0;}
  if(form.dataset.filter==='alerts')state.alertStatus=v.status;
  return render();
 }
 const submit=form.querySelector('[type="submit"],button:not([type])');if(submit)submit.disabled=true;
 try{
  if(form.dataset.form==='note'){await api('/patients/'+state.record.id+'/notes',{method:'POST',body:v});toast('Observația a fost salvată.');}
  if(form.dataset.form==='simulation'){state.simulationPatient=v.patientId;state.simulationInterval=Number(v.intervalSeconds);state.scenario=v.scenario;await api('/simulation/start',{method:'POST',body:{patientId:Number(v.patientId),intervalSeconds:Number(v.intervalSeconds),scenario:v.scenario}});toast('Simularea a pornit.');}
  await render();
 }catch(error){toast(error.message,true);if(submit)submit.disabled=false;}
});
document.addEventListener('change',event=>{const f=event.target.closest('[data-form="simulation"]');if(f){const v=Object.fromEntries(new FormData(f));state.simulationPatient=v.patientId;state.scenario=v.scenario;state.simulationInterval=Number(v.intervalSeconds);if(event.target.name==='patientId')render();}});
document.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',()=>$('editor').close()));
$('menuToggle').addEventListener('click',()=>{const open=$('sidebar').classList.toggle('open');$('menuToggle').setAttribute('aria-expanded',String(open));});
document.addEventListener('keydown',event=>{if(event.key==='Escape'){$('sidebar').classList.remove('open');$('menuToggle').setAttribute('aria-expanded','false');}});
document.addEventListener('click',event=>{if(!$('sidebar').contains(event.target)&&!$('menuToggle').contains(event.target)){$('sidebar').classList.remove('open');$('menuToggle').setAttribute('aria-expanded','false');}});
window.addEventListener('beforeunload',()=>{stopped=true;socket?.close();});
(async()=>{try{state.me=await api('/me');if(path==='/masuratori')state.metric='all';configureNavigation();await render();if(staff())connect();else{$('liveStatus').textContent='Spațiu personal';$('liveStatus').classList.add('connected');}setInterval(()=>{if(!$('editor').open&&!document.hidden&&!document.activeElement.matches('input,textarea,select'))render(true);},path==='/simulator'?3000:15000);}catch(error){$('content').setAttribute('aria-busy','false');$('content').innerHTML=`<div class="inline-error" role="alert"><h2>Conexiunea nu este disponibilă</h2><p>${h(error.message)}</p><button class="button secondary" data-action="reload-session">Reîncarcă pagina</button></div>`;}})();
