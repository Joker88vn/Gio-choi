# Giờ Chơi – giới hạn thời gian dùng tablet cho con

## App làm được gì
- **Tổng thời gian mỗi ngày**: tách riêng ngày thường và cuối tuần. Hết giờ thì máy tự hiện màn hình khoá phủ lên mọi thứ, kể cả màn hình chính.
- **Từng app**: Cho phép / Giới hạn X phút mỗi ngày / Chặn hẳn.
- **Mật khẩu bố mẹ** (4–12 chữ số), nhập trên bàn phím số ngay ở màn hình khoá. Nhập đúng thì chọn **+15 phút / +30 phút / +1 giờ / Cả hôm nay**.
- Báo trước khi còn 5 phút và 1 phút.
- Khoá luôn phần **Cài đặt** và **cài/gỡ app** bằng mật khẩu (bật sẵn), để con không tắt được app.
- Sau 0 giờ, thời gian tự đặt lại cho ngày mới.

---

## Bước 1 – Build ra file APK (chọn 1 trong 2 cách)

### Cách A: Android Studio trên máy tính (khuyên dùng)
1. Tải và cài **Android Studio**: https://developer.android.com/studio
2. Giải nén `GioChoi.zip`. Trong Android Studio chọn **File → Open** rồi chọn thư mục `GioChoi`.
3. Chờ nó tải và đồng bộ (Gradle Sync). Lần đầu mất khoảng 5–10 phút. Nếu nó hỏi nâng cấp gì thì cứ đồng ý.
4. Vào menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Build xong, bấm **locate** ở góc dưới. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

> Cách nhanh hơn: trên tab bật **Tuỳ chọn nhà phát triển → Gỡ lỗi USB**, cắm cáp vào máy tính rồi bấm nút ▶ Run trong Android Studio. App sẽ được cài thẳng vào tab.

### Cách B: Không cài gì, build trên GitHub
1. Tạo tài khoản GitHub, tạo một repository mới (để Private).
2. Upload toàn bộ nội dung thư mục `GioChoi`, gồm cả thư mục ẩn `.github`.
3. Vào tab **Actions**, chờ bước "Build APK" chạy xong (khoảng 5 phút).
4. Bấm vào lần chạy đó, phần **Artifacts** có `GioChoi-apk`. Tải về và giải nén là có file APK.

---

## Bước 2 – Cài lên Tab S8
1. Chép file APK vào tab (qua cáp, Google Drive hoặc Zalo đều được), rồi mở file.
2. Tab sẽ hỏi quyền cài app ngoài cửa hàng: chọn **Cho phép**, rồi **Cài đặt**.
3. Mở app **Giờ Chơi** và đặt mật khẩu phụ huynh.

## Bước 3 – Bật dịch vụ (quan trọng)
1. Trong Giờ Chơi, bấm **Mở cài đặt Trợ năng** → **Ứng dụng đã cài đặt** → **Giờ Chơi** → Bật.
2. **Nếu nút bật bị mờ hoặc hiện "Cài đặt bị hạn chế"** (Android 13 trở lên chặn app cài ngoài):
   - Vào **Cài đặt → Ứng dụng → Giờ Chơi**
   - Bấm dấu **⋮** góc trên phải → **Cho phép cài đặt bị hạn chế**
   - Quay lại bước 1 và bật.
3. Bấm **Không tối ưu pin cho Giờ Chơi** để Samsung không tắt app.
4. Quay lại Giờ Chơi, thấy dòng **✅ Đang bảo vệ** là xong.

## Bước 4 – Đặt giới hạn
- **Tổng thời gian mỗi ngày**: nhập số phút cho ngày thường và cuối tuần, rồi bấm Lưu. Nhập 0 nếu không giới hạn.
- **Ứng dụng**: chạm vào từng app để chọn Cho phép / Giới hạn phút / Chặn.
- Các nút **+30 phút hôm nay** và **Đặt lại hôm nay** để anh chỉnh nhanh.

---

## Lưu ý
- Thời gian chỉ được tính khi màn hình sáng và đã mở khoá màn hình.
- Khi bật khoá Cài đặt, vào Cài đặt sẽ phải nhập mật khẩu. Bấm "Mở cài đặt Trợ năng" từ trong Giờ Chơi thì được vào tự do 5 phút.
- **Quên mật khẩu**: khởi động tab vào **Chế độ an toàn** (giữ nút giảm âm lượng khi máy đang khởi động). Ở chế độ này app bên ngoài bị tắt, anh vào Cài đặt gỡ Giờ Chơi rồi cài lại. Trẻ lớn, rành máy cũng có thể biết cách này.
- Muốn đổi mật khẩu: vào Giờ Chơi → **Đổi mật khẩu**.
