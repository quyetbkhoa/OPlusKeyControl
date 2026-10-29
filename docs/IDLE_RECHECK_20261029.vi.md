# Kiểm tra lại lỗi đèn pin sau khi để lâu — 29/10/2026

Đọc máy CPH2745 trực tiếp: backend connected/authorized, features=23,
oom_score_adj=-700, torchReady=true. Lần request gần nhất completed-TORCH_TOGGLE.
Trace 16 raw events không chứa lần thất bại người dùng báo. Không kết luận
nguyên nhân lần thất bại từ trạng thái khỏe mạnh hiện tại.

Exit-info còn ghi nhận ColorOS o-kill(35) lúc 01:04 và 03:09 ngày 29/10;
o-kill(2003/4010) cũng có trong lịch sử ngày 26/10. Module Magisk
power_torch_controller có file disable và không thấy daemon tương ứng chạy.

Các thay đổi lần này:

- VectorAuthorization trước đây dừng vòng phục hồi sau 6 lần watcher không
  khởi động được. Tiếp tục thử khi scope còn hoạt động, backoff tối đa 30 giây.
  Khi watcher khỏe, vẫn chờ inotify, không polling. Đây là lỗi trong mã đã
  xác định, chưa chứng minh là nguyên nhân lần nhấn vật lý bị bỏ lỡ.
- Chỉ retry CameraAccessException CAMERA_DISCONNECTED/CAMERA_ERROR tối đa
  3 attempts, nghỉ 150/300 ms. Giữ nguyên trạng thái đèn đích suốt retry;
  không tính lại toggle. Không retry permission, camera-in-use hoặc max-cameras.
- Ghi exception action vào logcat, giữ 64 mốc xử lý Binder trong RAM để dumpsys
  không chỉ cho thấy kết quả cuối cùng. Không ghi package/activity đích.

Giới hạn: retry camera không giải quyết lần DOWN đến khi app chưa nối lại,
torch readiness đang bị thu hồi, hoặc camera đang thực sự bị chiếm dụng.
Chưa bắt được lỗi vật lý sau deep idle. Không dùng input giả để tuyên bố hết lỗi.

Người dùng xác nhận không rung/không sáng ở 1–2 lần đầu, sau đó bình thường;
cho phép soft reboot hoặc reboot trong phiên. Kiểm tra tiếp thấy backend hook
cũng bỏ retry sau 6 lần bind thất bại. Đã đổi sang RecoveryBackoff tiếp tục
thử với khoảng nghỉ tối đa 30 giây cho đến khi phục hồi/dispose. Khi kết nối
khỏe không đặt retry. Vẫn kiểm tra owner user, force-stop và quyền như trước.
Test 10.000 lần retry bảo đảm không hết lượt hoặc tràn counter, reset sau
phục hồi. Trace mới idle-recovery-v2 thêm peer/revision/user trên từng sự kiện.

Cài thay APK vẫn cần nạp lại module
và restart Android vì receiver PACKAGE_REPLACED của hook đang chạy sẽ dispose.

Bằng chứng đọc máy: private-evidence/idle-20261029/ (không commit).

Sau cài/soft reboot, trace mới bắt đúng lần người dùng báo: DOWN owned=false,
peer=true, rev=0; UP rev=5. Bốn lần sau completed-TORCH_TOGGLE. Người dùng
xác nhận bấm khoảng 30 giây sau khởi động; không thể coi lần đầu thất bại
là đã được bản retry sửa hết. Bổ sung wake lock ngắn 2,5 giây tại bind và
onServiceConnected để quá trình trao cấu hình không phải chờ lần đánh thức
tiếp theo; nghe thêm BOOT_COMPLETED/USER_UNLOCKED để nối ngay sau khởi động.
Không giữ CPU thức khi kết nối đã ổn định. Đây là xử lý thêm đường suspend
trong handshake, cần kiểm tra lại trên máy, không khẳng định đã chứng minh
deep idle là nguyên nhân duy nhất.

Xác minh bản cuối: 211 tests đạt, assemble/lint đạt (0 lỗi, 124 cảnh báo),
audit scope/build flags/DEX và apksigner verify đạt. Đã cài, reload riêng module,
soft restart Zygote. Kernel boot ID giữ nguyên; system_server đổi PID.
Sau khởi tạo, connected/authorized/torchReady=true, features=23,
lastRequest=none và runtimeTrace=idle-recovery-v2: nhận cấu hình trước khi
có lần bấm tiếp theo. Không mở Activity để khởi động dịch vụ.
Kill đúng watcher PID 6044 -> watcher mới 17184, app vẫn 4080; quyền/kết nối
tự phục hồi. Kill app PID 4080 -> app mới 17937 tự phục hồi, lastRequest=none.
Cấu hình giữ nguyên SHA-256
1fe355875517ee0e6fbe725b20331bc2cab7b8b1c8d7bb2477c58707b7c0e5e5.
Không có partial wake lock OPlusKeyControl active khi đọc lại sau test.
APK artifacts/OPlusKeyControl-idle-recheck-debug.apk SHA-256
85b29dc8c89c1ac4e031656f533ab57c7f04e551460faf6eb722908760332dd0.
Vẫn cần nghiệm thu lần bấm vật lý đầu tiên sau chờ lâu; các kiểm tra phục hồi
tiến trình không thay thế kiểm tra đó.
