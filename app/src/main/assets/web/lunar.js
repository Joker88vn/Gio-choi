/* Lõi âm lịch Việt Nam — thuật toán Hồ Ngọc Đức, múi giờ +7 (chuẩn Việt Nam).
   Chạy hoàn toàn offline. */
(function (global) {
  'use strict';
  var PI = Math.PI, TZ = 7.0;
  function INT(d) { return Math.floor(d); }

  function jdFromDate(dd, mm, yy) {
    var a = INT((14 - mm) / 12), y = yy + 4800 - a, m = mm + 12 * a - 3;
    var jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - INT(y / 100) + INT(y / 400) - 32045;
    if (jd < 2299161) jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - 32083;
    return jd;
  }
  function jdToDate(jd) {
    var a, b, c;
    if (jd > 2299160) { a = jd + 32044; b = INT((4 * a + 3) / 146097); c = a - INT((b * 146097) / 4); }
    else { b = 0; c = jd + 32082; }
    var d = INT((4 * c + 3) / 1461), e = c - INT((1461 * d) / 4), m = INT((5 * e + 2) / 153);
    return [e - INT((153 * m + 2) / 5) + 1, m + 3 - 12 * INT(m / 10), b * 100 + d - 4800 + INT(m / 10)];
  }
  function newMoon(k) {
    var T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = PI / 180;
    var Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
    Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
    var M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
    var Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
    var F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
    var C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
    C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
    C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
    C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
    C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
    C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
    C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
    var deltat;
    if (T < -11) deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3;
    else deltat = -0.000278 + 0.000265 * T + 0.000262 * T2;
    return Jd1 + C1 - deltat;
  }
  function sunLongitudeRad(jdn) {
    var T = (jdn - 2451545.0) / 36525, T2 = T * T, dr = PI / 180;
    var M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
    var L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
    var DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
    DL = DL + (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
    var L = (L0 + DL) * dr;
    L = L - PI * 2 * INT(L / (PI * 2));
    return L;
  }
  function getNewMoonDay(k) { return INT(newMoon(k) + 0.5 + TZ / 24); }
  function getSunLongitude(dayNumber) { return INT(sunLongitudeRad(dayNumber - 0.5 - TZ / 24) / PI * 6); }
  function getLunarMonth11(yy) {
    var off = jdFromDate(31, 12, yy) - 2415021, k = INT(off / 29.530588853), nm = getNewMoonDay(k);
    if (getSunLongitude(nm) >= 9) nm = getNewMoonDay(k - 1);
    return nm;
  }
  function getLeapMonthOffset(a11) {
    var k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5), last, i = 1;
    var arc = getSunLongitude(getNewMoonDay(k + i));
    do { last = arc; i++; arc = getSunLongitude(getNewMoonDay(k + i)); } while (arc != last && i < 14);
    return i - 1;
  }
  var cache = {};
  function solar2lunar(dd, mm, yy) {
    var key = dd + '/' + mm + '/' + yy;
    if (cache[key]) return cache[key];
    var dayNumber = jdFromDate(dd, mm, yy), k = INT((dayNumber - 2415021.076998695) / 29.530588853);
    var monthStart = getNewMoonDay(k + 1);
    if (monthStart > dayNumber) monthStart = getNewMoonDay(k);
    var a11 = getLunarMonth11(yy), b11 = a11, lunarYear;
    if (a11 >= monthStart) { lunarYear = yy; a11 = getLunarMonth11(yy - 1); }
    else { lunarYear = yy + 1; b11 = getLunarMonth11(yy + 1); }
    var lunarDay = dayNumber - monthStart + 1, diff = INT((monthStart - a11) / 29), lunarLeap = 0, lunarMonth = diff + 11;
    if (b11 - a11 > 365) {
      var leapMonthDiff = getLeapMonthOffset(a11);
      if (diff >= leapMonthDiff) { lunarMonth = diff + 10; if (diff == leapMonthDiff) lunarLeap = 1; }
    }
    if (lunarMonth > 12) lunarMonth -= 12;
    if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
    // độ dài tháng âm
    var nextStart = getNewMoonDay(INT((monthStart - 2415021.076998695) / 29.530588853 + 0.5) + 1);
    var res = { day: lunarDay, month: lunarMonth, year: lunarYear, leap: lunarLeap === 1, jd: dayNumber, monthLen: nextStart - monthStart };
    cache[key] = res;
    return res;
  }
  function lunar2solar(lunarDay, lunarMonth, lunarYear, lunarLeap) {
    var a11, b11;
    if (lunarMonth < 11) { a11 = getLunarMonth11(lunarYear - 1); b11 = getLunarMonth11(lunarYear); }
    else { a11 = getLunarMonth11(lunarYear); b11 = getLunarMonth11(lunarYear + 1); }
    var k = INT(0.5 + (a11 - 2415021.076998695) / 29.530588853), off = lunarMonth - 11;
    if (off < 0) off += 12;
    if (b11 - a11 > 365) {
      var leapOff = getLeapMonthOffset(a11), leapMonth = leapOff - 2;
      if (leapMonth < 0) leapMonth += 12;
      if (lunarLeap && lunarMonth != leapMonth) return null;
      else if (lunarLeap || off >= leapOff) off += 1;
    } else if (lunarLeap) return null;
    var monthStart = getNewMoonDay(k + off);
    var r = jdToDate(monthStart + lunarDay - 1);
    return { day: r[0], month: r[1], year: r[2] };
  }
  /* Tháng nhuận của năm âm (0 nếu không có) */
  function leapMonthOfYear(lunarYear) {
    var a11 = getLunarMonth11(lunarYear - 1), b11 = getLunarMonth11(lunarYear);
    if (b11 - a11 <= 365) return 0;
    var lm = getLeapMonthOffset(a11) - 2;
    if (lm < 0) lm += 12;
    return lm;
  }

  var CAN = ['Giáp', 'Ất', 'Bính', 'Đinh', 'Mậu', 'Kỷ', 'Canh', 'Tân', 'Nhâm', 'Quý'];
  var CHI = ['Tý', 'Sửu', 'Dần', 'Mão', 'Thìn', 'Tỵ', 'Ngọ', 'Mùi', 'Thân', 'Dậu', 'Tuất', 'Hợi'];
  var CON_GIAP = ['Chuột', 'Trâu', 'Hổ', 'Mèo', 'Rồng', 'Rắn', 'Ngựa', 'Dê', 'Khỉ', 'Gà', 'Chó', 'Lợn'];
  var TIET_KHI = ['Xuân phân', 'Thanh minh', 'Cốc vũ', 'Lập hạ', 'Tiểu mãn', 'Mang chủng', 'Hạ chí', 'Tiểu thử',
    'Đại thử', 'Lập thu', 'Xử thử', 'Bạch lộ', 'Thu phân', 'Hàn lộ', 'Sương giáng', 'Lập đông', 'Tiểu tuyết',
    'Đại tuyết', 'Đông chí', 'Tiểu hàn', 'Đại hàn', 'Lập xuân', 'Vũ thủy', 'Kinh trập'];
  var TRUC = ['Kiến', 'Trừ', 'Mãn', 'Bình', 'Định', 'Chấp', 'Phá', 'Nguy', 'Thành', 'Thu', 'Khai', 'Bế'];
  var THAN = ['Thanh Long', 'Minh Đường', 'Thiên Hình', 'Chu Tước', 'Kim Quỹ', 'Kim Đường',
    'Bạch Hổ', 'Ngọc Đường', 'Thiên Lao', 'Huyền Vũ', 'Tư Mệnh', 'Câu Trần'];
  var THAN_TOT = [true, true, false, false, true, true, false, true, false, false, true, false];
  var SAO28 = ['Giác', 'Cang', 'Đê', 'Phòng', 'Tâm', 'Vĩ', 'Cơ', 'Đẩu', 'Ngưu', 'Nữ', 'Hư', 'Nguy', 'Thất', 'Bích',
    'Khuê', 'Lâu', 'Vị', 'Mão', 'Tất', 'Chủy', 'Sâm', 'Tỉnh', 'Quỷ', 'Liễu', 'Tinh', 'Trương', 'Dực', 'Chẩn'];
  var SAO28_TOT = [1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 1];
  var NAP_AM = ['Hải Trung Kim', 'Lư Trung Hỏa', 'Đại Lâm Mộc', 'Lộ Bàng Thổ', 'Kiếm Phong Kim', 'Sơn Đầu Hỏa',
    'Giản Hạ Thủy', 'Thành Đầu Thổ', 'Bạch Lạp Kim', 'Dương Liễu Mộc', 'Tuyền Trung Thủy', 'Ốc Thượng Thổ',
    'Tích Lịch Hỏa', 'Tùng Bách Mộc', 'Trường Lưu Thủy', 'Sa Trung Kim', 'Sơn Hạ Hỏa', 'Bình Địa Mộc',
    'Bích Thượng Thổ', 'Kim Bạch Kim', 'Phú Đăng Hỏa', 'Thiên Hà Thủy', 'Đại Trạch Thổ', 'Thoa Xuyến Kim',
    'Tang Đố Mộc', 'Đại Khê Thủy', 'Sa Trung Thổ', 'Thiên Thượng Hỏa', 'Thạch Lựu Mộc', 'Đại Hải Thủy'];
  var HY_THAN = ['Đông Bắc', 'Tây Bắc', 'Tây Nam', 'Chính Nam', 'Đông Nam', 'Đông Bắc', 'Tây Bắc', 'Tây Nam', 'Chính Nam', 'Đông Nam'];
  var TAI_THAN = ['Đông Nam', 'Đông Nam', 'Chính Đông', 'Chính Đông', 'Chính Bắc', 'Chính Nam', 'Tây Nam', 'Tây Nam', 'Chính Tây', 'Tây Bắc'];
  var GIO_RANGE = ['23h–1h', '1h–3h', '3h–5h', '5h–7h', '7h–9h', '9h–11h', '11h–13h', '13h–15h', '15h–17h', '17h–19h', '19h–21h', '21h–23h'];
  var TRUC_VIEC = [
    ['Xuất hành, cưới hỏi, khai trương', 'Động thổ, đào giếng, lợp mái'],
    ['Cúng tế, giải hạn, chữa bệnh', 'Cưới hỏi, xuất vốn, đi xa'],
    ['Cầu tài, cúng tế, khai trương, mở kho', 'Nhậm chức, kiện tụng, an táng'],
    ['Sửa nhà, làm đường, giao dịch', 'Đào mương, đi sông nước'],
    ['Giao dịch, cưới hỏi, nhập học, mua bán', 'Kiện tụng, xuất hành đi xa'],
    ['Lập khế ước, thuê người, xây dựng', 'Xuất kho, chuyển nhà, khai trương'],
    ['Phá dỡ nhà cũ, chữa bệnh', 'Cưới hỏi, khai trương, việc lớn'],
    ['Cúng tế, lễ bái', 'Leo cao, đi thuyền, khởi sự lớn'],
    ['Khai trương, cưới hỏi, nhập trạch, xây nhà', 'Kiện tụng, tranh chấp'],
    ['Thu nợ, mua hàng, nhập kho, gieo trồng', 'An táng, khai trương, đi xa'],
    ['Khai trương, cưới hỏi, xây dựng, xuất hành', 'An táng, động thổ'],
    ['Đắp đê, lấp hố, xây tường', 'Khai trương, nhậm chức, chữa mắt']
  ];

  function mod(a, n) { return ((a % n) + n) % n; }
  function canChiIndex(can, chi) { // vị trí trong vòng 60 hoa giáp
    for (var i = 0; i < 60; i++) if (i % 10 === can && i % 12 === chi) return i;
    return 0;
  }
  function tietKhiIndex(jd) { return INT(sunLongitudeRad(jd + 1 - 0.5 - TZ / 24) / PI * 12); }
  /* chi tháng theo tiết khí (Lập xuân bắt đầu tháng Dần) */
  function monthChiByTiet(jd) {
    var deg = sunLongitudeRad(jd + 1 - 0.5 - TZ / 24) * 180 / PI; // cuối ngày
    var s = INT(mod(deg - 315, 360) / 30);
    return (s + 2) % 12;
  }
  function gioHoangDao(dayChi) {
    var start = (mod(dayChi - 2, 12) % 6) * 2, res = [];
    for (var h = 0; h < 12; h++) {
      var idx = mod(h - start, 12);
      res.push({ chi: h, name: CHI[h], range: GIO_RANGE[h], than: THAN[idx], good: THAN_TOT[idx] });
    }
    return res;
  }

  function dayInfo(dd, mm, yy) {
    var l = solar2lunar(dd, mm, yy), jd = l.jd;
    var dCan = (jd + 9) % 10, dChi = (jd + 1) % 12;
    var yCan = (l.year + 6) % 10, yChi = (l.year + 8) % 12;
    var mCan = (l.year * 12 + l.month + 3) % 10, mChi = (l.month + 1) % 12;
    var mChiTiet = monthChiByTiet(jd);
    var trucIdx = mod(dChi - mChiTiet, 12);
    var startT = (mod(mChiTiet - 2, 12) % 6) * 2, thanIdx = mod(dChi - startT, 12);
    var tk = tietKhiIndex(jd), tkPrev = tietKhiIndex(jd - 1);
    var sao = mod(jd - 2451545 + 16, 28);
    var xungChi = (dChi + 6) % 12;
    var canXung = [(dCan + 6) % 10, (dCan + 4) % 10];
    var warn = [];
    if ([3, 7, 13, 18, 22, 27].indexOf(l.day) >= 0) warn.push('Ngày Tam nương');
    if ([5, 14, 23].indexOf(l.day) >= 0) warn.push('Ngày Nguyệt kỵ');
    return {
      solar: { day: dd, month: mm, year: yy, weekday: new Date(yy, mm - 1, dd).getDay() },
      lunar: l,
      canChi: {
        day: CAN[dCan] + ' ' + CHI[dChi], month: CAN[mCan] + ' ' + CHI[mChi], year: CAN[yCan] + ' ' + CHI[yChi],
        dayCan: dCan, dayChi: dChi, yearChi: yChi
      },
      conGiap: CON_GIAP[yChi],
      napAmNgay: NAP_AM[INT(canChiIndex(dCan, dChi) / 2)],
      napAmNam: NAP_AM[INT(canChiIndex(yCan, yChi) / 2)],
      tietKhi: TIET_KHI[tk], tietKhiStart: tk !== tkPrev,
      truc: TRUC[trucIdx], trucViec: TRUC_VIEC[trucIdx],
      than: THAN[thanIdx], hoangDao: THAN_TOT[thanIdx],
      sao: SAO28[sao], saoTot: SAO28_TOT[sao] === 1,
      gio: gioHoangDao(dChi),
      hyThan: HY_THAN[dCan], taiThan: TAI_THAN[dCan],
      xung: CAN[canXung[0]] + ' ' + CHI[xungChi] + ', ' + CAN[canXung[1]] + ' ' + CHI[xungChi],
      lucHop: CHI[mod(1 - dChi, 12)], tamHop: [CHI[(dChi + 4) % 12], CHI[(dChi + 8) % 12]],
      warnings: warn
    };
  }
  function yearCanChi(y) { return CAN[(y + 6) % 10] + ' ' + CHI[(y + 8) % 12]; }
  function yearNapAm(y) { return NAP_AM[INT(canChiIndex((y + 6) % 10, (y + 8) % 12) / 2)]; }

  global.AmLich = {
    jdFromDate: jdFromDate, jdToDate: jdToDate, solar2lunar: solar2lunar, lunar2solar: lunar2solar,
    leapMonthOfYear: leapMonthOfYear, dayInfo: dayInfo, yearCanChi: yearCanChi, yearNapAm: yearNapAm,
    CAN: CAN, CHI: CHI, CON_GIAP: CON_GIAP, TIET_KHI: TIET_KHI, NAP_AM: NAP_AM
  };
})(typeof window !== 'undefined' ? window : globalThis);
