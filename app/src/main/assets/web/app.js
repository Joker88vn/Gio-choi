(function () {
  'use strict';
  var A = window.AmLich;
  var NB = window.Android || null; // cầu nối native
  var $ = function (id) { return document.getElementById(id); };
  var WD_SHORT = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
  var WD_FULL = ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy'];
  var MONTH_WORD = ['Giêng', 'Hai', 'Ba', 'Tư', 'Năm', 'Sáu', 'Bảy', 'Tám', 'Chín', 'Mười', 'Mười Một', 'Chạp'];
  var SOLAR_MONTH_WORD = ['Một', 'Hai', 'Ba', 'Tư', 'Năm', 'Sáu', 'Bảy', 'Tám', 'Chín', 'Mười', 'Mười Một', 'Mười Hai'];
  var EV_COLORS = ['#B3261E', '#B8862B', '#1F7A55', '#2F5C9E', '#7B3F8C', '#5C514A'];

  /* ---------------- Lưu trữ ---------------- */
  function load(key, def) {
    try {
      var s = NB && NB.getData ? NB.getData(key) : localStorage.getItem('al_' + key);
      if (s) return JSON.parse(s);
    } catch (e) {}
    return def;
  }
  function save(key, val) {
    var s = JSON.stringify(val);
    try { if (NB && NB.setData) NB.setData(key, s); else localStorage.setItem('al_' + key, s); } catch (e) {}
  }
  var settings = Object.assign({ theme: 'auto', accent: 'son', weekStart: 1, ongoing: false, ram: true, hol: true, ev: true, time: '07:00' }, load('settings', {}));
  var events = load('events', []);

  /* ---------------- Tiện ích ngày ---------------- */
  function jdOf(d, m, y) { return A.jdFromDate(d, m, y); }
  function fromJd(jd) { var r = A.jdToDate(jd); return { d: r[0], m: r[1], y: r[2] }; }
  function todayJd() { var t = new Date(); return jdOf(t.getDate(), t.getMonth() + 1, t.getFullYear()); }
  function weekday(jd) { return (jd + 1) % 7; } // 0 = CN
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function dim(m, y) { return new Date(y, m, 0).getDate(); }
  function lunarDayText(l) { return l.day === 15 ? 'Rằm' : l.day <= 10 ? 'Mùng ' + l.day : String(l.day); }
  function lunarMonthText(m, leap) { return 'tháng ' + (m === 1 ? 'Giêng' : m === 12 ? 'Chạp' : m) + (leap ? ' nhuận' : ''); }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function toast(msg) {
    var t = $('toast'); t.textContent = msg; t.classList.add('on');
    clearTimeout(toast._t); toast._t = setTimeout(function () { t.classList.remove('on'); }, 2200);
  }
  function leftText(n) { return n === 0 ? 'Hôm nay' : n === 1 ? 'Ngày mai' : n === 2 ? 'Ngày kia' : 'Còn ' + n + ' ngày'; }

  /* ---------------- Ngày lễ ---------------- */
  function holidaysOn(jd) {
    var s = fromJd(jd), l = A.solar2lunar(s.d, s.m, s.y), res = [];
    var hs = HOL_SOLAR[s.d + '/' + s.m];
    if (hs) res.push({ name: hs[0], off: !!hs[1] });
    if (!l.leap) {
      if (l.month === 12 && l.day === l.monthLen) res.push({ name: 'Giao thừa', off: true });
      var hl = HOL_LUNAR[l.day + '/' + l.month];
      if (hl) res.push({ name: hl[0], off: !!hl[1], lunar: true });
    }
    if (weekday(jd) === 0) {
      var nth = Math.floor((s.d - 1) / 7) + 1;
      if (s.m === 5 && nth === 2) res.push({ name: 'Ngày của Mẹ' });
      if (s.m === 6 && nth === 3) res.push({ name: 'Ngày của Cha' });
    }
    return res;
  }

  /* ---------------- Sự kiện cá nhân ---------------- */
  function lunarMonthLen(m, ly, leap) {
    var s = A.lunar2solar(1, m, ly, leap); if (!s) return 30;
    return A.solar2lunar(s.day, s.month, s.year).monthLen;
  }
  function occurrences(ev, from, to) {
    var out = [], y, m, jd, s;
    if (ev.type === 'solar') {
      if (ev.repeat === 'none') { jd = jdOf(ev.day, ev.month, ev.year); if (jd >= from && jd <= to) out.push(jd); return out; }
      var a = fromJd(from), b = fromJd(to);
      for (y = a.y; y <= b.y; y++) {
        var ms = ev.repeat === 'monthly' ? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] : [ev.month];
        for (var i = 0; i < ms.length; i++) {
          m = ms[i]; jd = jdOf(Math.min(ev.day, dim(m, y)), m, y);
          if (jd >= from && jd <= to) out.push(jd);
        }
      }
      return out;
    }
    // âm lịch
    if (ev.repeat === 'none') {
      s = A.lunar2solar(ev.day, ev.month, ev.year, !!ev.leap);
      if (s) { jd = jdOf(s.day, s.month, s.year); if (jd >= from && jd <= to) out.push(jd); }
      return out;
    }
    if (ev.repeat === 'monthly') {
      var f = fromJd(from), lf = A.solar2lunar(f.d, f.m, f.y), start = from - (lf.day - 1), len = lf.monthLen;
      var guard = 0;
      while (start <= to && guard++ < 40) {
        jd = start + Math.min(ev.day, len) - 1;
        if (jd >= from && jd <= to) out.push(jd);
        start += len;
        var nx = fromJd(start); len = A.solar2lunar(nx.d, nx.m, nx.y).monthLen;
      }
      return out;
    }
    var la = A.solar2lunar(fromJd(from).d, fromJd(from).m, fromJd(from).y).year;
    var lb = A.solar2lunar(fromJd(to).d, fromJd(to).m, fromJd(to).y).year;
    for (y = la; y <= lb; y++) {
      var ml = lunarMonthLen(ev.month, y, false);
      s = A.lunar2solar(Math.min(ev.day, ml), ev.month, y, false);
      if (!s) continue;
      jd = jdOf(s.day, s.month, s.year);
      if (jd >= from && jd <= to) out.push(jd);
    }
    return out;
  }
  function eventsOn(jd) { return events.filter(function (e) { return occurrences(e, jd, jd).length > 0; }); }
  function nextOcc(ev, from) { var o = occurrences(ev, from, from + 800); return o.length ? o[0] : null; }
  function evDesc(ev) {
    var t = ev.type === 'lunar' ? 'Âm lịch' : 'Dương lịch';
    if (ev.repeat === 'monthly') return t + ' · ngày ' + ev.day + ' hằng tháng';
    var d = ev.type === 'lunar' ? ev.day + '/' + ev.month + (ev.leap ? ' nhuận' : '') : ev.day + '/' + ev.month;
    if (ev.repeat === 'none') return t + ' · ' + d + '/' + ev.year;
    return t + ' · ' + d + ' hằng năm';
  }

  /* ---------------- Theme ---------------- */
  function applyTheme() {
    var dark = settings.theme === 'dark' || (settings.theme === 'auto' && (NB && NB.isSystemDark ? NB.isSystemDark() : window.matchMedia('(prefers-color-scheme: dark)').matches));
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.documentElement.dataset.accent = settings.accent;
    try { if (NB && NB.setBars) NB.setBars(dark); } catch (e) {}
  }

  /* ---------------- State ---------------- */
  var cur = todayJd();     // ngày đang xem
  var monthView = { m: fromJd(cur).m, y: fromJd(cur).y };
  var monthSel = cur;
  var tab = 'day';

  /* ---------------- Moon ---------------- */
  function moonSVG(lday) {
    var ph = ((lday - 1) / 29.53) * 2 * Math.PI, r = 20, cx = 23, cy = 23;
    var rx = Math.abs(Math.cos(ph)) * r, d;
    var top = cx + ' ' + (cy - r), bot = cx + ' ' + (cy + r);
    if (ph < Math.PI) { // trăng tròn dần: sáng bên phải
      d = 'M' + top + ' A' + r + ' ' + r + ' 0 0 1 ' + bot + ' A' + rx + ' ' + r + ' 0 0 ' + (ph < Math.PI / 2 ? 0 : 1) + ' ' + top;
    } else {
      d = 'M' + top + ' A' + r + ' ' + r + ' 0 0 0 ' + bot + ' A' + rx + ' ' + r + ' 0 0 ' + (ph < 1.5 * Math.PI ? 0 : 1) + ' ' + top;
    }
    return '<svg viewBox="0 0 46 46"><circle cx="23" cy="23" r="20" fill="rgba(0,0,0,.22)" stroke="rgba(255,235,200,.45)" stroke-width="1"/>' +
      '<path d="' + d + '" fill="#FFE7B0"/></svg>';
  }

  /* ---------------- Dial giờ hoàng đạo ---------------- */
  function dialSVG(gio, nowChi) {
    var h = '', cx = 100, cy = 100, R = 96, r = 58;
    function pt(rad, ang) { var a = (ang - 90) * Math.PI / 180; return [cx + rad * Math.cos(a), cy + rad * Math.sin(a)]; }
    for (var i = 0; i < 12; i++) {
      var a0 = i * 30 - 15, a1 = i * 30 + 15, p1 = pt(R, a0), p2 = pt(R, a1), p3 = pt(r, a1), p4 = pt(r, a0);
      var cls = 'seg ' + (gio[i].good ? 'g' : 'b') + (i === nowChi ? ' now' : '');
      h += '<path class="' + cls + '" d="M' + p1[0].toFixed(2) + ' ' + p1[1].toFixed(2) + ' A' + R + ' ' + R + ' 0 0 1 ' + p2[0].toFixed(2) + ' ' + p2[1].toFixed(2) +
        ' L' + p3[0].toFixed(2) + ' ' + p3[1].toFixed(2) + ' A' + r + ' ' + r + ' 0 0 0 ' + p4[0].toFixed(2) + ' ' + p4[1].toFixed(2) + 'Z"/>';
      var tp = pt(77, i * 30);
      h += '<text x="' + tp[0].toFixed(1) + '" y="' + tp[1].toFixed(1) + '" class="' + (gio[i].good || i === nowChi ? 'g' : '') + '">' + gio[i].name + '</text>';
    }
    if (nowChi >= 0) {
      h += '<text x="100" y="94" class="center-t">' + gio[nowChi].name + '</text><text x="100" y="116" class="center-s">' + (gio[nowChi].good ? 'GIỜ TỐT' : 'GIỜ HIỆN TẠI') + '</text>';
    } else {
      h += '<text x="100" y="94" class="center-t">6</text><text x="100" y="116" class="center-s">GIỜ TỐT</text>';
    }
    return h;
  }

  /* ---------------- Render: Ngày ---------------- */
  function renderDay(anim) {
    var s = fromJd(cur), I = A.dayInfo(s.d, s.m, s.y), l = I.lunar, tj = todayJd();
    $('d-title').textContent = 'Tháng ' + s.m + ', ' + s.y;
    $('d-picker').value = s.y + '-' + pad(s.m) + '-' + pad(s.d);
    $('h-weekday').textContent = WD_FULL[I.solar.weekday].toUpperCase() + (cur === tj ? ' · HÔM NAY' : '');
    var badge = $('h-badge');
    badge.textContent = I.hoangDao ? 'Hoàng đạo' : 'Hắc đạo';
    badge.className = 'hero-badge' + (I.hoangDao ? ' hd' : '');
    $('h-day').textContent = s.d;
    $('h-month').textContent = 'Tháng ' + SOLAR_MONTH_WORD[s.m - 1] + ' · ' + s.y;
    var hol = holidaysOn(cur);
    $('h-holiday').innerHTML = hol.map(function (h) { return '<span class="' + (h.off ? 'off' : '') + '">' + esc(h.name) + '</span>'; }).join(' ');
    $('h-moon').innerHTML = moonSVG(l.day);
    $('h-lday').textContent = lunarDayText(l);
    $('h-lmonth').textContent = lunarMonthText(l.month, l.leap) + (l.monthLen === 29 ? ' (thiếu)' : ' (đủ)');
    $('h-lyear').textContent = 'Năm ' + I.canChi.year + ' · Ngày ' + I.canChi.day;

    // week strip
    var wd = weekday(cur), start = cur - ((wd - settings.weekStart + 7) % 7), ws = '';
    for (var i = 0; i < 7; i++) {
      var j = start + i, sd = fromJd(j), ld = A.solar2lunar(sd.d, sd.m, sd.y), w = weekday(j);
      var mark = holidaysOn(j).length || eventsOn(j).length;
      ws += '<button class="ws-d' + (j === cur ? ' sel' : '') + (j === tj ? ' today' : '') + (w === 0 ? ' we' : '') + '" data-jd="' + j + '">' +
        (mark ? '<i class="mk"></i>' : '') + '<span class="w">' + WD_SHORT[w] + '</span><span class="s">' + sd.d + '</span><span class="l">' +
        (ld.day === 1 ? '1/' + ld.month : ld.day) + '</span></button>';
    }
    $('week-strip').innerHTML = ws;

    // events
    var evs = eventsOn(cur), ce = $('c-events');
    if (evs.length) {
      ce.hidden = false;
      ce.innerHTML = '<div class="card-h"><span class="dot blue"></span>Sự kiện của bạn</div>' + evs.map(function (e) {
        return '<div class="ev-chip" data-id="' + e.id + '"><i style="background:' + (e.color || EV_COLORS[0]) + '"></i><b>' + esc(e.title) + '</b><small>' + evDesc(e) + '</small></div>';
      }).join('');
    } else ce.hidden = true;

    $('cc-day').textContent = I.canChi.day;
    $('cc-month').textContent = I.canChi.month;
    $('cc-year').textContent = I.canChi.year;
    $('kv-napam').textContent = I.napAmNgay;
    $('kv-tiet').innerHTML = esc(I.tietKhi) + (I.tietKhiStart ? '<span class="tag good">Bắt đầu</span>' : '');
    $('kv-truc').textContent = 'Trực ' + I.truc;
    $('kv-sao').innerHTML = 'Sao ' + esc(I.sao) + '<span class="tag ' + (I.saoTot ? 'good">Tốt' : 'bad">Xấu') + '</span>';
    $('kv-than').innerHTML = esc(I.than) + '<span class="tag ' + (I.hoangDao ? 'good">Hoàng đạo' : 'bad">Hắc đạo') + '</span>';

    var nowChi = -1;
    if (cur === tj) { var hr = new Date().getHours(); nowChi = Math.floor((hr + 1) / 2) % 12; }
    // giờ Tý 23h thuộc ngày hôm sau theo can chi, nhưng hiển thị theo ngày dương cho dễ hiểu
    $('dial').innerHTML = dialSVG(I.gio, nowChi);
    $('gio-list').innerHTML = I.gio.filter(function (g) { return g.good; }).map(function (g) {
      return '<li' + (g.chi === nowChi ? ' class="now"' : '') + '><b>' + g.name + '</b><span>' + g.range + '</span></li>';
    }).join('');
    $('gio-now').textContent = nowChi >= 0 ? (I.gio[nowChi].good ? 'Đang trong giờ tốt' : 'Giờ ' + I.gio[nowChi].name + ' · ' + I.gio[nowChi].than) : '';

    $('yn-good').textContent = I.trucViec[0];
    $('yn-bad').textContent = I.trucViec[1];
    $('yn-warn').innerHTML = I.warnings.map(function (w) { return '<span>' + w + '</span>'; }).join('');

    $('x-hy').textContent = I.hyThan;
    $('x-tai').textContent = I.taiThan;
    $('x-xung').textContent = I.xung;
    $('x-hop').textContent = I.lucHop + ' · ' + I.tamHop.join(', ');

    var qi = (s.y * 372 + s.m * 31 + s.d) % QUOTES.length;
    $('quote').textContent = QUOTES[qi];

    // Đếm ngược Tết (tính từ hôm nay thật)
    var t = fromJd(tj), lt = A.solar2lunar(t.d, t.m, t.y), tet = A.lunar2solar(1, 1, lt.year + 1, false);
    var tc = $('tet-count');
    if (lt.month === 1 && lt.day <= 3 && !lt.leap) tc.innerHTML = '🎉 <b>Chúc mừng năm mới ' + A.yearCanChi(lt.year) + '!</b>';
    else if (tet) {
      var n = jdOf(tet.day, tet.month, tet.year) - tj;
      tc.innerHTML = 'Còn <b>' + n + ' ngày</b> nữa đến Tết ' + A.yearCanChi(lt.year + 1) + ' (' + tet.day + '/' + tet.month + '/' + tet.year + ')';
    }

    if (anim) {
      var hero = $('hero');
      hero.style.transition = 'none';
      hero.className = 'hero ' + (anim > 0 ? 'slide-r' : 'slide-l');
      void hero.offsetWidth;
      hero.style.transition = '';
      hero.className = 'hero';
    }
  }

  function goDay(jd, anim) { cur = jd; renderDay(anim); }

  /* ---------------- Render: Tháng ---------------- */
  function renderMonth(anim) {
    var m = monthView.m, y = monthView.y, tj = todayJd();
    $('m-title').textContent = 'Tháng ' + m + ', ' + y;
    $('m-picker').value = y + '-' + pad(m);
    var l1 = A.solar2lunar(1, m, y), l2 = A.solar2lunar(dim(m, y), m, y);
    $('m-sub').textContent = 'Âm lịch: ' + lunarMonthText(l1.month, l1.leap).replace('tháng ', 'tháng ') + (l1.month !== l2.month || l1.leap !== l2.leap ? ' – ' + lunarMonthText(l2.month, l2.leap).replace('tháng ', '') : '') + ' · ' + A.yearCanChi(l2.year);

    var wdh = '';
    for (var i = 0; i < 7; i++) { var w = (settings.weekStart + i) % 7; wdh += '<div class="' + (w === 0 ? 'we' : '') + '">' + WD_SHORT[w] + '</div>'; }
    $('wd-row').innerHTML = wdh;

    var first = jdOf(1, m, y), lead = (weekday(first) - settings.weekStart + 7) % 7, start = first - lead;
    var total = Math.ceil((lead + dim(m, y)) / 7) * 7, html = '';
    for (var k = 0; k < total; k++) {
      var jd = start + k, s = fromJd(jd), I = A.dayInfo(s.d, s.m, s.y), l = I.lunar;
      var hol = holidaysOn(jd), evs = eventsOn(jd);
      var cls = 'mc' + (s.m !== m ? ' out' : '') + (weekday(jd) === 0 ? ' we' : '') + (jd === tj ? ' today' : '') + (jd === monthSel ? ' sel' : '') + (I.hoangDao ? ' hd' : '');
      var lt = l.day === 1 ? '<span class="l first">' + l.day + '/' + l.month + (l.leap ? 'N' : '') + '</span>' : l.day === 15 ? '<span class="l ram">' + l.day + '</span>' : '<span class="l">' + l.day + '</span>';
      html += '<button class="' + cls + '" data-jd="' + jd + '"><span class="s">' + s.d + '</span>' + lt +
        ((hol.length || evs.length) ? '<span class="mk">' + (hol.length ? '<i class="h"></i>' : '') + (evs.length ? '<i class="e"></i>' : '') + '</span>' : '') + '</button>';
    }
    var g = $('m-grid');
    g.innerHTML = html;
    if (anim) { g.className = 'm-grid ' + (anim > 0 ? 'slide-r' : 'slide-l'); void g.offsetWidth; g.className = 'm-grid'; }
    renderMonthDetail();

    // danh sách trong tháng
    var lst = [], a = jdOf(1, m, y), b = jdOf(dim(m, y), m, y);
    for (var j = a; j <= b; j++) {
      holidaysOn(j).forEach(function (h) { lst.push({ jd: j, name: h.name, sub: h.lunar ? 'Âm lịch' : 'Ngày lễ', color: 'var(--acc)' }); });
      var sd = fromJd(j), ll = A.solar2lunar(sd.d, sd.m, sd.y);
      if ((ll.day === 1 && !(ll.month === 1)) || ll.day === 15 && !HOL_LUNAR[ll.day + '/' + ll.month]) lst.push({ jd: j, name: (ll.day === 1 ? 'Mùng 1 ' : 'Rằm ') + lunarMonthText(ll.month, ll.leap), sub: 'Âm lịch', color: 'var(--gold)' });
    }
    events.forEach(function (e) { occurrences(e, a, b).forEach(function (j) { lst.push({ jd: j, name: e.title, sub: evDesc(e), color: e.color || EV_COLORS[0] }); }); });
    lst.sort(function (p, q) { return p.jd - q.jd; });
    $('m-events').innerHTML = lst.length ? lst.map(evRow).join('') : '<li class="ev-main"><small>Không có ngày lễ hay sự kiện.</small></li>';
  }
  function evRow(it) {
    var s = fromJd(it.jd), l = A.solar2lunar(s.d, s.m, s.y), n = it.jd - todayJd();
    return '<li data-jd="' + it.jd + '"' + (it.id ? ' data-id="' + it.id + '"' : '') + '><span class="ev-bar" style="background:' + it.color + '"></span><div class="ev-date"><b>' + s.d + '</b><small>' + WD_SHORT[weekday(it.jd)] + ' · ' + l.day + '/' + l.month + '</small></div>' +
      '<div class="ev-main"><b>' + esc(it.name) + '</b><small>' + esc(it.sub) + '</small></div>' +
      (n >= 0 ? '<span class="ev-left' + (n > 7 ? ' far' : '') + '">' + leftText(n) + '</span>' : '') + '</li>';
  }
  function renderMonthDetail() {
    var s = fromJd(monthSel), I = A.dayInfo(s.d, s.m, s.y), hol = holidaysOn(monthSel).map(function (h) { return h.name; });
    var evs = eventsOn(monthSel).map(function (e) { return e.title; });
    $('m-detail').innerHTML = '<div class="big">' + s.d + '</div><div class="t"><b>' + WD_FULL[I.solar.weekday] + ', ' + s.d + '/' + s.m + '/' + s.y + '</b>' +
      '<p>' + lunarDayText(I.lunar) + ' ' + lunarMonthText(I.lunar.month, I.lunar.leap) + ' · Ngày ' + I.canChi.day + ' · ' + (I.hoangDao ? 'Hoàng đạo' : 'Hắc đạo') +
      (hol.concat(evs).length ? '<br><b style="display:inline;color:var(--acc)">' + esc(hol.concat(evs).join(' · ')) + '</b>' : '') + '</p></div>' +
      '<button class="go" data-go="' + monthSel + '">Chi tiết</button>';
  }

  /* ---------------- Render: Sự kiện ---------------- */
  var evFilter = 'upcoming';
  function renderEvents() {
    var tj = todayJd(), items = [];
    if (evFilter === 'mine') {
      events.forEach(function (e) { var n = nextOcc(e, tj); items.push({ jd: n, name: e.title, sub: evDesc(e) + (e.remind ? ' · nhắc trước ' + e.remind + ' ngày' : ''), color: e.color || EV_COLORS[0], id: e.id }); });
      items.sort(function (a, b) { return (a.jd || 1e9) - (b.jd || 1e9); });
    } else {
      var to = tj + (evFilter === 'holiday' ? 366 : 90);
      if (evFilter === 'upcoming') events.forEach(function (e) { occurrences(e, tj, tj + 366).forEach(function (j) { items.push({ jd: j, name: e.title, sub: evDesc(e), color: e.color || EV_COLORS[0], id: e.id }); }); });
      for (var j = tj; j <= to; j++) holidaysOn(j).forEach(function (h) { items.push({ jd: j, name: h.name, sub: (h.lunar ? 'Lễ âm lịch' : 'Lễ dương lịch') + (h.off ? ' · Nghỉ lễ' : ''), color: h.off ? 'var(--acc)' : 'var(--gold)' }); });
      items.sort(function (a, b) { return a.jd - b.jd; });
    }
    var html = '', lastM = '';
    items.forEach(function (it) {
      if (it.jd != null && evFilter !== 'mine') {
        var s = fromJd(it.jd), key = s.m + '/' + s.y;
        if (key !== lastM) { html += '<li class="m-sep" style="border:0;padding:14px 2px 2px;font-size:12px;font-weight:700;letter-spacing:.8px;color:var(--muted);text-transform:uppercase">Tháng ' + s.m + ' / ' + s.y + '</li>'; lastM = key; }
      }
      html += it.jd != null ? evRow(it) : '<li data-id="' + it.id + '"><span class="ev-bar" style="background:' + it.color + '"></span><div class="ev-main"><b>' + esc(it.name) + '</b><small>' + esc(it.sub) + '</small></div></li>';
    });
    $('ev-list').innerHTML = html;
    $('ev-empty').hidden = !(evFilter === 'mine' && events.length === 0);
  }

  /* ---------------- Sheet: thêm/sửa sự kiện ---------------- */
  function openSheet(html) { $('sheet-body').innerHTML = html; $('sheet').classList.add('on'); $('scrim').classList.add('on'); }
  function closeSheet() { $('sheet').classList.remove('on'); $('scrim').classList.remove('on'); }
  function opts(a, b, sel, fmt) { var h = ''; for (var i = a; i <= b; i++) h += '<option value="' + i + '"' + (i === sel ? ' selected' : '') + '>' + (fmt ? fmt(i) : i) + '</option>'; return h; }

  function openEventForm(ev, presetJd) {
    var isNew = !ev, base = presetJd || cur, s = fromJd(base), l = A.solar2lunar(s.d, s.m, s.y);
    ev = ev || { id: 'e' + Date.now(), title: '', type: 'lunar', day: l.day, month: l.month, year: l.year, repeat: 'yearly', remind: 1, color: EV_COLORS[0] };
    var f = JSON.parse(JSON.stringify(ev));
    openSheet('<div class="sh-title">' + (isNew ? 'Thêm sự kiện' : 'Sửa sự kiện') + '</div><div class="form">' +
      '<div class="field"><label>Tên sự kiện</label><input type="text" id="f-title" placeholder="VD: Giỗ ông nội, Sinh nhật mẹ…" value="' + esc(f.title) + '" style="user-select:text;-webkit-user-select:text"></div>' +
      '<div><div class="field"><label>Theo lịch</label></div><div class="seg small" id="f-type"><button data-v="lunar">Âm lịch</button><button data-v="solar">Dương lịch</button></div></div>' +
      '<div class="row3"><div class="field"><label>Ngày</label><select id="f-d"></select></div><div class="field"><label>Tháng</label><select id="f-m"></select></div><div class="field"><label>Năm</label><input id="f-y" type="number" inputmode="numeric" value="' + f.year + '"></div></div>' +
      '<div><div class="field"><label>Lặp lại</label></div><div class="seg small" id="f-rep"><button data-v="yearly">Hằng năm</button><button data-v="monthly">Hằng tháng</button><button data-v="none">Một lần</button></div></div>' +
      '<div class="row2"><div class="field"><label>Nhắc trước</label><select id="f-rem">' +
      [[0, 'Đúng ngày'], [1, '1 ngày'], [2, '2 ngày'], [3, '3 ngày'], [7, '1 tuần']].map(function (o) { return '<option value="' + o[0] + '"' + (o[0] === f.remind ? ' selected' : '') + '>' + o[1] + '</option>'; }).join('') +
      '</select></div><div class="field"><label>Màu</label><div class="colors" id="f-col" style="height:46px;align-items:center">' +
      EV_COLORS.slice(0, 5).map(function (c) { return '<button data-v="' + c + '" style="--c:' + c + '"></button>'; }).join('') + '</div></div></div>' +
      '<div class="sh-preview" id="f-prev"></div>' +
      '<div class="sh-actions">' + (isNew ? '' : '<button class="btn danger" id="f-del">Xoá</button>') + '<button class="btn ghost" id="f-cancel">Huỷ</button><button class="btn pri" id="f-save">Lưu</button></div></div>');

    function segSet(id, v) { [].forEach.call($(id).children, function (b) { b.classList.toggle('on', b.dataset.v === v); }); }
    function fillSel() {
      $('f-d').innerHTML = opts(1, f.type === 'lunar' ? 30 : 31, f.day);
      $('f-m').innerHTML = opts(1, 12, f.month, f.type === 'lunar' ? function (i) { return i === 1 ? 'Giêng' : i === 12 ? 'Chạp' : i; } : null);
      $('f-m').parentNode.style.visibility = f.repeat === 'monthly' ? 'hidden' : '';
      $('f-y').parentNode.style.visibility = f.repeat === 'none' ? '' : 'hidden';
    }
    function preview() {
      var n = nextOcc(f, todayJd()), p = $('f-prev');
      if (n == null) { p.innerHTML = 'Ngày này đã qua hoặc không tồn tại.'; return; }
      var sd = fromJd(n), ld = A.solar2lunar(sd.d, sd.m, sd.y);
      p.innerHTML = 'Lần tới: <b>' + WD_FULL[weekday(n)] + ', ' + sd.d + '/' + sd.m + '/' + sd.y + '</b> (' + lunarDayText(ld) + ' ' + lunarMonthText(ld.month, ld.leap) + ') · ' + leftText(n - todayJd()).toLowerCase();
    }
    segSet('f-type', f.type); segSet('f-rep', f.repeat);
    [].forEach.call($('f-col').children, function (b) { b.classList.toggle('on', b.dataset.v === f.color); });
    fillSel(); preview();
    $('f-type').onclick = function (e) {
      var v = e.target.dataset.v; if (!v || v === f.type) return;
      // đổi kiểu: quy đổi ngày tương ứng
      if (v === 'solar') { var sl = f.repeat === 'none' ? A.lunar2solar(f.day, f.month, f.year, false) : null; if (sl) { f.day = sl.day; f.month = sl.month; f.year = sl.year; } }
      else if (f.repeat === 'none') { var ls = A.solar2lunar(f.day, f.month, f.year); f.day = ls.day; f.month = ls.month; f.year = ls.year; }
      f.type = v; segSet('f-type', v); fillSel(); preview();
    };
    $('f-rep').onclick = function (e) { var v = e.target.dataset.v; if (!v) return; f.repeat = v; segSet('f-rep', v); fillSel(); preview(); };
    $('f-col').onclick = function (e) { var v = e.target.dataset.v; if (!v) return; f.color = v; [].forEach.call($('f-col').children, function (b) { b.classList.toggle('on', b.dataset.v === v); }); };
    $('f-d').onchange = function () { f.day = +this.value; preview(); };
    $('f-m').onchange = function () { f.month = +this.value; preview(); };
    $('f-y').oninput = function () { f.year = +this.value || f.year; preview(); };
    $('f-rem').onchange = function () { f.remind = +this.value; };
    $('f-cancel').onclick = closeSheet;
    if ($('f-del')) $('f-del').onclick = function () {
      events = events.filter(function (e) { return e.id !== f.id; }); save('events', events); closeSheet(); refreshAll(); toast('Đã xoá sự kiện');
    };
    $('f-save').onclick = function () {
      f.title = $('f-title').value.trim();
      if (!f.title) { $('f-title').focus(); toast('Nhập tên sự kiện'); return; }
      var i = events.findIndex(function (e) { return e.id === f.id; });
      if (i >= 0) events[i] = f; else events.push(f);
      save('events', events); closeSheet(); refreshAll();
      toast(isNew ? 'Đã thêm sự kiện' : 'Đã lưu');
      if (settings.ev) askNotif();
    };
  }

  /* ---------------- Tiện ích ---------------- */
  var convMode = 's2l';
  function initTools() {
    var t = fromJd(todayJd());
    function setDefault() {
      if (convMode === 's2l') { $('cv-d').innerHTML = opts(1, 31, t.d); $('cv-m').innerHTML = opts(1, 12, t.m); $('cv-y').value = t.y; }
      else { var l = A.solar2lunar(t.d, t.m, t.y); $('cv-d').innerHTML = opts(1, 30, l.day); $('cv-m').innerHTML = opts(1, 12, l.month); $('cv-y').value = l.year; }
      convert();
    }
    function convert() {
      var d = +$('cv-d').value, m = +$('cv-m').value, y = +$('cv-y').value, out = $('cv-out');
      if (!y || y < 1800 || y > 2199) { out.innerHTML = ''; return; }
      if (convMode === 's2l') {
        $('cv-leap-wrap').hidden = true;
        if (d > dim(m, y)) { out.innerHTML = '<div class="r2">Tháng ' + m + '/' + y + ' chỉ có ' + dim(m, y) + ' ngày.</div>'; return; }
        var I = A.dayInfo(d, m, y), l = I.lunar;
        out.innerHTML = '<div class="r1">' + lunarDayText(l) + ' ' + lunarMonthText(l.month, l.leap) + '</div><div class="r2">Năm ' + I.canChi.year + ' · Tháng ' + I.canChi.month + ' · Ngày ' + I.canChi.day + '<br>' +
          WD_FULL[I.solar.weekday] + ' · ' + (I.hoangDao ? 'Ngày hoàng đạo' : 'Ngày hắc đạo') + ' · Trực ' + I.truc + '</div><a class="lnk" data-go="' + jdOf(d, m, y) + '">Xem chi tiết ngày →</a>';
      } else {
        var lm = A.leapMonthOfYear(y);
        $('cv-leap-wrap').hidden = lm !== m;
        if (lm !== m) $('cv-leap').checked = false;
        var s = A.lunar2solar(d, m, y, $('cv-leap').checked);
        if (!s) { out.innerHTML = '<div class="r2">Ngày không hợp lệ.</div>'; return; }
        var chk = A.solar2lunar(s.day, s.month, s.year);
        if (chk.day !== d) { out.innerHTML = '<div class="r2">' + lunarMonthText(m, $('cv-leap').checked) + ' năm ' + A.yearCanChi(y) + ' chỉ có 29 ngày (tháng thiếu).</div>'; return; }
        var jd = jdOf(s.day, s.month, s.year);
        out.innerHTML = '<div class="r1">' + WD_FULL[weekday(jd)] + ', ' + s.day + '/' + s.month + '/' + s.year + '</div><div class="r2">Ngày ' + A.dayInfo(s.day, s.month, s.year).canChi.day + ' · ' + leftText(jd - todayJd()).replace('Còn', 'còn') + (jd < todayJd() ? ' (đã qua ' + (todayJd() - jd) + ' ngày)' : '') + '</div><a class="lnk" data-go="' + jd + '">Xem chi tiết ngày →</a>';
      }
    }
    $('conv-seg').onclick = function (e) {
      var v = e.target.dataset.m; if (!v) return; convMode = v;
      [].forEach.call(this.children, function (b) { b.classList.toggle('on', b.dataset.m === v); }); setDefault();
    };
    ['cv-d', 'cv-m', 'cv-leap'].forEach(function (id) { $(id).onchange = convert; });
    $('cv-y').oninput = convert;
    setDefault();

    // Tuổi
    $('age-y').value = 1990;
    function age() {
      var y = +$('age-y').value, o = $('age-out');
      if (!y || y < 1900 || y > 2100) { o.innerHTML = ''; return; }
      var chi = (y + 8) % 12, nap = A.yearNapAm(y), hanh = nap.split(' ').pop();
      var CHI = A.CHI, curY = A.solar2lunar(t.d, t.m, t.y).year;
      o.innerHTML = '<div><label>Năm</label><b>' + A.yearCanChi(y) + '</b></div><div><label>Con giáp</label><b>' + A.CON_GIAP[chi] + '</b></div>' +
        '<div><label>Mệnh</label><b>' + hanh + '</b></div><div><label>Tuổi mụ (' + curY + ')</label><b>' + (curY - y + 1) + '</b></div>' +
        '<div class="wide"><label>Ngũ hành nạp âm</label><b>' + nap + '</b></div>' +
        '<div class="wide"><label>Tam hợp · Tứ hành xung</label><b>' + [CHI[chi], CHI[(chi + 4) % 12], CHI[(chi + 8) % 12]].join(' – ') + ' · ' + [CHI[(chi + 3) % 12], CHI[(chi + 6) % 12], CHI[(chi + 9) % 12]].join(', ') + '</b></div>';
    }
    $('age-y').oninput = age; age();

    // Đếm ngày
    $('dd-a').value = t.y + '-' + pad(t.m) + '-' + pad(t.d);
    var tt = fromJd(todayJd() + 100); $('dd-b').value = tt.y + '-' + pad(tt.m) + '-' + pad(tt.d);
    function dd() {
      var a = $('dd-a').value.split('-').map(Number), b = $('dd-b').value.split('-').map(Number), o = $('dd-out');
      if (a.length < 3 || b.length < 3 || !a[0] || !b[0]) { o.innerHTML = ''; return; }
      var n = jdOf(b[2], b[1], b[0]) - jdOf(a[2], a[1], a[0]), an = Math.abs(n);
      o.innerHTML = '<div class="r1">' + an + ' ngày</div><div class="r2">≈ ' + Math.floor(an / 7) + ' tuần ' + (an % 7) + ' ngày' + (an >= 30 ? ' · khoảng ' + (an / 30.44).toFixed(1) + ' tháng' : '') + (n < 0 ? ' (ngày sau trước ngày đầu)' : '') + '</div>';
    }
    $('dd-a').onchange = dd; $('dd-b').onchange = dd; dd();
  }

  /* ---------------- Cài đặt ---------------- */
  var WIDGETS = [
    { t: 'day', n: 'Lịch ngày', s: '2 × 2', pv: '<div class="pv pv-day"><i>THỨ NĂM</i><b>15</b><i>6/9 ÂL</i></div>' },
    { t: 'detail', n: 'Chi tiết', s: '4 × 2', pv: '<div class="pv pv-det"><div class="l">15</div><div class="r"><i></i><i></i><i style="width:80%"></i><i style="width:60%"></i></div></div>' },
    { t: 'month', n: 'Lịch tháng', s: '4 × 4', pv: '<div class="pv pv-mon">' + Array(35).join('<i></i>') + '<i class="t"></i></div>' },
    { t: 'tet', n: 'Đếm ngược Tết', s: '2 × 1', pv: '<div class="pv pv-tet"><b>125</b><i>ngày nữa đến Tết</i></div>' },
    { t: 'line', n: 'Dòng ngày (trong suốt)', s: '4 × 1', pv: '<div class="pv pv-line">Thứ Năm 15/10 · 6/9 Bính Ngọ</div>' }
  ];
  function renderSettings() {
    function segOn(id, v) { [].forEach.call($(id).children, function (b) { b.classList.toggle('on', b.dataset.v === String(v)); }); }
    segOn('set-theme', settings.theme); segOn('set-week', settings.weekStart); segOn('set-accent', settings.accent);
    $('set-ongoing').checked = settings.ongoing; $('set-ram').checked = settings.ram; $('set-hol').checked = settings.hol; $('set-ev').checked = settings.ev;
    $('set-time').value = settings.time;
    $('widget-list').innerHTML = WIDGETS.map(function (w) {
      return '<button class="wg" data-w="' + w.t + '">' + w.pv + '<b>' + w.n + '</b><small>' + w.s + '</small><span class="add">+ Thêm ra màn hình</span></button>';
    }).join('');
  }
  function saveSettings() { save('settings', settings); }
  function askNotif() { try { if (NB && NB.requestNotif) NB.requestNotif(); } catch (e) {} }
  function initSettings() {
    function seg(id, key, num) {
      $(id).onclick = function (e) {
        var v = e.target.dataset.v; if (v == null) return;
        settings[key] = num ? +v : v; saveSettings(); applyTheme(); renderSettings();
        if (key === 'weekStart') { renderDay(); renderMonth(); }
      };
    }
    seg('set-theme', 'theme'); seg('set-accent', 'accent'); seg('set-week', 'weekStart', true);
    [['set-ongoing', 'ongoing'], ['set-ram', 'ram'], ['set-hol', 'hol'], ['set-ev', 'ev']].forEach(function (p) {
      $(p[0]).onchange = function () { settings[p[1]] = this.checked; saveSettings(); if (this.checked) askNotif(); };
    });
    $('set-time').onchange = function () { if (this.value) { settings.time = this.value; saveSettings(); toast('Sẽ nhắc lúc ' + this.value + ' mỗi ngày'); } };
    $('widget-list').onclick = function (e) {
      var b = e.target.closest('.wg'); if (!b) return;
      var ok = false;
      try { ok = NB && NB.pinWidget ? NB.pinWidget(b.dataset.w) : false; } catch (er) {}
      clearTimeout(pinTimer);
      if (!ok) { widgetGuide(); return; }
      toast('Đang mở hộp thoại thêm widget…');
      // Nếu launcher không hiện hộp thoại (MIUI, ColorOS…) thì hướng dẫn thêm thủ công
      pinTimer = setTimeout(function () { if (!document.hidden) widgetGuide(true); }, 2500);
    };
  }
  var pinTimer = 0;
  function widgetGuide(silent) {
    openSheet('<div class="sh-title">Thêm widget ra màn hình</div>' +
      (silent ? '<p class="sh-preview" style="margin-bottom:12px">Máy của anh chưa hiện hộp thoại thêm tự động. Thêm thủ công rất nhanh:</p>' : '') +
      '<ol class="guide">' +
      '<li><b>Về màn hình chính</b>, nhấn giữ vào chỗ trống khoảng 1 giây.</li>' +
      '<li>Chọn <b>Widget</b> (hoặc <b>Tiện ích</b>).</li>' +
      '<li>Gõ tìm <b>“Âm Lịch”</b> hoặc kéo xuống tìm biểu tượng lịch đỏ.</li>' +
      '<li>Chọn mẫu muốn dùng (Ngày, Chi tiết, Tháng, Đếm ngược Tết, Dòng ngày) rồi <b>kéo thả</b> ra màn hình.</li>' +
      '</ol>' +
      '<p class="hint">Máy Xiaomi/Redmi: nếu không thấy hộp thoại, vào Cài đặt → Ứng dụng → Âm Lịch → Quyền khác → bật <b>“Lối tắt trên màn hình chính”</b>.</p>' +
      '<div class="sh-actions"><button class="btn pri" id="g-ok">Đã hiểu</button></div>');
    $('g-ok').onclick = closeSheet;
  }

  /* ---------------- Điều hướng ---------------- */
  function switchTab(v) {
    tab = v;
    ['day', 'month', 'events', 'tools', 'settings'].forEach(function (k) { $('v-' + k).classList.toggle('active', k === v); });
    [].forEach.call($('tabbar').children, function (b) { b.classList.toggle('on', b.dataset.v === v); });
    if (v === 'month') renderMonth();
    if (v === 'events') renderEvents();
    if (v === 'settings') renderSettings();
  }
  function openDay(jd) { goDay(jd); switchTab('day'); $('day-scroll').scrollTop = 0; }
  function refreshAll() { renderDay(); if (tab === 'month') renderMonth(); if (tab === 'events') renderEvents(); }

  function swipe(el, cb) {
    var x0 = 0, y0 = 0, t0 = 0;
    el.addEventListener('touchstart', function (e) { x0 = e.touches[0].clientX; y0 = e.touches[0].clientY; t0 = Date.now(); }, { passive: true });
    el.addEventListener('touchend', function (e) {
      var dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0;
      if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.4 && Date.now() - t0 < 700) cb(dx < 0 ? 1 : -1);
    }, { passive: true });
  }

  function handleHash(h) {
    h = (h || location.hash || '').replace(/^#/, '');
    if (!h) return;
    var p = {}; h.split('&').forEach(function (kv) { var a = kv.split('='); p[a[0]] = decodeURIComponent(a[1] || ''); });
    if (p.d) { var a = p.d.split('-').map(Number); if (a.length === 3) { cur = jdOf(a[2], a[1], a[0]); monthSel = cur; monthView = { m: a[1], y: a[0] }; } }
    renderDay();
    if (p.t) switchTab(p.t);
  }

  function init() {
    applyTheme();
    // Ngày
    $('h-prev').onclick = function () { goDay(cur - 1, -1); };
    $('h-next').onclick = function () { goDay(cur + 1, 1); };
    $('btn-today').onclick = function () { goDay(todayJd(), cur > todayJd() ? -1 : 1); $('day-scroll').scrollTop = 0; };
    $('d-picker').onchange = function () { var a = this.value.split('-').map(Number); if (a.length === 3 && a[0]) goDay(jdOf(a[2], a[1], a[0]), 1); };
    swipe($('hero-wrap'), function (d) { goDay(cur + d, d); });
    swipe($('week-strip'), function (d) { goDay(cur + d * 7, d); });
    $('week-strip').onclick = function (e) { var b = e.target.closest('.ws-d'); if (b) goDay(+b.dataset.jd); };
    $('c-events').onclick = function (e) { var c = e.target.closest('[data-id]'); if (c) openEventForm(events.find(function (x) { return x.id === c.dataset.id; })); };
    $('hero').addEventListener('dblclick', function () { goDay(todayJd()); });

    // Tháng
    function mStep(d) { var m = monthView.m + d, y = monthView.y; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } monthView = { m: m, y: y }; renderMonth(d); }
    $('m-prev').onclick = function () { mStep(-1); };
    $('m-next').onclick = function () { mStep(1); };
    $('m-picker').onchange = function () { var a = this.value.split('-').map(Number); if (a[0]) { monthView = { m: a[1], y: a[0] }; renderMonth(1); } };
    swipe(document.querySelector('.month-card'), mStep);
    $('m-grid').onclick = function (e) {
      var b = e.target.closest('.mc'); if (!b) return;
      var jd = +b.dataset.jd;
      if (jd === monthSel) { openDay(jd); return; }
      monthSel = jd; var s = fromJd(jd);
      if (s.m !== monthView.m) { monthView = { m: s.m, y: s.y }; renderMonth(); } else {
        [].forEach.call($('m-grid').children, function (c) { c.classList.toggle('sel', +c.dataset.jd === jd); });
        renderMonthDetail();
      }
    };
    document.addEventListener('click', function (e) {
      var g = e.target.closest('[data-go]'); if (g) { openDay(+g.dataset.go); return; }
      var li = e.target.closest('.ev-list li');
      if (li) {
        if (li.dataset.id) { var ev = events.find(function (x) { return x.id === li.dataset.id; }); if (ev) { openEventForm(ev); return; } }
        if (li.dataset.jd) openDay(+li.dataset.jd);
      }
    });

    // Sự kiện
    $('ev-seg').onclick = function (e) { var v = e.target.dataset.f; if (!v) return; evFilter = v; [].forEach.call(this.children, function (b) { b.classList.toggle('on', b.dataset.f === v); }); renderEvents(); };
    $('fab-add').onclick = function () { openEventForm(null, todayJd()); };
    $('scrim').onclick = closeSheet;

    // Tab
    $('tabbar').onclick = function (e) { var b = e.target.closest('button'); if (b) switchTab(b.dataset.v); };

    initTools(); initSettings();
    renderDay();
    handleHash();
    if (window.matchMedia) window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
    // cập nhật khi qua ngày mới / giờ mới
    var lastToday = todayJd();
    setInterval(function () {
      var t = todayJd();
      if (t !== lastToday) { if (cur === lastToday) cur = t; lastToday = t; refreshAll(); }
      else if (tab === 'day' && cur === t) renderDay();
    }, 60000);
  }

  // API cho native
  window.App = {
    back: function () {
      if ($('sheet').classList.contains('on')) { closeSheet(); return true; }
      if (tab !== 'day') { switchTab('day'); return true; }
      if (cur !== todayJd()) { goDay(todayJd()); return true; }
      return false;
    },
    handleHash: handleHash,
    pinned: function () { clearTimeout(pinTimer); closeSheet(); toast('Đã thêm widget ra màn hình ✓'); },
    cancelPin: function () { clearTimeout(pinTimer); },
    pinFailed: function () { clearTimeout(pinTimer); widgetGuide(true); },
    onResume: function () { applyTheme(); refreshAll(); }
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
