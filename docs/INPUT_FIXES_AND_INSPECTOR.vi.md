# Tổ hợp, mở camera và kiểm tra màn hình — 22/10/2026

## Thay đổi

- Plus + Volume± không còn tranh với Plus nhấn giữ ở ngưỡng 600 ms. Khi có
  rule tổ hợp sẵn sàng, giữ Plus làm modifier tới lúc thả. Nếu không bấm partner,
  LONG được giải quyết lúc thả. Không có rule tổ hợp thì LONG vẫn chạy tại ngưỡng.
  Volume trước Plus không được thu hồi: cần giữ Plus trước.
- Thêm Plus + Power theo cùng cơ chế Plus trước. Chỉ cặp partner khớp rule được
  chiếm; Power riêng vẫn theo đường hệ thống.
- Thêm Power + Volume± qua hai callback execute native của đúng firmware:
  PhoneWindowManager$6 (Power/VolDown) và $9 (Power/VolUp). Xác nhận cặp keyCode
  từ field mKeyCode1/2 trước khi thay callback. Chỉ count vật lý cách nhau không
  quá 150 ms, đúng rule/lịch/lease/readiness và queue thành công mới thay action.
  Hai thứ tự đều được chấp nhận. Không consume/replay raw Power/Volume ở đường
  này; nhận diện và hủy tổ hợp vẫn do OEM. preCondition VolUp chỉ được mở rộng
  khi có rule sẵn sàng. Đây là hook thử nghiệm, chưa nghiệm thu SOS/hard reset.
- Binder v4 thêm capability Power/Volume. Backend chưa có hook không bị UI báo
  là sẵn sàng chỉ vì cấu hình được. Hai nút Volume cùng nhau vẫn bảo lưu.
- Camera chuyển sang mở camera gốc, bỏ chờ CameraService/MediaStore và phát
  shutter. Giữ enum STOCK_CAMERA_CAPTURE để đọc rule cũ; hành vi và nhãn UI mới
  là chỉ mở camera. Không sửa file cấu hình chỉ để đổi loại action.
- Root Activity launcher và encoder MP3 không thay đổi.

## Kiểm tra Activity / UI

Cài đặt → Kiểm tra Activity / UI → mở cửa sổ kiểm tra. Cần bật Trợ năng của
OPlusKeyControl; đây là quyền đọc nội dung màn hình mới được khai báo. Không tự
bật bằng Settings.Secure. Overlay dùng TYPE_ACCESSIBILITY_OVERLAY, không cần
quyền vẽ trên ứng dụng khác. Cây UI là cây Accessibility mà Android cung cấp,
không phải mọi View nội bộ; màn hình bảo vệ có thể không cung cấp cây.

Cửa sổ dark kéo được bằng tiêu đề, có Làm mới, Lưu TXT, Tạm dừng/Tiếp tục,
Thu gọn/Mở cây UI và Đóng. Đọc theo sự kiện, gom sự kiện 350 ms, không quét khi
đã đóng. Root mode đã kết nối dùng dumpsys activity để lấy Activity thật, riêng
với class của sự kiện (có thể chỉ là View); không coi View class là Activity.
Giới hạn 1.500 node, sâu 40, khoảng 220 KB và 1,5 giây duyệt cây mỗi lượt.

Save lấy đúng bản cây đang hiển thị, UTF-8, ghi MediaStore Downloads với pending
transaction; lỗi thì xóa file pending. Android 10+ lưu ở Download/OPlusKeyControl,
Android 9 dùng Documents riêng của app. Không tự lưu, không upload hoặc log cây
UI. Ẩn text/contentDescription của node mật khẩu và các node con của nó.
Đóng overlay khôi phục chế độ không nhận sự kiện nội dung màn hình.

Nguồn API: [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo).

## Kiểm tra build

155 JVM tests đạt; lint 0 lỗi / 53 cảnh báo. Các regression mới kiểm tra giữ
Plus lâu vẫn tạo chord, LONG lúc thả không chạy trùng, partner Power, hủy chuỗi,
Power/Volume cả hai thứ tự, khoảng 150 ms, callback sai/giả/cũ/trùng, mất lease,
queue đầy, mật khẩu và phân biệt Activity foreground với task cũ.

APK: artifacts/OPlusKeyControl-power-combos-debug.apk
SHA-256: 72f4a8454725f61a45bff321447aba181d1b18b413d24a12ceb6fda625518ff5

Bản trước thay đổi: artifacts/OPlusKeyControl-power-combos-before-inspector-debug.apk.
Rollback UI/Plus ổn định: artifacts/OPlusKeyControl-dark-ui-debug.apk.
Cài APK không đủ chứng minh nút thật hoặc overlay hoạt động. Kết quả trên thiết
bị sẽ được ghi riêng sau khi được phép kích hoạt hook mới.

## Kết quả cài thiết bị

Đã được người dùng cho phép cài, kích hoạt hook mới, soft reboot và kiểm tra
Trợ năng. APK đầu tiên đã cài, cấu hình giữ nguyên, system_server đổi PID và
kernel boot ID giữ nguyên. TCP vẫn ở cổng 5555. UI Chẩn đoán báo features: 15,
nghĩa là Plus, Power double, Plus partner và native Power/Volume đã đăng ký.
Đây chưa phải nghiệm thu hành động bằng phím thật của các tổ hợp mới.

Người dùng tự bật Trợ năng. Đã thấy overlay trên launcher và Cài đặt. Người dùng
sau đó yêu cầu dừng test thêm tính năng này và chỉ sửa nút; chưa xác nhận lưu
TXT trên thiết bị. Không tạo file TXT thử hoặc bắt người dùng thử tiếp.
Nút overlay đã đổi từ nền xám/chữ trắng của theme hệ thống sang nền xanh tối,
viền xanh, chữ sáng, có pressed/disabled state và khoảng cách giữa các nút.

Bản sửa nút đã build/test/lint thành công, cài đè và soft reboot. Hash APK trên
máy khớp bản cuối ở trên; cấu hình tiếp tục giống từng byte. TCP kết nối lại.
Hook/action executor không thay đổi trong lượt sửa màu nút. Không thử SOS,
không ghi âm/chụp ảnh, không sửa rule hoặc module PowerKey bên ngoài.

## Plus + Volume nhận cả hai thứ tự

Đã bỏ giới hạn Plus trước cho hai tổ hợp Plus/Volume. Volume xuống trước được
quan sát theo deviceId/downTime; Plus xuống khi Volume vẫn giữ sẽ nhận cùng rule
canonical. Không replay hoặc inject sự kiện. Volume DOWN đã chuyển cho OEM nên
repeat/UP của đúng cặp đó cũng luôn chuyển cho OEM, kể cả sau khi mất kết nối.
Vì vậy âm lượng có thể thay đổi trước khi Plus được bấm; không thể thu hồi thay
đổi đã xảy ra. Volume dùng riêng không bị thêm độ chờ. Plus trước vẫn chỉ chiếm
cặp partner sau khi tổ hợp thực sự khớp.

Không chọn ngẫu nhiên khi cả hai Volume cùng giữ; UP/CANCEL, thiết bị lạ, mất
lease, đổi cấu hình, repeat và duplicate không được tạo thêm action. Sau chord
không chạy thêm Plus single/long. Thay đổi này chỉ dành cho Plus/Volume; Plus/Power
vẫn yêu cầu Plus trước, Power/Volume dùng cửa sổ native 150 ms cả hai thứ tự.

166 JVM tests đạt; lint 0 lỗi / 53 cảnh báo. Đã cài APK hash
bb110e5186355dbf7e6294c1aed5327e987bccabe6e8c0f8abca4b010d21f75b
và soft reboot theo quyền đã cho. Hash APK trên máy khớp, cấu hình giống từng
byte trước/sau cài, kernel boot ID giữ nguyên và system_server đổi PID. TCP vẫn
cổng 5555. Chưa có xác nhận nút thật cho chiều Volume trước trong lượt sửa này.
