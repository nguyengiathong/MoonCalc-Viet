# MoonCalc Việt — ứng dụng Android

Vị trí, pha, giờ mọc và lặn của Mặt Trăng, kèm âm lịch Việt Nam. Ứng dụng bọc trang
`MoonCalc-Viet.html` trong một WebView, chạy **hoàn toàn offline** (font chữ đóng gói sẵn, không xin
quyền Internet). Quyền vị trí chỉ được hỏi khi bấm **Định vị**, quyền thông báo khi bật thông báo
đầu tiên. Yêu cầu Android 7.0 trở lên.

## Tải và cài đặt

1. Vào mục **Releases** của repo này, mở bản mới nhất và tải file `MoonCalc-Viet-1.0.N.apk` bằng
   điện thoại (cần đăng nhập GitHub vì repo ở chế độ riêng tư).
2. Mở file vừa tải. Lần đầu Android sẽ hỏi cho phép trình duyệt / trình quản lý tệp
   "cài ứng dụng không rõ nguồn gốc" — bật cho phép rồi quay lại cài.
3. Mỗi lần đẩy code lên GitHub, workflow **Build APK** tự build và tạo release mới với số phiên bản
   tăng dần, cài đè lên bản cũ được (xem phần khoá ký bên dưới).

File APK của mỗi lần build cũng nằm trong mục *Artifacts* của lượt chạy trong tab **Actions**.

## Khác biệt so với bản HTML gốc

- Font Be Vietnam Pro, Spectral, JetBrains Mono (chỉ bộ ký tự Latin + tiếng Việt) được đóng gói
  trong `app/src/main/assets/www/fonts/` thay vì tải từ Google Fonts.
- Bản đồ: chụm/mở hai ngón để phóng to, thu nhỏ (bản gốc chỉ có cuộn chuột và nút +/−).
- Nút "Sao chép bảng" dùng clipboard của Android.
- Khi quay lại ứng dụng, đồng hồ cập nhật ngay về thời gian hiện tại.
- **Định vị**: nút cạnh ô vĩ độ/kinh độ (hoặc mục "📍 Vị trí hiện tại" trong danh sách Địa điểm)
  lấy vị trí của điện thoại; múi giờ lấy theo múi giờ của điện thoại. Vị trí gần đúng (approximate)
  là đủ. Khi đã chọn chế độ này, mỗi lần mở app sẽ tự cập nhật lại vị trí. Nếu chưa cấp quyền hoặc
  GPS đang tắt, trang hiện thông báo kèm nút "Mở Cài đặt".
- Địa điểm và Múi giờ nằm cùng một hàng; múi giờ hiển thị gọn theo tên thành phố ("Ho Chi Minh").
- "Quanh cao/thấp nhất" và "Quanh 0°" được lưu lại cho lần mở sau (cùng địa điểm, múi giờ, thông báo).
- **Thông báo** (khung dưới bảng "Giờ Mặt Trăng theo độ cao"): với từng khoảng quanh mốc (quanh 0° lúc
  mọc, quanh điểm cao nhất, quanh 0° lúc lặn, quanh điểm thấp nhất) có thể bật riêng thông báo lúc
  bắt đầu và lúc kết thúc. Giờ cạnh mỗi ô là lần báo kế tiếp. Trang tính sẵn lịch 60 ngày tới theo vị
  trí, múi giờ và độ rộng khoảng đang chọn, ứng dụng đặt báo thức chính xác để báo đúng giờ kể cả khi
  đã đóng app hoặc khởi động lại máy. Lịch được tính lại mỗi lần mở app; nếu 58 ngày không mở, sẽ có
  một thông báo nhắc mở app để gia hạn. Nút "Gửi thử thông báo" kiểm tra điện thoại có cho hiện
  thông báo không. Một số máy (Xiaomi, Oppo, Vivo…) cần cho phép ứng dụng chạy nền / tắt tối ưu pin.

Trang vẫn mở được trực tiếp trên trình duyệt: `app/src/main/assets/www/index.html`.

## Khoá ký APK

Android chỉ cho cài bản mới đè lên bản cũ khi cả hai được ký cùng một khoá.

- **Mặc định** (chưa cấu hình gì): lần build đầu tiên, workflow tự tạo một khoá ký và cất trong
  cache của GitHub Actions (không nằm trong repo); các lần sau dùng lại khoá đó. Cache bị GitHub xoá
  nếu 7 ngày liền không có build nào, và mỗi nhánh chỉ thấy cache của chính nó cùng nhánh mặc định.
  Khi khoá bị tạo mới, workflow ghi cảnh báo và bản mới không cài đè được: gỡ ứng dụng cũ rồi cài lại
  (vị trí đã lưu sẽ mất). Mã SHA-256 của chứng chỉ ký hiện ở phần tóm tắt của mỗi lượt build.
- **Khoá riêng, cố định** (nên làm): tạo khoá một lần và lưu vào GitHub Secrets.

  ```sh
  keytool -genkeypair -keystore mooncalc-release.jks -storetype PKCS12 -alias mooncalc \
          -keyalg RSA -keysize 4096 -validity 20000 -dname "CN=MoonCalc Viet"
  base64 -w0 mooncalc-release.jks > mooncalc-release.jks.b64
  ```

  Trong repo trên GitHub: **Settings → Secrets and variables → Actions → New repository secret**,
  tạo 4 secret:

  | Tên | Giá trị |
  | --- | --- |
  | `MOONCALC_KEYSTORE_BASE64` | nội dung file `mooncalc-release.jks.b64` |
  | `MOONCALC_KEYSTORE_PASSWORD` | mật khẩu keystore đã nhập ở lệnh trên |
  | `MOONCALC_KEY_ALIAS` | `mooncalc` |
  | `MOONCALC_KEY_PASSWORD` | mật khẩu khoá (PKCS12: giống mật khẩu keystore) |

  Cất file `.jks` và mật khẩu ở nơi an toàn, **không** commit vào repo. Lần chuyển từ khoá debug sang
  khoá riêng cần gỡ ứng dụng cũ một lần.

## Build trên máy

Cần JDK 17+ và Android SDK (Android Studio có sẵn cả hai):

```sh
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/MoonCalc-Viet-1.0.1.apk
```

Để ký bằng khoá riêng khi build trên máy, đặt các biến môi trường `MOONCALC_KEYSTORE` (đường dẫn
file `.jks`), `MOONCALC_KEYSTORE_PASSWORD`, `MOONCALC_KEY_ALIAS`, `MOONCALC_KEY_PASSWORD`.

## Cấu trúc

| Đường dẫn | Nội dung |
| --- | --- |
| `app/src/main/assets/www/` | Trang MoonCalc (HTML + Astronomy Engine) và font |
| `app/src/main/java/.../MainActivity.java` | WebView, phục vụ assets qua `https://appassets.androidplatform.net/`, xin quyền vị trí, cầu nối clipboard / cài đặt |
| `app/src/main/java/.../MoonNotifier.java`, `AlarmReceiver.java` | Giữ lịch thông báo do trang tính, đặt báo thức chính xác, hiện thông báo, đặt lại sau khi khởi động máy |
| `app/src/main/res/` | Icon, theme tối |
| `.github/workflows/android.yml` | Build, ký, kiểm tra trên emulator, tạo release |

Tính toán thiên văn: [Astronomy Engine](https://github.com/cosinekitty/astronomy) (MIT, Don Cross).
