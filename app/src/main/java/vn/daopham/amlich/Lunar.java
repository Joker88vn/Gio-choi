package vn.daopham.amlich;

/** Lõi âm lịch Việt Nam (thuật toán Hồ Ngọc Đức, múi giờ +7) — bản Java cho widget và thông báo. */
public final class Lunar {
    private Lunar() {}

    private static final double PI = Math.PI;
    private static final double TZ = 7.0;

    public static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    public static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};
    public static final String[] TIET_KHI = {"Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
            "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ", "Thu phân", "Hàn lộ", "Sương giáng",
            "Lập đông", "Tiểu tuyết", "Đại tuyết", "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập"};
    public static final String[] THU = {"Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"};
    private static final boolean[] THAN_TOT = {true, true, false, false, true, true, false, true, false, false, true, false};
    public static final String[] THAN = {"Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Kim Đường",
            "Bạch Hổ", "Ngọc Đường", "Thiên Lao", "Huyền Vũ", "Tư Mệnh", "Câu Trần"};

    private static long INT(double d) { return (long) Math.floor(d); }

    public static long jdFromDate(int dd, int mm, int yy) {
        long a = INT((14 - mm) / 12.0);
        long y = yy + 4800 - a;
        long m = mm + 12 * a - 3;
        long jd = dd + INT((153 * m + 2) / 5.0) + 365 * y + INT(y / 4.0) - INT(y / 100.0) + INT(y / 400.0) - 32045;
        if (jd < 2299161) jd = dd + INT((153 * m + 2) / 5.0) + 365 * y + INT(y / 4.0) - 32083;
        return jd;
    }

    public static int[] jdToDate(long jd) {
        long a, b, c;
        if (jd > 2299160) {
            a = jd + 32044;
            b = INT((4 * a + 3) / 146097.0);
            c = a - INT((b * 146097) / 4.0);
        } else {
            b = 0;
            c = jd + 32082;
        }
        long d = INT((4 * c + 3) / 1461.0);
        long e = c - INT((1461 * d) / 4.0);
        long m = INT((5 * e + 2) / 153.0);
        int day = (int) (e - INT((153 * m + 2) / 5.0) + 1);
        int month = (int) (m + 3 - 12 * INT(m / 10.0));
        int year = (int) (b * 100 + d - 4800 + INT(m / 10.0));
        return new int[]{day, month, year};
    }

    private static double newMoon(long k) {
        double T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = PI / 180;
        double Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        Jd1 = Jd1 + 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
        C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
        C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
        C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        double deltat;
        if (T < -11) deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3;
        else deltat = -0.000278 + 0.000265 * T + 0.000262 * T2;
        return Jd1 + C1 - deltat;
    }

    private static double sunLongitudeRad(double jdn) {
        double T = (jdn - 2451545.0) / 36525, T2 = T * T, dr = PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL = DL + (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L = L - PI * 2 * INT(L / (PI * 2));
        return L;
    }

    private static long getNewMoonDay(long k) { return INT(newMoon(k) + 0.5 + TZ / 24); }

    private static long getSunLongitude(long dayNumber) { return INT(sunLongitudeRad(dayNumber - 0.5 - TZ / 24) / PI * 6); }

    private static long getLunarMonth11(int yy) {
        long off = jdFromDate(31, 12, yy) - 2415021;
        long k = INT(off / 29.530588853);
        long nm = getNewMoonDay(k);
        if (getSunLongitude(nm) >= 9) nm = getNewMoonDay(k - 1);
        return nm;
    }

    private static int getLeapMonthOffset(long a11) {
        long k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        long last;
        int i = 1;
        long arc = getSunLongitude(getNewMoonDay(k + i));
        do {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }

    /** Kết quả: {ngày âm, tháng âm, năm âm, nhuận(0/1), số ngày trong tháng âm} */
    public static int[] solar2lunar(int dd, int mm, int yy) {
        long dayNumber = jdFromDate(dd, mm, yy);
        long k = INT((dayNumber - 2415021.076998695) / 29.530588853);
        long monthStart = getNewMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k);
        long a11 = getLunarMonth11(yy), b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = getLunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = getLunarMonth11(yy + 1);
        }
        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) INT((monthStart - a11) / 29.0);
        int lunarLeap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) lunarLeap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        long nextStart = getNewMoonDay(INT((monthStart - 2415021.076998695) / 29.530588853 + 0.5) + 1);
        return new int[]{lunarDay, lunarMonth, lunarYear, lunarLeap, (int) (nextStart - monthStart)};
    }

    /** Trả về {d, m, y} dương lịch hoặc null nếu không hợp lệ. */
    public static int[] lunar2solar(int lunarDay, int lunarMonth, int lunarYear, boolean leap) {
        long a11, b11;
        if (lunarMonth < 11) {
            a11 = getLunarMonth11(lunarYear - 1);
            b11 = getLunarMonth11(lunarYear);
        } else {
            a11 = getLunarMonth11(lunarYear);
            b11 = getLunarMonth11(lunarYear + 1);
        }
        long k = INT(0.5 + (a11 - 2415021.076998695) / 29.530588853);
        int off = lunarMonth - 11;
        if (off < 0) off += 12;
        if (b11 - a11 > 365) {
            int leapOff = getLeapMonthOffset(a11);
            int leapMonth = leapOff - 2;
            if (leapMonth < 0) leapMonth += 12;
            if (leap && lunarMonth != leapMonth) return null;
            else if (leap || off >= leapOff) off += 1;
        } else if (leap) return null;
        long monthStart = getNewMoonDay(k + off);
        return jdToDate(monthStart + lunarDay - 1);
    }

    private static int mod(long a, int n) { return (int) (((a % n) + n) % n); }

    public static String dayCanChi(int dd, int mm, int yy) {
        long jd = jdFromDate(dd, mm, yy);
        return CAN[mod(jd + 9, 10)] + " " + CHI[mod(jd + 1, 12)];
    }

    public static String yearCanChi(int lunarYear) { return CAN[(lunarYear + 6) % 10] + " " + CHI[(lunarYear + 8) % 12]; }

    public static String monthCanChi(int lunarMonth, int lunarYear) {
        return CAN[(lunarYear * 12 + lunarMonth + 3) % 10] + " " + CHI[(lunarMonth + 1) % 12];
    }

    public static String tietKhi(int dd, int mm, int yy) {
        long jd = jdFromDate(dd, mm, yy);
        return TIET_KHI[(int) INT(sunLongitudeRad(jd + 1 - 0.5 - TZ / 24) / PI * 12)];
    }

    private static int monthChiByTiet(long jd) {
        double deg = sunLongitudeRad(jd + 1 - 0.5 - TZ / 24) * 180 / PI;
        int s = (int) INT((((deg - 315) % 360) + 360) % 360 / 30);
        return (s + 2) % 12;
    }

    /** Ngày hoàng đạo? */
    public static boolean isHoangDao(int dd, int mm, int yy) {
        long jd = jdFromDate(dd, mm, yy);
        int dChi = mod(jd + 1, 12);
        int mChi = monthChiByTiet(jd);
        int start = (mod(mChi - 2, 12) % 6) * 2;
        return THAN_TOT[mod(dChi - start, 12)];
    }

    /** Danh sách tên giờ hoàng đạo, vd "Tý (23h-1h)". */
    public static String gioHoangDao(int dd, int mm, int yy, boolean withRange) {
        String[] range = {"23-1h", "1-3h", "3-5h", "5-7h", "7-9h", "9-11h", "11-13h", "13-15h", "15-17h", "17-19h", "19-21h", "21-23h"};
        long jd = jdFromDate(dd, mm, yy);
        int dChi = mod(jd + 1, 12);
        int start = (mod(dChi - 2, 12) % 6) * 2;
        StringBuilder sb = new StringBuilder();
        for (int h = 0; h < 12; h++) {
            if (THAN_TOT[mod(h - start, 12)]) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(CHI[h]);
                if (withRange) sb.append(" (").append(range[h]).append(")");
            }
        }
        return sb.toString();
    }

    /** Ngày lễ trong ngày (dương + âm), chuỗi rỗng nếu không có. */
    public static String holiday(int dd, int mm, int yy) {
        int[] l = solar2lunar(dd, mm, yy);
        String s = Holidays.solar(dd, mm, yy);
        if (s != null) return s;
        if (l[3] == 0) {
            String h = Holidays.lunar(l[0], l[1], l[4]);
            if (h != null) return h;
        }
        return "";
    }

    public static String lunarDayLabel(int[] l) {
        if (l[0] == 1) return "Mùng 1";
        if (l[0] <= 10) return "Mùng " + l[0];
        if (l[0] == 15) return "Rằm";
        return String.valueOf(l[0]);
    }

    public static String lunarMonthName(int m, boolean leap) {
        String n = m == 1 ? "Giêng" : m == 12 ? "Chạp" : String.valueOf(m);
        return "tháng " + n + (leap ? " nhuận" : "");
    }
}
