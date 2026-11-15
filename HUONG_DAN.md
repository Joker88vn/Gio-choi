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
