# COD Master — master âm thanh trên điện thoại

App Android master nhạc (đặc biệt bài từ Suno) ngay trên máy, không cần mạng.

## Cách dùng
1. **Chọn file nhạc** (MP3, M4A, WAV, FLAC, OGG…) — hoặc chia sẻ file từ app khác vào COD Master.
2. Chọn **phong cách**: Streaming (−14 LUFS), Rock Ballad, Giọng hát rõ, Ấm/Acoustic, To & đậm, Nhẹ nhàng.
3. **Tinh chỉnh** nếu muốn (bass, gọn trung trầm, rõ giọng, độ sáng, độ ấm, nén, độ rộng stereo, trần đỉnh).
4. Bấm **MASTER NGAY**, rồi **Phát** và chuyển **Gốc / Master** để so sánh.
   Bật **Cân âm lượng** để nghe khác biệt về màu tiếng mà không bị lừa vì bản master to hơn.
5. **Lưu file master**: WAV 24-bit (cao nhất) hoặc 16-bit (có dither).

## Chuỗi xử lý
Lọc siêu trầm 28 Hz → EQ (kệ 100 Hz, chuông 300 Hz, chuông 3 kHz, kệ 10 kHz) → nén glue liên kết stereo
→ bão hoà kiểu băng từ → độ rộng M/S (bass dưới ~110 Hz giữ mono) → đưa lên LUFS mục tiêu
→ limiter nhìn trước 3 ms phát hiện đỉnh thật (true peak). Đo độ lớn theo ITU BS.1770-4 / EBU R128,
đỉnh thật đo bằng nội suy 8x.

## Build
GitHub Actions tự build khi đẩy lên nhánh `audio-master`. APK nằm ở Releases (`codmaster-N`)
và nhánh `apk-codmaster`.

## Bản Windows
Mã nguồn ở `desktop/` (Java Swing, dùng chung lõi `dsp` với bản Android). GitHub Actions
(`windows.yml`) đóng gói bằng jpackage kèm Java và ffmpeg: bộ cài `CODMaster-Setup-*.exe`
và bản chạy liền `CODMaster-Portable-*.zip`, ở Releases `codmaster-win-N`.
Thêm: kéo thả file, phím Space (phát/dừng), B (đổi Gốc ↔ Master), xuất MP3 320k.

### Chế độ 2 stem (bản Windows 1.3)
Nạp riêng **giọng hát** và **nhạc nền** (ví dụ stem Vocals / Instrumental tải từ Suno).
- Giọng: cắt trầm, gọn đục, nén giọng RMS, khử xì động (chỉ dải 5–10 kHz khi có xì), rõ lời, sáng giọng, vang plate.
- Nhạc nền: trầm riêng, độ rộng riêng, **nhường chỗ cho giọng** — EQ động hạ vùng 1–4.5 kHz của nhạc khi giọng cất lên.
- Mỗi stem được giữ đúng độ lớn như bản gốc sau xử lý, rồi cộng/trừ theo thanh "to/nhỏ", sau đó master tổng.

### Khử vang & delay có sẵn trong giọng Suno (bản Windows 1.4)
Trong chế độ 2 stem, tab GIỌNG có thêm **Khử vang Suno** và **Khử delay Suno** (xử lý đầu tiên trong chuỗi giọng):
- Tự đo độ dài vang (T60) từ các chỗ kết thúc câu; nhận ra giọng khô sẵn để không đụng vào.
- Dò delay bằng cepstrum (đỉnh nhọn ở thời gian trễ), phân biệt với rung giọng (vibrato).
- Trừ phổ vang muộn + tiếng dội theo khung STFT, làm mượt để hạn chế tiếng lạo xạo, kèm cổng đuôi vang.
- Nghe so sánh riêng giọng: GIỌNG GỐC ↔ GIỌNG MỚI (phím V).
