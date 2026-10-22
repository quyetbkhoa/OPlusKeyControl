# Tự kiểm tra OPlusKeyControl — 2026-10-22

## Bản cập nhật hiện tại

Xem [kết quả sửa MP3/UI](UX_MP3_UPDATE.vi.md). Người dùng đã xác nhận bản runtime
trước hoạt động. Bản mới cho phép từng gesture Plus độc lập, không cần đủ ba rule.
Sau reboot, bật công tắc chính rồi thử nhấn đơn với riêng rule đèn pin nếu muốn;
không cần bật rule ghi âm hoặc mở app để đủ coverage. Giữ nguyên các rule đã tắt.
Gesture chưa gán không chạy gì trong chuỗi phím app đã chiếm. Power chưa hỗ trợ.
Các yêu cầu đủ ba gesture và lịch DOUBLE trong những mục bên dưới là lịch sử.

MP3: người dùng chủ động thử ghi âm và dừng, kiểm tra file mới trong
Music/OPlusKeyControl bằng trình phát; bản ghi M4A cũ vẫn được giữ và phát đúng MIME.
Kiểm tra khi màn hình bật/tắt; quyền pin Android và thiết lập ColorOS cần phân biệt.
Không coi test tín hiệu tổng hợp hoặc cài APK là chứng minh microphone nền hoạt động.

## Kiểm tra sau reboot bản Binder — 07:01

TCP kết nối. Ban đầu UI chưa kết nối; gửi lại BACKEND_READY bằng ADB root
(không sửa cấu hình) đã tạo `RUNTIME_BINDER_CONNECTED` từ system_server PID 4442
lúc 07:01:07. Service Binder được bind bởi android/UID 1000 và UI chuyển sang
“Backend Plus đã kết nối”. Kết nối Binder PASS sau yêu cầu sẵn sàng lại;
kết nối tự động ngay sau boot chưa được xác nhận ổn định.

Công tắc chính và cả ba rule hiện ON. Rule DOUBLE vẫn chỉ 22:00–06:00,
ngoài lịch tại thời điểm kiểm tra 07:01 nên toàn bộ Plus pass-through theo thiết kế.
Chưa thử nhấn Plus/chạy action sau kết nối; chưa nghiệm thu remap. Không sửa rule,
không bật đèn pin hoặc ghi âm trong lượt kiểm tra này. Để thử thủ công ban ngày,
người dùng cần đặt DOUBLE mọi lúc hoặc thêm lịch phù hợp; nên dùng ba action
đèn pin cho thử nghiệm ban đầu, tránh vô tình kích hoạt ghi âm khi giữ lâu.

## Sửa nguyên nhân backend không kết nối: Binder

Đã xác nhận hook chạy và receiver BACKEND_READY tồn tại trong system_server.
Khi yêu cầu kết nối lại, log trực tiếp báo SELinux `avc: denied { connectto }`:
nguồn `system_server`, đích socket app `untrusted_app`. Đây là nguyên nhân đã
quan sát được, không phải suy luận từ việc thiếu log khởi tạo.

Mã mới bỏ kênh socket, dùng Binder qua service riêng không export. App chỉ nhận
UID 1000; callback phía hệ thống chỉ nhận UID thật của app. Cấu hình giới hạn
256 KiB, mất Binder/hủy phiên sẽ trả phím về hệ thống. Không đổi SELinux,
không thêm module, không mở khóa Power/Volume/chord. Các kiểm thử payload bổ sung
không thay thế kiểm tra Binder trên điện thoại. Chưa nghiệm thu remap bản Binder.

Sau khi cài cần tự reboot một lần để nạp mã hệ thống mới, rồi kiểm tra dòng
Backend Plus. Giữ nguyên rule cũ. Rule nhấn đúp hiện chỉ 22:00–06:00, nên ngoài
giờ này vẫn không đủ ba gesture để bản thử nghiệm chiếm Plus. Chỉ đổi lịch hoặc
hành động sau khi đã xác nhận backend kết nối; không tự chạy thử ghi âm.

Các mục bên dưới ghi lại các phiên bản trước để phân biệt bằng chứng.

Bản Binder đã cài, hash trên máy khớp:
`4e65ee5fac465278ea62a098a552459b68b3459645ac5c7524697d3084c35d91`.
Dữ liệu cấu hình trước/sau cài giống hệt từng byte. UI mở được; 105 kiểm thử đạt,
lint 0 lỗi/22 cảnh báo. Đang chờ reboot thủ công để nạp Binder; chưa xác nhận kết nối.

## Cập nhật sửa công tắc rule

Đã cài bản sửa, SHA-256 `2645a14e7c913d438f8b2fdc9213c892a08e5149438bc0104ef4b9aa9625d1b5`.
UI trên máy xác nhận công tắc rule Plus một lần/giữ lâu đã enabled dù backend
offline. Chưa thử chuyển ON vì công tắc chính đã được người dùng bật; không
tự kích hoạt rule ghi âm/đèn pin. Không thay đổi các rule của người dùng.

Trước đây UI buộc backend kết nối mới cho bật rule. Bản sửa cho phép chuẩn bị
rule Plus trong Root khi offline, đồng thời hiển thị rõ chưa remap. Quyền runtime
và yêu cầu đủ ba gesture vẫn được kiểm tra độc lập trong backend. Power/Volume/
chord không được mở khóa bởi thay đổi này.

Đã sửa thứ tự khởi tạo: đăng ký receiver trước khi truy vấn ActivityManager,
thêm retry giới hạn khi app-ready trùng với kết nối đang lỗi, kéo dài cửa sổ
bootstrap hữu hạn và ghi lỗi cụ thể tại entry/profile/hook/context/socket.
Chưa xác định nguyên nhân sâu của phiên backend cũ: thiếu log không chứng minh
hook không nạp. Cần reboot thủ công rồi kiểm tra bản mới; chưa nghiệm thu kết nối.
Không tự reboot hoặc đổi scope/module. Build/tests/lint và kiểm tra chữ ký đạt.

Phần dưới là bằng chứng lần cài trước và trình tự kiểm tra đầy đủ.

Đã cài APK runtime 0.1.0-dev thành công qua TCP và gửi lệnh mở app. TCP bị
ngắt trước khi lấy được UI, nên chưa xác nhận màn hình sau cài. Chưa bật hook,
chưa reboot, chưa thử remap. Scope Vector rỗng sau cài; module được xác nhận
tắt ngay trước cài. PowerKeyTorch không bị thay đổi.

101 kiểm thử JVM đạt; build và chữ ký APK đạt. Lint bản Android gần nhất:
0 lỗi, 22 cảnh báo. Đây là bằng chứng phần mềm, chưa chứng minh remap trên máy.

## Trình tự test thủ công

1. Mở app, kiểm tra UI và giữ lại các rule cũ. Đọc [khôi phục](RECOVERY.md)
   trước khi tự kích hoạt hook thử nghiệm trong system_server (có rủi ro bootloop).
2. Trong Vector, bật riêng OPlusKeyControl, chọn scope `system` (hệ thống/framework
   Android) và OPlusKeyControl. Tự reboot toàn bộ điện thoại để nạp hook.
3. Mở app, chọn Root, bật công tắc chính và cấp quyền su khi được hỏi.
   Phải thấy backend kết nối trước khi thử remap. Khi chưa có rule, Plus phải
   giữ hành vi mặc định. Nếu không kết nối, dừng tại đây và báo lại trạng thái.
4. Tạo đủ ba rule Plus: bấm một lần, bấm hai lần, giữ lâu. Bật cả ba, mọi ngày,
   cả ngày, cùng hành động đèn pin để thử đầu tiên; cấp Camera nếu được hỏi.
   Tránh rule cũ trùng lịch/gesture. Chỉ cấu hình một hoặc hai gesture thì bản
   hiện tại nhường toàn bộ Plus cho hệ thống, đây là giới hạn có chủ đích.
5. Thử từng gesture, quan sát một hành động cho mỗi lần thao tác. Tắt công tắc
   chính rồi thử Plus: hành vi mặc định phải trở lại. Sau đó kiểm tra tắt riêng
   module Vector và buộc dừng app có trả lại hành vi mặc định hay không.
6. Chỉ sau khi phần trên đạt mới thử lần lượt mở ứng dụng, Activity, camera,
   ghi âm theo lựa chọn của bạn. Camera/Activity yêu cầu bước thử và xác nhận
   thành công trong UI trước khi rule sẵn sàng. Khi thử ghi âm, dùng thông báo
   hoặc nút dừng trong app nếu gesture dừng không hoạt động. Không cần gửi ảnh/audio.

## Giới hạn hiện tại

- Plus single/double/long đã có mã runtime nhưng chưa kiểm chứng remap thật.
- Power, Volume, tổ hợp nút và remap Shizuku chưa được nghiệm thu; không thử SOS.
- Chụp đúng một ảnh bằng camera stock, ghi âm từ nền/màn hình khóa, Activity nội bộ,
  thu hồi quyền khi tắt scope/force-stop và vòng đời helper đều cần test thực tế.
- Chưa có số đo pin/deep sleep hay bảo đảm dọn helper sau gỡ app.

Nếu lỗi: dùng **Tắt ngay**, dừng ghi âm qua thông báo nếu cần, tắt riêng module
OPlusKeyControl trong Vector. Có thể cài đè APK thường
`artifacts/OPlusKeyControl-0.1.0-dev-debug.apk` để quay về bản không hook hệ thống,
giữ dữ liệu ứng dụng. Chi tiết và lệnh khôi phục nằm trong RECOVERY.md.

Khi báo kết quả, chỉ cần: backend có kết nối không; single/double/long làm gì;
tắt công tắc có trả Plus về mặc định không; lỗi/ảnh màn hình UI nếu có.
