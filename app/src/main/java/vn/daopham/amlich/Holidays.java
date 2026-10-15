package vn.daopham.amlich;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Ngày lễ Việt Nam (đồng bộ với holidays trong app.js). */
final class Holidays {
    private Holidays() {}

    private static final Map<String, String> SOLAR = new HashMap<>();
    private static final Map<String, String> LUNAR = new HashMap<>();

    static {
        SOLAR.put("1/1", "Tết Dương lịch");
        SOLAR.put("9/1", "Ngày Học sinh – Sinh viên");
        SOLAR.put("3/2", "Thành lập Đảng CSVN");
        SOLAR.put("14/2", "Lễ Tình nhân");
        SOLAR.put("27/2", "Ngày Thầy thuốc Việt Nam");
        SOLAR.put("8/3", "Quốc tế Phụ nữ");
        SOLAR.put("26/3", "Thành lập Đoàn TNCS HCM");
        SOLAR.put("1/4", "Cá tháng Tư");
        SOLAR.put("30/4", "Giải phóng miền Nam");
        SOLAR.put("1/5", "Quốc tế Lao động");
        SOLAR.put("7/5", "Chiến thắng Điện Biên Phủ");
        SOLAR.put("19/5", "Ngày sinh Chủ tịch Hồ Chí Minh");
        SOLAR.put("1/6", "Quốc tế Thiếu nhi");
        SOLAR.put("28/6", "Ngày Gia đình Việt Nam");
        SOLAR.put("27/7", "Ngày Thương binh – Liệt sĩ");
        SOLAR.put("19/8", "Cách mạng tháng Tám");
        SOLAR.put("2/9", "Quốc khánh");
        SOLAR.put("10/10", "Giải phóng Thủ đô");
        SOLAR.put("13/10", "Ngày Doanh nhân Việt Nam");
        SOLAR.put("20/10", "Ngày Phụ nữ Việt Nam");
        SOLAR.put("31/10", "Halloween");
        SOLAR.put("20/11", "Ngày Nhà giáo Việt Nam");
        SOLAR.put("22/12", "Thành lập QĐND Việt Nam");
        SOLAR.put("24/12", "Đêm Giáng sinh");
        SOLAR.put("25/12", "Lễ Giáng sinh");

        LUNAR.put("1/1", "Tết Nguyên Đán");
        LUNAR.put("2/1", "Mùng 2 Tết");
        LUNAR.put("3/1", "Mùng 3 Tết");
        LUNAR.put("15/1", "Tết Nguyên Tiêu");
        LUNAR.put("3/3", "Tết Hàn thực");
        LUNAR.put("10/3", "Giỗ Tổ Hùng Vương");
        LUNAR.put("15/4", "Lễ Phật Đản");
        LUNAR.put("5/5", "Tết Đoan Ngọ");
        LUNAR.put("15/7", "Lễ Vu Lan");
        LUNAR.put("15/8", "Tết Trung Thu");
        LUNAR.put("9/9", "Tết Trùng Cửu");
        LUNAR.put("10/10", "Tết Thường Tân");
        LUNAR.put("15/10", "Tết Hạ Nguyên");
        LUNAR.put("23/12", "Ông Công Ông Táo");
    }

    static String solar(int d, int m, int y) {
        String s = SOLAR.get(d + "/" + m);
        if (s != null) return s;
        // Ngày của Mẹ: CN thứ 2 tháng 5; Ngày của Cha: CN thứ 3 tháng 6
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d);
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            int nth = (d - 1) / 7 + 1;
            if (m == 5 && nth == 2) return "Ngày của Mẹ";
            if (m == 6 && nth == 3) return "Ngày của Cha";
        }
        return null;
    }

    static String lunar(int d, int m, int monthLen) {
        if (m == 12 && d == monthLen) return "Giao thừa";
        return LUNAR.get(d + "/" + m);
    }
}
