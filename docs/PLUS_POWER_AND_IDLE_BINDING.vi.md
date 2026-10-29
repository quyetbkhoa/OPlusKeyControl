# Plus + nguồn và giữ kết nối sau chờ lâu — 25/10/2026

Người dùng xác nhận nguồn nhấn ba lần đã hoạt động, nhưng Plus + nguồn chưa
hoạt động và nhấn đơn đèn pin sau chờ lâu vẫn đôi khi cần bấm lại. Người dùng
yêu cầu bỏ nguồn + âm lượng, chỉ giữ ba tổ hợp có Plus.

## Thay đổi

- Xóa đăng ký hook nguồn + âm lượng khỏi RootPolicyBackend, không quảng bá
  POWER_VOLUME. UI chỉ liệt kê Plus + tăng âm lượng, Plus + giảm âm lượng,
  Plus + nguồn. DataStore/import loại mapping nguồn + âm lượng cũ; giữ các
  mapping và thiết lập khác.
- Nhận Plus + nguồn cả hai thứ tự. Nếu nguồn DOWN đã được OEM nhận trước,
  UP/repeat cũng tiếp tục thuộc OEM; không chiếm nửa cặp hoặc phát lại input.
  Vì vậy nguồn trước vẫn có thể thực hiện thao tác màn hình gốc.
- GestureEngine tách thời gian phần cứng khỏi thời gian quan sát của timer.
  Worker timer chạy trước một sự kiện đang vận chuyển không còn xóa trạng thái
  giữ Plus. Timestamp phần cứng thật sự lùi vẫn hủy nhận diện.
- Binding hệ thống dùng BIND_AUTO_CREATE | BIND_IMPORTANT (0x41), giữ tiến trình
  thực thi tác vụ ở mức quan trọng phù hợp với client system_server. Không thêm
  foreground service, timer định kỳ hoặc wake lock khi không thao tác.

Tham chiếu hành vi binding:
https://developer.android.com/reference/android/content/Context#BIND_IMPORTANT
https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/OomAdjuster.java

## Bằng chứng và triển khai

Lịch sử thiết bị ghi nhận ColorOS kill app nhiều lần sau các bản trước, ví dụ
16:22:36, 16:23:31 và 16:24:21 với o-kill(2003). Baseline oom_score_adj=100.
Ngay trước cài, tiến trình app cũng không có PID khi đọc preflight; cấu hình
đã được sao lưu trước đó. Không coi việc nối lại thành công là hết lỗi chờ lâu.

203 JVM tests đạt, gồm hồi quy nguồn đến trễ sau timer, nguồn trước/Plus trước,
timestamp thật sự lùi và chuyển đổi mapping đã bỏ. Lint 0 lỗi / 124 cảnh báo;
audit DEX/scope và chữ ký APK đạt. UI ba tổ hợp đã render/inspect trên emulator.

APK đã cài: artifacts/OPlusKeyControl-plus-power-idle-debug.apk.
SHA-256: 5ee010cdd95e6aad60155ea33babf8a3c1f11323065acc76d4ed59e374529a03.
Readback APK trên máy khớp artifact.

Lần soft restart đầu vẫn nạp hook cũ: features=31, binding flags=0x1,
oom_score_adj=100 dù APK trên đĩa đã mới. Do đó chưa chấp nhận kết quả.
Đã disable rồi enable riêng dev.opluskeycontrol qua Vector CLI, xác nhận
scope trước/sau giống nhau (system/0, self/0), rồi soft restart Zygote lần nữa.
Không thay trạng thái module khác.

Readback cuối: features=23 (không có POWER_VOLUME), binding flags=0x41,
oom_score_adj=-700, connected=true, authorized=true, torchReady=true.
Chỉ một mapping nguồn + tăng âm lượng cũ bị loại; mọi mapping/setting khác
giữ nguyên theo đối chiếu JSON. Kernel boot ID giữ nguyên.

Bằng chứng riêng tư: private-evidence/plus-power-idle-20261025/.
Đã yêu cầu người dùng kiểm tra Plus + nguồn và nhấn đơn sau thời gian chờ;
chưa có xác nhận hết lỗi chờ lâu tại thời điểm ghi tài liệu.
