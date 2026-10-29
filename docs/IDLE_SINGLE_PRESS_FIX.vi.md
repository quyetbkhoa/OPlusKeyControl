# Sửa nhấn đơn sau thời gian chờ — 25/10/2026

Người dùng báo để lâu không dùng rồi nhấn Plus bật đèn pin có thể phải bấm
ba lần; lúc đang sử dụng liên tục thì bình thường. Bản sửa phục hồi v1.0.2
trước đó chưa giải quyết đủ trường hợp này.

## Bằng chứng và nguyên nhân trong mã

Đọc trực tiếp CPH2745 / CPH2745_17.0.0.102(EX01), cấu hình Plus single là
TORCH_TOGGLE, double=300 ms, long=600 ms. Lịch sử thoát tiến trình ghi nhận
ColorOS kill app hai lần khoảng 09:56 với o-kill(2003).
Chưa bắt được chính lần nhấn vật lý thất bại sau thời gian chờ dài.

Các lỗ hổng xác định từ mã:

- PolicyBridge gửi CONFIG với revision mới kể cả cấu hình không đổi.
  PlusPolicySession.configure thay recognizer và bỏ single đang chờ; backend
  cũng xóa deadline. Refresh/readiness có thể làm mất một nhấn đã nhận.
- Timer Handler dùng uptime; deep sleep làm hoãn timer. Code trước không giữ
  CPU thức qua cửa sổ chờ double và gửi tác vụ. Đây là đường có thể trì hoãn
  single khi tắt màn hình, chưa phải trace xác nhận một lần lỗi cụ thể.
- Khi app chết, backend unbind rồi thử lại chỉ trong cửa sổ vài giây, dễ hết
  trước khi chính sách OEM cho app khởi động lại.
- Nếu root ScopeWatchMain chết nhưng app còn sống, job theo dõi kết thúc và
  quyền bị thu hồi mà không có vòng phục hồi trực tiếp trong watcher.

## Bản sửa

- Không phát snapshot giống nhau; buộc gửi snapshot khi có peer mới.
- Refresh lease/readiness không reset gesture khi config/timing/proof không đổi.
  Giữ và lên lịch lại deadline; tắt config/thu hồi readiness vẫn chặn tác vụ.
- Giữ AUTO_CREATE binding sau process death/service disconnect để Android
  phục hồi. Binding chết hẳn hoặc timeout vẫn unbind/rebind; retry có backoff,
  thêm cơ hội phục hồi khi màn hình bật. Callback chết của peer cũ bị bỏ qua.
- Root watcher tự khởi động lại sau khi chết; vẫn kiểm tra lại quyền/scope.
- Giữ CPU thức ngắn quanh recognition/handoff trên worker, tối đa 2,5 giây
  mỗi acquire. Không gọi PowerManager từ input hook. Action app có timeout
  tối đa 15 giây và release trong finally, thường nhả ngay sau tác vụ.
  Không giữ wake lock trong thời gian chờ bình thường, không đánh thức màn hình.
- Thêm Service.dump để đọc authorization, revision và kết quả action gần nhất
  bằng dumpsys activity service, không cần mở UI hoặc xuất package/Activity đích.

Tham chiếu: https://developer.android.com/reference/android/os/Handler
và https://developer.android.com/reference/android/os/PowerManager.WakeLock

## Xác minh

189 JVM tests đạt, gồm 7 regression tests mới về refresh/single/thu hồi/reconnect.
Lint 0 lỗi, 118 cảnh báo; DEX/scope/build-flag audit và chữ ký APK v2 đạt.

APK artifacts/OPlusKeyControl-idle-single-fix-debug.apk đã cài đè và soft reboot.
SHA-256 ed57006e2e049049c50c9fe34df0d74e8ca2139d8ff355ea6fe42679801a4cf1.
APK trên thiết bị khớp; kernel boot ID giữ nguyên; system_server PID đổi
12610 -> 20632. Sau khởi tạo, connected=true, authorized=true, torchReady=true.

Kiểm tra thực tế độc lập:

- Kill đúng ScopeWatchMain: watcher đổi PID, app giữ PID; quyền và kết nối tự
  phục hồi sau khoảng 4,81 giây, không mở lại Activity.
- Kill đúng tiến trình app, sau khi kiểm tra không có RecorderService: app
  đổi PID, quyền và kết nối tự phục hồi sau khoảng 4,52 giây.
- Cấu hình ngay sau cài giống trước cài từng byte. Snapshot sau các test có
  một thay đổi ở trường enabled của mapping Plus + Volume Up; không thêm/xóa
  mapping hoặc đổi action/timing. Không tự ghi đè snapshot đang dùng để hoàn tác.
- Không còn wake lock OPlusKeyControl trong danh sách active sau tác vụ/test.

Snapshot/APK cũ: private-evidence/single-press-20261025/ (không commit dữ liệu).
Chưa nghiệm thu nhấn đơn vật lý sau chờ lâu/deep sleep; không dùng input giả
làm bằng chứng. Bản mới hiện được cài local, chưa thay asset GitHub v1.0.2.
