# Candidate ColorOS 17 — 24/10/2026

## Nguyên nhân và thay đổi

Máy CPH2745 hiện chạy `CPH2745_17.0.0.102(EX01)`, API 37. APK đang cài
có SHA-256 `19e905e2e48b19adec5910eaaaafd9d6010f8f490563cbd92d0b59043523d177`.
App mở được, Root hoạt động, module Vector bật với scope system/0 và self/0,
nhưng không có BackendKeeperService được bind. Entry cũ trả về ngay nếu SDK
khác 36; backend cũng khóa đúng firmware ColorOS 16.

- Entry cho phép khởi tạo từ API 36; RuntimeProfile vẫn chỉ chấp nhận đúng hai
  cặp SDK/build đã khảo sát: API 36 / `CPH2745_16.0.10.601(EX01)` và
  API 37 / `CPH2745_17.0.0.102(EX01)`, model CPH2745.
- Giữ hook Plus tại `InputManagerService.interceptKeyBeforeQueueing(KeyEvent,int):int`.
  OEM mới vẫn xử lý action button keyCode 780; input gpio-keys vẫn có scanCode 735.
- Power double giữ `PhoneWindowManager$PowerKeyRule.onMultiPress(long,int,int)`.
  Trên firmware mới, `onKeyGesture(SingleKeyGestureEvent)` vẫn gọi method này.
  Các field `this$0`, `mShouldEarlyShortPressOnPower`, `MULTI_PRESS_TIMEOUT` còn tồn tại.
- Power/Volume chuyển từ package policy sang input:
  `input.KeyCombinationManager$TwoKeysCombinationRule`,
  `input.KeyGestureController$4` (25/26), `$7` (24/26).
  Đã đối chiếu constructor, execute và preCondition trong DEX. Vẫn kiểm tra
  superclass, return type và cặp mKeyCode1/2 khi thực thi.
- Profile ColorOS 16 giữ nguyên các callback cũ. Không thay rule, dữ liệu,
  master, quyền, cơ chế ghi âm hoặc camera. Probe quan sát riêng vẫn giữ guard cũ.

## Bằng chứng tĩnh

Framework được kéo chỉ đọc từ máy vào private-evidence/coloros17 (Git ignore),
không phân phối binary hoặc log riêng tư:

| File | SHA-256 |
|---|---|
| services.jar | ea7fd4ef674b744d3cd8125252e532d0c856374b380dd9a3f0f206a5d756ba32 |
| oplus-services.jar | cee3f1508aa0c7708395e992818d5a801a702b21dd654c7584718c48f1acb487 |
| framework.jar | 7fac5a3bbe455c22a768f61735b5a3049ee942d6bbc1c7b2b219b1ecc27e3e52 |

## Kiểm tra và triển khai

Build candidate với `-PopcRuntime=true -PopcAdvancedInputs=true`. Test profile
kiểm tra giữ hỗ trợ cũ, chấp nhận đúng firmware mới, từ chối sai SDK/model/OTA.
167 JVM test đạt, không lỗi/bỏ qua. Runtime assemble và lint đạt (0 lỗi,
84 cảnh báo). Bản thường assemble đạt; cả hai bản qua packaging/DEX audit,
trong đó bản thường vẫn có entry không chạy hook. Chữ ký APK được kiểm tra.

Candidate: `artifacts/OPlusKeyControl-coloros17-debug.apk`, SHA-256
`e760473be8c0c905526a85730f0da3ebfa66eb6e7e1cc3ddca8c29d67fc30f75`.
APK cũ khớp hash bản đang cài được giữ tại
`artifacts/OPlusKeyControl-before-coloros17-debug.apk` để khôi phục.

## Kết quả triển khai được cho phép

Người dùng đã cho phép cài đè và soft reboot. `adb install -r` thành công;
hash APK trên máy khớp candidate. Đã sao lưu rules.preferences_pb (1480 byte,
SHA-256 `9e9ce3479de00f222b55beef6b6e6739c13fd0fb95c37edd7da413297bbe16c5`).
Cấu hình trước cài, sau cài và sau restart giống từng byte.

Đã gọi `setprop ctl.restart zygote` đúng một lần. Kernel boot ID không đổi,
system_server đổi PID 4380 → 483, TCP kết nối lại được. Sau khi user 0 ở trạng
thái RUNNING_UNLOCKED, app mở được. BackendKeeperService được android/UID 1000
bind từ system_server mới. Không cần đổi scope hoặc gửi broadcast phục hồi.

Log lúc 04:05 ghi nhận các cặp Plus DOWN/UP `owned=true driverMatches=true`.
Ảnh UI cho thấy Plus single và long sẵn sàng. Rule Activity double hiện yêu
cầu thử hành động: ActionProofs cố ý vô hiệu xác nhận cũ khi firmware thay đổi.
Không tự chạy Activity đích, ghi âm, camera hoặc phát phím. Khi thấy người
dùng đang bấm nút trên máy, không điều khiển UI thêm.

Chưa nghiệm thu kết quả hành động bằng phím thật, Power/chords hoặc
screen-off/deep sleep; Binder và nhận event không chứng minh các hành vi đó.

## Candidate sửa readiness theo từng rule

Người dùng báo Plus double và Plus + Volume+ không chạy, còn thử hành động
thủ công mở được. Đọc cấu hình cho thấy hai rule đều là ACTIVITY. SharedPreferences
đã có proof API 37 cho Activity double nhưng tại thời điểm kiểm tra chỉ có proof
API 36 cho Activity của chord. Đây là dữ liệu đọc được; chưa giải thích đầy đủ
vì sao thử thủ công chord chưa tạo proof mới.

Lỗi code xác định: PolicyBridge dùng `all` trên mọi rule ACTIVITY đang bật để
cho phép cả action kind. Một Activity chưa xác nhận chặn luôn Activity khác
đã xác nhận, trong khi UI đánh giá từng action và vẫn báo rule kia sẵn sàng.
APP launcher cũng có cùng kiểu gộp theo kind.

Candidate mới dùng Binder v5, gửi danh sách readyRuleIds giới hạn 500 ID,
mỗi ID tối đa 128 ký tự, cấm trùng hoặc ID ngoài rule đang bật. PolicyGate
vẫn resolve rule/lịch/priority trước rồi kiểm tra readiness của chính rule đó;
không xóa/tắt rule trên đĩa và không rơi xuống action ưu tiên thấp hơn khi
rule thắng chưa sẵn sàng. Các điều kiện quyền/action kind/Binder vẫn giữ nguyên.

171 JVM test đạt, assemble/lint đạt (0 lỗi, 84 cảnh báo), packaging/DEX audit
và chữ ký đạt. Regression có hai Activity độc lập, thu hồi readiness, không
fallback và luồng Plus double rồi chord sau khi cấp readiness cho rule chord.

APK: `artifacts/OPlusKeyControl-coloros17-readiness-debug.apk`, SHA-256
`88e04d72590b9bf4778dd561ccb238b405d302a4631eab32f4eaac4d3e326262`.
Trạng thái tại thời điểm tạo candidate: chưa cài hoặc nghiệm thu. Cập nhật
2026-10-24: thay đổi này đã được đưa vào bản MD3 không lịch và cài lên máy.
Đã xác minh lại lời dặn trực tiếp của người dùng ở thread dự án trước: tự
soft reboot khi cần nạp lại Vector sau cập nhật APK. Đã thực hiện đúng một
soft reboot, UID1000 bound BackendKeeperService và UI hiện “Đã kết nối”.
Xem docs/design/NO_SCHEDULE_INSTALL_QA.md; kết quả hành động phím thật
vẫn cần kiểm tra riêng.
