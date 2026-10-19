# MoonCalc Việt — ứng dụng Android

Vị trí, pha, giờ mọc và lặn của Mặt Trăng, kèm âm lịch Việt Nam. Ứng dụng bọc trang
`MoonCalc-Viet.html` trong một WebView, chạy **hoàn toàn offline** (font chữ đóng gói sẵn, không xin
quyền Internet). Yêu cầu Android 7.0 trở lên.

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

Trang vẫn mở được trực tiếp trên trình duyệt: `app/src/main/assets/www/index.html`.

## Khoá ký APK

Android chỉ cho cài bản mới đè lên bản cũ khi cả hai được ký cùng một khoá.

- **Mặc định** (chưa cấu hình gì): APK được ký bằng khoá debug. Workflow lưu khoá này trong cache
  của GitHub Actions, nhưng cache bị xoá nếu 7 ngày không có build nào. Khi đó bản mới sẽ không cài
  đè được — gỡ ứng dụng cũ rồi cài lại (vị trí đã lưu sẽ mất).
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
| `app/src/main/java/.../MainActivity.java` | WebView, phục vụ assets qua `https://appassets.androidplatform.net/`, cầu nối clipboard |
| `app/src/main/res/` | Icon, theme tối |
| `.github/workflows/android.yml` | Build, ký, kiểm tra trên emulator, tạo release |

Tính toán thiên văn: [Astronomy Engine](https://github.com/cosinekitty/astronomy) (MIT, Don Cross).
