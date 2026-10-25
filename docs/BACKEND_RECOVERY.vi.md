# Phục hồi backend sau khi hệ thống kill app

Ngày 2026-10-25, người dùng báo bấm bật đèn pin đôi lúc không nhận,
phải mở app hoặc bấm lại nhiều lần.

## Bằng chứng

Đọc trực tiếp CPH2745 / CPH2745_17.0.0.102(EX01) qua ADB:
ApplicationExitInfo ghi nhận nhiều lần hệ thống kill dev.opluskeycontrol,
gần nhất 08:35:51 ngày 25/10, reason OTHER KILLS BY SYSTEM,
description o-kill(4010). Không suy diễn ý nghĩa nội bộ của mã OEM này.
Tại thời điểm kiểm tra, BackendKeeperService có kết nối từ UID 1000,
quyền CAMERA đã được cấp và scope module là system/0 cùng app/0.
Snapshot lưu ở private-evidence/backend-recovery/.

Mã cũ chỉ drop kết nối khi Binder chết, onServiceDisconnected hoặc
onBindingDied; không lên lịch bind lại. drop cũng unbind, nên không còn
trông chờ Android tự phục hồi binding. Mở Activity gửi READY; phím Plus
khi chưa có peer yêu cầu connect, giải thích khả năng phải bấm lại.
Đây là lỗi xác định được trong mã, phù hợp lịch sử kill; chưa tái hiện
đúng lần bấm thất bại của người dùng.

## Thay đổi

RootPolicyBackend tự thử kết nối lại khi mất tiến trình/binding, lỗi HELLO
hoặc kênh gửi action. Dùng cửa sổ thử lại hiện có (tối đa 4 lần thử lại,
cách nhau 1 giây); reset ngân sách khi nhận cấu hình hợp lệ.
Callback mất kết nối được xử lý trên worker, kiểm tra đúng ServiceConnection
để callback cũ không làm rơi kết nối mới. Không phát lại action đã thất bại.
Vẫn tôn trọng force-stop, user đang hoạt động, cấu hình tắt và quyền module.

## Xác minh

Build runtime + advanced inputs thành công; 182 JVM tests hiện có không lỗi.
Audit DEX/scope/build flags và chữ ký APK v2 đạt.
Các JVM tests chưa kiểm tra vòng đời Binder thực tế trên điện thoại.
APK: artifacts/OPlusKeyControl-backend-recovery-debug.apk
SHA-256: 4293a0d2dedc234ff9ed9757e89763ffd5910c55cc4744ec1251f253689b3928

## Cài đặt và kiểm tra thiết bị

Đã cài ngày 2026-10-25 theo yêu cầu người dùng. APK trên máy có SHA-256
khớp bản trên. Đã soft restart Zygote: system_server đổi PID 3457 -> 12610,
kernel boot ID giữ nguyên. Sau khởi động, mở app một lần để khởi tạo;
BackendKeeperService nối lại từ UID 1000.

Đã kiểm tra phục hồi bằng SIGKILL đúng tiến trình dev.opluskeycontrol,
sau khi xác nhận không có RecorderService đang chạy. Tiến trình đổi PID
23558 -> 30208 và service tự nối lại từ UID 1000 (hasBound=true),
không mở lại Activity hoặc giả lập phím để kích hoạt phục hồi.
Cấu hình DataStore trước cài, sau cài và sau kiểm tra phục hồi giống từng byte.
Snapshot và APK cũ lưu trong private-evidence/backend-recovery/install-20261025/.

Kiểm tra này chứng minh phục hồi sau khi tiến trình app chết; chưa chứng minh
mọi lần bấm vật lý đều nhận hoặc không có độ trễ. Cần người dùng thử đèn pin
bằng nút thật khi app ở nền, nhất là sau thời gian chờ dài.
