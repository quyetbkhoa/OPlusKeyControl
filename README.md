# OPlusKeyControl

Ứng dụng Kotlin/Compose tùy biến nút trên OnePlus 15, có giao diện tiếng Việt
và tiếng Anh. Bản runtime thử nghiệm dùng module Vector/libxposed để nhận phím
trong `system_server`, rồi gửi tác vụ sang app qua Binder xác thực UID.

[English](README.en.md) · [Changelog](CHANGELOG.md) ·
[Release v1.0.2](docs/releases/v1.0.2.md) · [Khôi phục](docs/RECOVERY.md)

## Bản hiện tại

- Gán riêng nhấn đơn, nhấn đúp và giữ nút Plus; cấu hình đèn pin, mở app/Activity,
  camera và ghi âm MP3 tùy điều kiện sẵn sàng/quyền.
- Candidate nâng cao có Power nhấn đúp, Plus/Volume theo hai thứ tự, Plus-first
  Plus/Power và tổ hợp Power/Volume qua callback gốc của ROM.
- Giao diện Material 3 nhóm mapping theo phím, bộ chọn app/Activity, tiện ích,
  Inspector và tùy chọn rung xác nhận. Không còn lịch theo khung giờ.
- Rung nhận diện giữ phím có thể phát ngay khi đủ ngưỡng, không cần nhả phím.
- Tự kết nối lại khi tiến trình app hoặc binding bị mất, với số lần thử giới hạn.
  Không cần mở lại app để kích hoạt việc phục hồi sau process death.
- DataStore, sao lưu/khôi phục, kiểm tra xung đột, readiness theo từng mapping;
  khôi phục cấu hình mặc định tắt master.

## Tương thích và mức kiểm chứng

Runtime chỉ chấp nhận model `CPH2745` với đúng hai profile đã khảo sát:

| Android API | Firmware |
| --- | --- |
| 36 | `CPH2745_16.0.10.601(EX01)` |
| 37 | `CPH2745_17.0.0.102(EX01)` |

Đây là bản **runtime preview**. Bản mới đã cài trên ColorOS 17, giữ nguyên cấu
hình và vượt kiểm tra phục hồi sau khi kill tiến trình app. Điều này chưa chứng
minh mọi gesture vật lý, deep sleep, rung thực tế hoặc không có độ trễ.
Xem [bằng chứng phục hồi](docs/BACKEND_RECOVERY.vi.md),
[tương thích ColorOS 17](docs/COLOROS17_COMPATIBILITY.vi.md) và
[ghi chú phản hồi rung](docs/POWER_HAPTICS_UPDATE.vi.md).

Power nhấn đơn/giữ và tổ hợp hai nút Volume được bảo lưu. Volume độc lập chưa
có backend thay thế. Trợ năng chỉ quan sát Volume; Shizuku chưa có input adapter.
App không phát lại input, thay keylayout hay ghi thiết lập phím OEM.

## Build

JDK 17, Android SDK 36, Gradle wrapper 9.3.1, AGP 9.1.0. Đặt SDK qua
`ANDROID_HOME` hoặc `local.properties` không commit.

```powershell
# Bản runtime nâng cao tương ứng APK phát hành:
.\gradlew.bat -PopcRuntime=true -PopcAdvancedInputs=true :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/package_debug.py --runtime --advanced-inputs

# Bản thường: self scope, không hook hệ thống:
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/package_debug.py
```

APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`. CI kiểm tra bản thường,
runtime Plus và runtime nâng cao. Các tài liệu trong `docs/` có ngày cụ thể là
lịch sử kiểm tra; dùng changelog và release notes để xác định trạng thái hiện tại.

## Cài đặt và dữ liệu

Bản runtime cần framework tương thích modern libxposed API 101 và khả năng hook
`system_server`. Module dùng scope `system` và `dev.opluskeycontrol` ở user 0.
Cấp quyền cần thiết, mở app và kiểm tra backend trước khi bật mapping.
Cập nhật code hook cần restart Zygote hoặc reboot để được nạp.

APK preview dùng chữ ký debug của máy build. Asset v1.0.2 giữ nguyên binary đã
cài và kiểm tra: manifest vẫn là `0.1.0-dev` / versionCode `1`; số `v1.0.2` là tag
source/release. Bản tự build trên máy khác có thể không cài đè được vì khác signer.

App không có quyền INTERNET, analytics hoặc quảng cáo. QUERY_ALL_PACKAGES phục
vụ bộ chọn app/Activity. Ghi âm dùng microphone FGS có thông báo Stop, lưu ở
`Music/OPlusKeyControl` trên Android 10+. Không đưa keystore, dữ liệu cấu hình
cá nhân, firmware, log riêng tư hoặc bản nháp system-only vào repo/release.

Apache-2.0; xem [third-party notices](THIRD_PARTY_NOTICES.md).
