# OPlusKeyControl

Ứng dụng Android Kotlin/Compose tùy biến nút theo lịch, đang phát triển cho
OnePlus 15 / Android 16. **Bản 0.1.0-dev chưa remap nút thật.**
Không phải bản nghiệm thu đầy đủ theo [yêu cầu](docs/IMPLEMENTATION_BRIEF.vi.md).
[English](README.en.md) · [Khảo sát thiết bị](docs/DEVICE_DISCOVERY.md) ·
[Ma trận nghiệm thu](docs/ACCEPTANCE.md) · [Recovery](docs/RECOVERY.md).

## Đã triển khai

- Compose Material 3, sáng/tối/dynamic color, tiếng Việt mặc định và tiếng Anh.
- Wizard nút/gesture/lịch/action; không nhập package/class/intent/shell.
- Lõi Kotlin độc lập: single/double/long, tổ hợp giữ nút và gần đồng thời,
  debounce, repeat/cancel, ownership; **chưa nối vào policy của điện thoại**.
- Rule nhiều khung giờ, thứ trong tuần, qua nửa đêm, `[start,end)`, ưu tiên,
  default, báo xung đột; clock/múi giờ hiện tại khi resolver được gọi.
- DataStore JSON schema v1, kiểm tra dữ liệu nhập, khôi phục mặc định tắt.
- Quét app và activity từng package trên IO; icon tải theo hàng hiển thị,
  tìm tên, lọc hệ thống/advanced, làm mới khi package thay đổi.
- Action thủ công thật: đèn pin CameraManager, mở launcher, ghi âm AAC/m4a
  từ UI hiển thị với microphone FGS/thông báo Stop, tự dừng; lịch sử 50 bản.
- Accessibility chỉ quan sát Volume, luôn `return false`; không đọc màn hình.
- Phát hiện binder/quyền Shizuku thật; `su -c id` chỉ khi chủ động chọn Root.
- Modern libxposed API **101.0.1** `compileOnly`, entry **probe không hook**,
  scope chỉ `dev.opluskeycontrol`. Binary Vector hiện cài báo API 102 qua
  phân tích bytecode; chưa kích hoạt probe để xác nhận runtime.

## Chưa hỗ trợ trong bản này

Plus/Power/Volume replacement trên máy thật; capture qua Shizuku; native/root
input listener; hook OEM; screen-off/deep-sleep remap; tự chụp camera stock;
launch activity đặc quyền; bắt đầu ghi âm từ màn hình khóa; multi-profile.
Các rule liên quan chỉ lưu dạng tắt. Không tự thay bằng camera riêng, inject
Volume làm shutter, giả UID/chữ ký, hoặc chiếm cả input device.

Power single/long và mọi chord chứa Power bị chặn ở domain/UI. Hai nút Volume
cùng nhau được bảo lưu. Các nhánh SOS/hard-reset không bị can thiệp.
Tắt master không được hiểu là đã tắt toàn bộ dịch vụ quan sát Trợ năng do user bật;
dịch vụ đó không chiếm nút ngay cả khi master bật.

## Build và kiểm tra

JDK 17, SDK platform 36, Gradle wrapper 9.3.1 (SHA-256 pinned), AGP 9.1.0,
Kotlin 2.4.10. Tối thiểu Android 9/API 28; mục tiêu kiểm chứng Android 16/API 36.

```powershell
# Đặt SDK bằng ANDROID_HOME hoặc local.properties (file này không commit).
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Khóa debug do Android
toolchain tạo trên máy build; không có keystore riêng trong repo. CI chưa chạy
trên GitHub cho tới khi người dùng cho phép publish/push. Không có SDK mạng,
analytics hoặc quảng cáo.

## Cài đặt và quyền

Chỉ cài APK khi đã cho phép thử trên thiết bị. Mở app trước, kiểm tra trạng thái
capability. Nút kiểm tra thủ công thực hiện hành động ngay khi nhấn. Ghi âm chỉ
bắt đầu từ UI đang hiển thị sau khi cấp microphone; thông báo và indicator hệ
thống giữ nguyên. File nằm ở `Music/OPlusKeyControl` trên Android 10+; Android 9
dùng thư mục Music riêng của app (lịch sử MediaStore chỉ từ Android 10).

Để xem event Volume, user có thể bật Trợ năng; Android có thể yêu cầu cho phép
Restricted Settings đối với APK sideload. App không tự cấp quyền này.
Shizuku yêu cầu dịch vụ đang chạy và user cấp quyền; chưa có event adapter,
không tự dùng root khi thiếu Shizuku. Không cần cài KSU module phụ.
Probe Vector không cần cho UI/action thủ công; không thêm `android` vào scope.

## Quyền riêng tư và khôi phục

`QUERY_ALL_PACKAGES` dùng cho bộ chọn app/activity, là chức năng cốt lõi của bản
sideload GitHub. Danh sách chỉ ở RAM, không log/upload, không khai báo INTERNET.
Không đặt mục tiêu Google Play. Chỉ quét hồ sơ hiện tại. Export chứa định danh
app/activity đã chọn, nên kiểm tra trước khi chia sẻ.

Nút Tắt ngay và Quick Settings tile chốt disable trước khi ghi DataStore.
Không daemon, keylayout/partition/settings OEM write hoặc root autostart.
Gỡ APK không để lại hook chặn nút; file ghi âm đã xuất vào MediaStore được giữ.
Hành vi uninstall/kill/reboot **chưa được nghiệm thu trên máy thật**.

Source dùng Apache-2.0. Xem [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).
Firmware binaries và log cá nhân trong `private-evidence/` được Git ignore và
không được đưa lên GitHub.
