# MP3, gesture độc lập và giao diện — 2026-10-22

## Đã triển khai và cài

- Ghi âm PCM16 mono 44,1 kHz → MP3 128 kb/s bằng Jump3r 1.0.5. Mã hóa/ghi file trên worker, bộ đệm giới hạn; thông báo Dừng và tự dừng được giữ. File MediaStore mới dùng `.mp3` / `audio/mpeg`. Bản ghi cũ không bị đổi/xóa; lịch sử mở bằng MIME thực tế.
- Plus cho phép riêng SINGLE, DOUBLE hoặc LONG khi rule khớp lịch và action sẵn sàng. Nhận diện cả ba để double/hold chưa gán không vô tình chạy single. Nếu một chuỗi đã bị app chiếm, gesture chưa gán không làm gì; không thể trả sự kiện đã nuốt về OEM. Nếu không rule nào sẵn sàng, phím tiếp tục về OEM.
- Danh sách Activity hiển thị các mục exported và (ở Root) nội bộ mà không cần mở Advanced. Các mục bị tắt/bảo vệ nằm trong Advanced, không chọn được. Ưu tiên launcher/exported; tên dự phòng khi thiếu nhãn. Tìm theo app/package/class; không bắt nhập component. Việc chọn không tự mở Activity; rule Activity vẫn cần bước thử mở thành công để bật.
- Giảm các đoạn cảnh báo lặp; giải thích dài đưa vào nút thông tin. Trạng thái chế độ quyền tách khỏi quyền và backend: đang kiểm tra Root/scope, xác minh không thành công, đã được cấp nhưng backend chưa nối, đã nối Plus. Shizuku có callback cấp/từ chối, trạng thái đang chờ. Chọn lại chế độ hiện tại không tự tắt remap.
- Màn hình pin phản ánh `isIgnoringBatteryOptimizations`, mở yêu cầu miễn tối ưu hoặc trang pin app. Không đồng nhất miễn tối ưu Android với mọi hạn chế nền riêng của ColorOS. Chưa chứng minh loại bỏ độ trễ màn hình tắt.
- Power DOUBLE: đã xác định không bật được vì chưa có backend. UI nêu rõ lý do và chỉ cho lưu bản nháp. **Chưa triển khai remap Power**, không chỉ mở khóa công tắc để tạo cảm giác đã hoạt động. Đã đọc offline `PhoneWindowManager$PowerKeyRule.onMultiPress(JII)` trong bản framework có sẵn: chuyển vào OEM `IInputExt.powerPress`; đây chỉ là đầu mối nghiên cứu, chưa xác minh pass-through/SOS và chưa gắn hook. Bỏ qua module powerkey cũ theo yêu cầu.

## Bằng chứng

- Build runtime + unit tests: 110 test, không thất bại. Thêm case riêng từng gesture Plus, gesture không gán, action chưa sẵn sàng và codec MP3.
- Build/lint sau chỉnh giao diện cuối: thành công; lint 0 lỗi / 32 cảnh báo (gồm API ẩn đã có, dependency versions, unused resources và cảnh báo Play policy cho xin miễn tối ưu pin; đây là app sideload).
- Tín hiệu tổng hợp 440 Hz, một giây PCM được mã hóa thành MP3. `ffprobe`: codec mp3, 44100 Hz, mono, 1.044875 giây, 16718 byte. `ffmpeg -v error ... -f null -` giải mã không báo lỗi. Không sử dụng microphone để thử.
- APK cuối: `artifacts/OPlusKeyControl-0.1.0-runtime-debug.apk`, SHA-256 `bd1086c60a551edcbef2f82114c5d2da8683dd57d0dc421e64e56f7967b8a45f`. Cài `adb install -r` thành công; hash APK trên điện thoại khớp.
- Dữ liệu `rules.preferences_pb` trước/sau toàn bộ cài và kiểm tra UI giống từng byte. Công tắc chính đang OFF theo cấu hình giữ lại; không tự bật rule, đổi lịch hoặc ghi âm/chụp ảnh/thử Power.
- UI máy thật: đã xem Tổng quan, quyền, trạng thái pin đã miễn tối ưu Android, danh sách Activity thật (launcher và nội bộ). Hủy draft sau khi xem, không lưu thay đổi.
- Sau lần reboot đầu, UI xác nhận Plus đã kết nối. Sau đó có bản chỉnh picker cuối, cập nhật APK làm backend cũ tự dispose đúng thiết kế. Người dùng báo đã reboot thêm lần nữa; kết nối TCP cuối cùng bị từ chối nên **chưa xác nhận backend sau lần reboot cuối**. Không dùng trạng thái nối của APK trước làm bằng chứng bản cuối hiện đang nối.
- Thử mở emulator cũ không thành công vì system image thiếu kernel; không tải mới hoặc coi đó là test UI. Bằng chứng UI ở trên lấy từ điện thoại thật.

## Còn cần test thật

Kết nối TCP lại, kiểm tra backend, người dùng bật công tắc chính nếu muốn test.
Thử Plus với từng gesture độc lập; ghi âm MP3 và dừng/phát lại; so sánh độ trễ
khi bật/tắt màn hình. MP3 tổng hợp và unit test không thay thế các phép thử này.
Power, Volume, chord và Shizuku remap vẫn chưa hỗ trợ.

## Quyền thực hiện tiếp theo

Người dùng đã yêu cầu: lần sau khi cần nạp lại Vector sau cập nhật APK, assistant
tự thực hiện **soft reboot**, không hỏi lại mỗi lần. Đây là quyền mới do người dùng
cấp trong cuộc trao đổi này. Kiểm tra phương thức soft reboot được hỗ trợ trước
khi chạy; không tự thay bằng hard reboot hoặc đổi module khác. Lượt này người dùng
đã tự reboot; assistant không chạy thêm reboot. TCP có thể cần người dùng kết nối lại.
