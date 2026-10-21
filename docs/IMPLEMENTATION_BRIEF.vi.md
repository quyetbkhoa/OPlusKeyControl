# MASTER PROMPT CHO CODEX — ONEPLUS 15 BUTTON REMAPPER

> Đây là chỉ thị TRIỂN KHAI dự án, không phải yêu cầu chỉ viết kế hoạch. Hãy đọc hết, khảo sát hiện trạng, lập kế hoạch ngắn gọn, rồi thực thi từng giai đoạn trong workspace hiện tại. Không giả định những điều chưa xác minh trên thiết bị.

## 0. Vai trò, mục tiêu và phương pháp làm việc

Bạn là Android Staff Engineer kiêm chuyên gia Android input framework, AOSP, ColorOS, KernelSU, Shizuku và libxposed. Hãy **tạo từ đầu** một ứng dụng Android chất lượng có thể chia sẻ công khai trên GitHub, tên làm việc **Button Remapper** (tên/namespace cuối cùng có thể đặt hợp lý và ghi rõ). Ứng dụng tùy biến thao tác nút vật lý của OnePlus 15 trên ColorOS 16, với chế độ thông thường, ADB/Shizuku và Root/Vector.

Mục tiêu là **một sản phẩm hoạt động thực sự** trên máy, có giao diện hiện đại, đơn giản, dễ cấu hình, tiết kiệm pin, an toàn, có tài liệu và kiểm thử; không chỉ demo hoặc một bản UI giả. Có quyền truy cập ADB root để kiểm chứng nhưng **không được tự ý thử nghiệm có nguy cơ ảnh hưởng máy**.

Nguyên tắc:

1. Phân biệt `FACT` (đã đo/đọc bằng lệnh), `INFERENCE` (suy luận), `UNKNOWN` (chưa biết) và `BLOCKED` (không thể thử vì thiếu sự chấp thuận). Không bịa thành công, tương thích, API hoặc kết quả test.
2. Khảo sát **chỉ đọc** trước: đọc build, input device, package, camera, phiên bản Vector, giới hạn Android/ColorOS. Không sửa setting, không cài module, không khởi động lại máy hoặc tiến trình hệ thống trong giai đoạn này.
3. Chủ động tự nghiên cứu tài liệu chính thức và source liên quan khi cần; API ẩn/OEM phải được xác nhận bằng source hoặc thực nghiệm, không lấy ví dụ trên mạng làm sự thật cho firmware này.
4. Không hỏi người dùng những thông tin ADB có thể lấy tự động. Chỉ hỏi khi thực sự cần quyết định sản phẩm hoặc **xin phép trước thao tác có nguy cơ** trên thiết bị thật.
5. Sau giai đoạn khảo sát, triển khai code/test độc lập đến mức có thể. Nếu tính năng cần tương tác/nguy cơ trên thiết bị, dừng riêng phép thử đó và xin chấp thuận; tiếp tục các phần an toàn khác.
6. Dự án mới: khởi tạo Git, nhánh `main` ổn định, `dev` cho tích hợp, nhánh `task/*` khi cần. Checkpoint commits gọn, không push hoặc release tự động nếu chưa được cho phép. Giữ lịch sử sạch; không commit bí mật/keystore/serial/log riêng tư.
7. Luôn báo cáo ngắn: thay đổi, bằng chứng test, phạm vi đã hoàn thành, rủi ro còn lại và bước kế tiếp; không dump log dài vào context hoặc tài liệu dự án.

## 1. Thiết bị và môi trường đã biết

- Thiết bị đích: **OnePlus 15 (OP15)**.
- ROM: **ColorOS 16** (phải tự đọc đúng bản build và Android API level qua ADB).
- Đã root bằng **KernelSU (KSU)**.
- Đang sử dụng **Vector**, một framework Xposed/libxposed, người dùng xác nhận môi trường **API 101**; hãy đọc bản/cách khai báo module thực tế và kiểm tra tương thích.
- Có **ADB**, có thể sử dụng **ADB root / su** trong lúc phát triển. Kiểm tra `adb root` và `adb shell su -c id` riêng; đừng đánh đồng hai cơ chế.
- Dự án **mới hoàn toàn**, chưa có code; Codex có quyền dùng ADB để đọc dữ liệu hệ thống, nhưng các phép thử tác động máy cần xin phép trước.
- Mục đích: **công khai source trên GitHub**, ưu tiên dễ build/reproduce, không đặt mục tiêu Google Play ở giai đoạn đầu.
- Không đòi người dùng mở khóa bootloader/root thêm hoặc thay đổi cấu hình root đã có.

## 2. Nút được hỗ trợ và gesture

### Nút

- **Plus Key** bên trái máy OnePlus 15.
- **Volume +**.
- **Volume -**.
- **Power** với GIỚI HẠN NGHIÊM NGẶT: **chỉ cho phép remap double click và button combinations**. KHÔNG hỗ trợ mapping single press hoặc long press Power. Single press và long press Power tiếp tục để hệ thống xử lý như mặc định.

### Gesture

- Với Plus Key, Volume+, Volume-: **single click, double click, long press**.
- Với Power: **double click và combinations**, không có mục single/long trong UI hoặc domain rule.
- **Button combinations/chords**: giữ nút A rồi nhấn B, và khi khả thi nhấn gần đồng thời A+B. Ví dụ giữ Plus Key rồi nhấn Volume+ để bật/tắt đèn pin; Power + Plus Key nếu không xung đột các gesture hệ thống. Có thể mở rộng multi-button sau, nhưng MVP phải có tổ hợp hai nút hoạt động ổn định.
- Cấu hình thời gian nhận diện bằng **slider/preset, không bắt gõ tên mã**: double-click interval, long-press threshold (trừ Power), simultaneous/chord window, debouncing/repeat suppression.
- Mỗi mapping có thể bật/tắt, sửa, xóa; có thể thêm nhiều rule cho cùng một trigger với điều kiện thời gian khác nhau.

### Quy tắc an toàn và thứ tự ưu tiên

- **Chỉ thay thế hành động gốc của ColorOS khi trigger đó được gán và đang active**. Các thao tác không remap phải *pass-through* bình thường.
- Power single press phải khóa/đánh thức màn hình như hệ thống; không block toàn bộ Power để phát lại sự kiện giả nếu cách đó làm thay đổi single press.
- Power long press, very-long-press, SOS, emergency action và hard-reset/hardware power behavior **không được bị chiếm đoạt**. Không làm hỏng screenshot, emergency, boot/recovery/fastboot hoặc các tổ hợp OEM dành riêng.
- Khi có combo, tránh kích hoạt nhầm single/long của các nút thành phần; thiết kế gesture state machine quyết định đúng một hành động trong cùng một chuỗi sự kiện.
- Có thứ tự ưu tiên rõ (thí dụ chord > long > double > single), nhưng chỉ được *commit/suppress* sau khi xác định sự kiện đã đủ điều kiện. Cần xử lý key down/up, repeat, cancellation, thời gian giữ, press thừa, reentrancy và concurrent events.
- Phát hiện và cảnh báo xung đột rule, nhất là tổ hợp chứa Power hoặc rule đụng shortcut hệ thống. Không cho cấu hình nguy hiểm nếu chưa có biện pháp bảo vệ.

## 3. Điều kiện thời gian — BẮT BUỘC

Người dùng muốn **cùng một nút/cùng một gesture thực hiện hành động khác nhau vào các khung giờ khác nhau**. Ví dụ: Plus Key double-click 06:00–18:00 bật đèn pin; 18:00–23:00 mở app; còn lại dùng rule mặc định hoặc pass-through.

Thiết kế rule theo mô hình:

`Trigger(button/gesture/chord) + Conditions(local-time/day) -> Action + Priority + Enabled`

Yêu cầu cụ thể:

1. Chọn giờ bắt đầu/kết thúc bằng **Time Picker**; không nhập chuỗi giờ thủ công.
2. Hỗ trợ **khung giờ qua nửa đêm** (ví dụ 22:00–06:00), chọn các ngày trong tuần bằng checkbox/chip; mặc định áp dụng hằng ngày.
3. Hỗ trợ nhiều khung giờ cho cùng gesture, và một rule **mặc định không ràng buộc giờ**.
4. Quy định rõ khoảng `[start, end)` (bao gồm thời điểm bắt đầu, không bao gồm thời điểm kết thúc); hiển thị preview: **“Bây giờ, thao tác này sẽ làm gì?”** và lịch hiệu lực theo ngày.
5. Nếu hai rule overlap: báo xung đột trực quan; có độ ưu tiên xác định (rule thời gian cụ thể ưu tiên hơn default; nếu vẫn chồng lấn, yêu cầu chọn/chỉnh ưu tiên hoặc ngăn lưu, không random).
6. Xử lý thay đổi giờ hệ thống, múi giờ, DST, ngày trong tuần, khởi động lại và thời điểm qua ranh giới khung giờ. **Không dùng polling/timer chạy mỗi giây**. Có thể quyết định rule tại lúc nhận key event bằng clock + timezone hiện hành.
7. Với ghi âm toggle đang chạy, việc hết khung giờ không được khiến người dùng mất cách dừng ghi; có nút dừng trong UI/thông báo và giải pháp nhất quán cho lần nhấn tiếp theo.
8. Bổ sung unit test cho 00:00, 23:59, thời điểm đúng ranh giới, qua nửa đêm, overlap, timezone change và lịch theo thứ trong tuần.

## 4. Actions phải có

### 4.1 Đèn pin
- Turn on, turn off và toggle; ưu tiên `CameraManager.setTorchMode` khi quyền/điều kiện cho phép.
- Phản hồi nếu đèn pin bị camera hoặc hệ thống chiếm dụng; không treo/khởi động service thường trực không cần thiết.

### 4.2 Mở ứng dụng
- Chọn ứng dụng từ danh sách **tự quét**, hiển thị icon, tên ứng dụng, hỗ trợ tìm theo tên/nhãn, filter đã cài/hệ thống.
- Xử lý app bị gỡ, launcher intent không tồn tại, nhiều user/profile và giới hạn background activity launches.
- Đang khóa màn hình: trigger vẫn được nhận; khi app đích bắt buộc xác thực, giữ nguyên bảo vệ khóa màn hình, có thể đánh thức/chuyển tới màn hình unlock hợp lệ. Không bypass Keyguard.

### 4.3 Mở **Activity cụ thể** của ứng dụng — ROOT MODE
- Đây là action BẮT BUỘC: trong Root mode, người dùng chọn **ứng dụng**, rồi chọn **Activity cụ thể đã được app tự phát hiện/quét**. Tuyệt đối **không bắt người dùng nhập package name, class name, component, intent action, flags hoặc câu lệnh shell**.
- Màn hình chọn gồm icon + tên app, danh sách Activity với nhãn (nếu có), class dạng thông tin phụ chỉ đọc, trạng thái exported/protected/launchability, và xem trước kết quả kiểm tra.
- Quét các launcher/intent-resolvable Activities bằng Android APIs, và khi có root có thể đọc thông tin bổ sung qua PackageManager/dumpsys/package manifest trong phạm vi hợp lý. Quét nền, phân trang/cache, làm mới khi cài/gỡ/cập nhật app; không block main thread, không tải tất cả data nặng vào RAM cùng lúc.
- Ưu tiên intent explicit đúng ComponentName và flags đã kiểm chứng; xử lý exported/permission, profile/user, task affinity, yêu cầu extras và Activity chỉ hoạt động trong luồng điều hướng nội bộ của ứng dụng đích.
- **Yêu cầu về danh tính/đặc quyền:** người dùng muốn mở Activity “như có quyền thực sự” khi Root mode. Hãy xác minh **quyền UID thực tế của caller**, vì `su`, `am start` và `shell/root` KHÔNG tự động biến lời gọi thành UID hoặc chữ ký của app đích. Nghiên cứu chiến lược có thể hoạt động hợp lệ trên thiết bị root; chỉ triển khai nếu có bằng chứng chắc chắn trên firmware, không nói dối là có quyền target app. Không tắt kiểm tra quyền toàn hệ thống, không giả mạo quyền riêng tư hoặc bypass xác thực tài khoản/khóa bảo mật của app đích.
- Activity không exported/không thể chạy vì quyền chữ ký hoặc cần extras không xác định phải hiện `Không hỗ trợ / Cần xác minh` cùng lý do; vẫn hiển thị dưới bộ lọc Advanced nếu an toàn nhưng KHÔNG tạo rule hỏng. Không biến một ý tưởng chưa kiểm chứng thành tính năng được tuyên bố là hoàn thiện.
- Có giao diện `Test launch` rõ ràng, xin phép trước nếu thử hoạt động có tác dụng phụ hoặc ảnh hưởng ứng dụng khác.

### 4.4 Camera gốc ColorOS: MỞ VÀ CHỤP NGAY, GIỮ CAMERA MỞ
- **BẮT BUỘC sử dụng camera STOCK ColorOS của OnePlus 15**, không thay bằng CameraX/Camera2 riêng khi người dùng chưa đồng ý. Mục tiêu là giữ thuật toán xử lý ảnh gốc.
- Nhấn mapping -> gọi camera gốc (secure camera intent khi khóa màn hình) -> đợi camera thực sự sẵn sàng -> **kích hoạt chụp ảnh tự động đúng MỘT lần** -> **giữ camera tiếp tục mở** sau khi chụp.
- Cần tự khám phá package/activity camera ColorOS bằng ADB/PackageManager/intent resolution; KHÔNG hardcode package hoặc tọa độ màn hình khi chưa được xác nhận trên máy.
- Xem xét `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` và `..._SECURE`, OEM shortcut, KEYCODE_CAMERA, cách camera gốc nhận lệnh shutter, input injection/Accessibility chỉ nếu đã được chứng minh ổn định. Không giả định Volume Up là màn trập mặc định; không tự sửa tùy chọn camera gốc khi chưa xin phép.
- Chống double-shutter do sự kiện duplicate, tránh chụp trước khi camera ready; có timeout, retry giới hạn, kiểm chứng chụp thành công khi có tín hiệu đáng tin cậy (không nhầm “intent launched” với “photo captured”).
- Hoạt động khi màn hình đang mở, khóa, tắt trong giới hạn Android/ColorOS thực tế; nếu cần đánh thức màn hình, làm đúng cách nhưng không yêu cầu mở khóa khi secure camera hỗ trợ. Không lộ thư viện ảnh trên màn hình khóa.
- Nếu firmware không cho điều khiển shutter tin cậy: ghi rõ FAILED/UNSUPPORTED kèm bằng chứng, tiếp tục tìm phương án an toàn; KHÔNG lặng lẽ thay bằng camera riêng, KHÔNG claim thành công.

### 4.5 Ghi âm bằng nút
- Action ghi âm theo kiểu **toggle**: nhấn lần đầu bắt đầu, nhấn tiếp dừng và lưu; hỗ trợ thời lượng tự dừng do người dùng chọn qua preset/slider/time picker.
- Có trạng thái đang ghi, nút Stop dễ tiếp cận và thông báo/FGS hợp lệ; không ẩn chỉ báo microphone của Android, không ghi âm lén.
- `RECORD_AUDIO`, foreground service type `microphone` và while-in-use permission restrictions trên Android 14–16 phải được xử lý đúng; không giả định root tự giải quyết. Với khóa/tắt màn hình, nghiên cứu luồng được hệ điều hành cho phép (chẳng hạn visible Activity hợp lệ trước khi bắt đầu microphone FGS) rồi kiểm chứng.
- Lưu file vào vị trí có thể truy cập phù hợp, đặt tên tự động và hiển thị lịch sử bản ghi; lỗi quyền, thiếu dung lượng, mất microphone, ghi âm đang chạy và ngoại lệ app kill phải được xử lý.

## 5. Mức quyền / backend — một app, tự phát hiện khả năng

### Tier 1 — Basic
- App bình thường + runtime permissions, Accessibility tùy trường hợp và public APIs.
- Hỗ trợ các nút/hành vi mà API thực sự cho phép; thể hiện giới hạn khi Power/Plus Key hoặc screen-off không nhận được.
- Không hiện tính năng root chỉ bằng UI giả; dùng capability matrix và thông báo lý do thật.

### Tier 2 — ADB/Shizuku
- Sử dụng Shizuku khi có và được user cấp quyền; nghiên cứu cơ chế event capture và launching được cho phép trên thiết bị thật, không tự giả định Shizuku hỗ trợ mọi thứ.
- Trường hợp chỉ cấp quyền bằng ADB một lần phải giải thích tác dụng, giới hạn sau reboot và rủi ro. Không yêu cầu nhập các câu lệnh package/class bằng tay.
- Không tự động chuyển sang su/root nếu người dùng đang chọn Tier 2.

### Tier 3 — Root (KSU, Vector API 101 khi cần)
- Tier ưu tiên hỗ trợ đầy đủ Plus, Volume, Power-double và combinations trong trạng thái màn hình bật/khóa/tắt, miễn là driver/OEM chuyển được event.
- Thiết kế native Linux input event listener (nếu phù hợp), hook Android input policy/ColorOS bằng Vector/libxposed cho các xử lý cần thay thế hành vi OEM, hoặc giải pháp khác nếu bằng chứng cho thấy an toàn/tốt hơn.
- **Vector API 101 đang được cài trên máy**, không mặc định dùng legacy API khi không phù hợp. Xác nhận version, module metadata, hook scope, phiên bản API từ source Vector/libxposed và thử ở phạm vi hẹp trước khi mở rộng.
- Ưu tiên một APK gồm UI + module integration nếu kỹ thuật cho phép; nếu bắt buộc thành phần root phụ trợ/KSU module riêng, phải giải thích và chỉ thêm sau chấp thuận vì yêu cầu clean uninstall.
- Đặc quyền dùng tối thiểu, chỉ cho component thực sự cần. Không để shell command injection hoặc cấu hình intent tùy ý từ dữ liệu không tin cậy.
- **Không cam kết screen-off trong deep sleep nếu nút không phải wake source**. Đọc wakeup source/input availability; không giữ wakelock dài hạn để ép đạt tiêu chí. Nếu phần cứng không thể bảo đảm một trigger, báo capability không hỗ trợ và bằng chứng.

### Capability detection
- Từng trigger/action có trạng thái `Supported`, `Requires permission`, `Needs device verification`, `Unsupported`, kèm lý do ngắn.
- Nâng quyền theo nhu cầu. Trạng thái tier dễ hiểu; không bắt buộc toàn bộ người dùng root.
- Cho phép fallback giữa backend chỉ nếu không gây duplicate events và không phá behavior gốc.

## 6. Kiến trúc input, gesture và rule engine

Thiết kế dạng:

`Input Source(s) -> Normalized ButtonEvent -> Gesture/Chord State Machine -> Rule Resolver(time + priority) -> Action Executor -> Observability`

- Có interface cho Basic/Shizuku/Root sources. Các source phải có ownership và dedup để một press không phát nhiều lần.
- Event gồm device ID/source, button, action down/up, key repeat, monotonic timestamp, screen/keyguard state nếu cần; tránh suy luận dựa trên wall clock cho interval gesture.
- Gesture resolver và schedule matcher phải là **pure Kotlin/testable**, không dính Android UI.
- Mapping storage bền vững (Room hoặc DataStore phù hợp); migration/version rõ. Không lưu lệnh shell tùy ý do người dùng nhập.
- Power-double: thiết kế hook tại vị trí cho phép **giữ nguyên Power single/long trong OEM policy** và chỉ thay thao tác double đã gán; nếu ROM không có điểm can thiệp như vậy, báo hạn chế thay vì “grab rồi inject lại” làm hỏng chức năng nguồn.
- Chord detection cần giải quyết latency vs. pass-through các nút độc lập. Đo/giới hạn độ trễ volume; không swallow key-up; clear state khi source disconnect, screen state change, timeout, process restart.
- Đối với các action đang chạy, phải chống bấm nhiều lần gây đua tranh (camera open/capture, ghi âm toggle, app launch).

## 7. Khóa/tắt màn hình và hành vi hệ thống

- **Bắt buộc kiểm thử**: unlocked-screen-on, locked-screen-on, screen-off (ngay sau tắt), screen-off idle/deep sleep đủ điều kiện, sau reboot.
- Trigger event phải được nhận và giải quyết rule khi màn hình khóa/tắt nếu driver hỗ trợ. **Kết quả action** (ví dụ mở một app đòi authentication) vẫn phải tuân theo bảo vệ Keyguard của Android.
- Không phá khóa bảo mật, không mở nội dung cá nhân từ secure camera, không vô hiệu hóa thông báo/chỉ báo microphone.
- Có bảo vệ SOS, hard reboot, screenshot, recovery/fastboot, đồng thời đánh dấu các combination bị hệ thống bảo lưu là unavailable nếu không thể dùng an toàn.

## 8. Zero-config setup: mọi thứ chọn trong UI, tự quét

**QUY TẮC UX QUAN TRỌNG:** Không bao giờ bắt người dùng bình thường điền package name, Activity class, intent action, component string, input key code, script hoặc ADB command để cấu hình một rule.

- UI quét thiết bị: danh sách nút hỗ trợ, các app đã cài, launcher intents và Activities có thể khám phá, current capability, permissions, trạng thái Vector/Root/Shizuku.
- Wizard: (1) chọn nút/chord, (2) chọn gesture, (3) chọn điều kiện thời gian hoặc “mọi lúc”, (4) chọn action, (5) chọn target app/activity nếu cần, (6) xem preview + lưu/test.
- Tất cả điều kiện dùng chọn lựa, dropdown, radio, chips, time picker, slider; ô **tìm kiếm theo tên** được phép, nhưng không phải cấu hình bằng mã định danh kỹ thuật.
- Class/component/package chỉ được xuất hiện dưới phần “Chi tiết kỹ thuật” **read-only**, cần thiết cho debug. Giữ người dùng khỏi phải tìm và nhập thủ công.
- Quét manifest/activity có thể giới hạn bởi package visibility của Android: khai báo queries chính xác, giải thích/đề xuất cách làm root discovery khi cần và chịu trách nhiệm về quyền riêng tư.
- Danh sách có loading/empty/error states, sort/filter, cache và refresh theo thay đổi package.
- Không cố mở mọi Activity nội bộ một cách mù quáng; phân biệt `discoverable`, `launchable` và `requires app-specific state`.

## 9. Thiết kế giao diện

- **Jetpack Compose + Material 3**, UI tối giản, hiện đại, dễ nhìn, hỗ trợ light/dark/dynamic color nếu phù hợp.
- **Tiếng Việt và tiếng Anh** với strings resources; tiếng Việt là mặc định phù hợp, toàn bộ text hiển thị là localization, không hardcode string trong composable.
- Các màn hình tối thiểu:
  1. **Overview**: trạng thái service/backend, tier/quyền, nút được hỗ trợ, mapping active theo giờ hiện tại, quick enable/disable, warnings.
  2. **Mappings**: danh sách rule phân nhóm theo nút, bật/tắt, giờ áp dụng, action, thứ tự ưu tiên, trạng thái conflict.
  3. **Rule editor**: wizard chọn nút/chord/gesture/time/action/target bằng UI, preview và validation.
  4. **App & Activity picker**: icon, nhãn, sort/search, trạng thái exported/launchable, refresh.
  5. **Settings/Diagnostics**: quyền, battery info, test event (chỉ đọc), backup/restore rules, logs đã redacted, emergency disable, info firmware.
- Giao diện responsive, touch target đủ lớn, accessibility semantic, TalkBack, haptic tùy chọn, chỉ báo đang ghi âm thật.
- Nên có template/preset chức năng hữu ích (đèn pin, camera, ghi âm, ứng dụng), nhưng mặc định **KHÔNG tự remap nút** trước khi người dùng chọn.
- Emergency Disable trong app và notification/Quick Setting tile nếu phù hợp; khi disable phải trả ngay mọi nút về ColorOS.

## 10. Tiêu thụ pin và hiệu năng

- **Event-driven 100% trong trạng thái chờ**: native input listener chặn chờ bằng `poll`/`epoll` hoặc cơ chế kernel phù hợp; hook chỉ chạy khi có event; không polling logcat vô hạn như lựa chọn mặc định, không scan app liên tục.
- Không giữ wakelock CPU thường trực, không đặt timer chạy mỗi giây để áp schedule, không chạy busy loop, không giữ service foreground không cần thiết khi không ghi âm.
- Tránh hooks trên hot path không liên quan; O(1) hoặc rất nhanh cho event matching; không truy cập DB/IPC nặng trong main event dispatch của `system_server`.
- Có benchmark/measurement đơn giản: idle CPU usage, wakeups, memory, battery drain tương đối với app disabled/enabled; nêu phương pháp và số đo nếu thật sự thu được, tuyệt đối không bịa % pin.
- Tắt app/disable remap giải phóng listener và trả sự kiện cho Android ngay.

## 11. Khả năng khôi phục và gỡ cài đặt: yêu cầu bắt buộc

- Ưu tiên can thiệp **runtime, không sửa vĩnh viễn** `/system`, partition, keylayout, `Settings.Secure/Global/System` hoặc cấu hình OEM.
- Không mặc định dùng `EVIOCGRAB` chiếm trọn input device, đặc biệt Power; nếu thực nghiệm cần, chỉ thử sau khi có cơ chế rollback an toàn và user đồng ý.
- Khi rule không khớp, service dừng, crash, backend mất kết nối, Vector scope bị tắt hoặc APK bị gỡ, hook/listener **fail-open/pass-through**, không gây stuck keys hoặc mất khả năng sử dụng nút.
- **Gỡ APK trực tiếp phải đưa hành vi về mặc định ColorOS**, kể cả khi hook được nạp trong tiến trình còn sống: thiết kế kiểm tra sự tồn tại/trạng thái cấu hình theo event hoặc cơ chế package change, không phụ thuộc `onDestroy()`/uninstall callback của chính APK; nếu bắt buộc reboot mới giải phóng hook thì phải chứng minh nút đã pass-through an toàn ngay sau uninstall và ghi giới hạn minh bạch.
- Không để daemon/root script tự khởi động còn sót sau gỡ. Nếu thay đổi setting nào thật sự không thể tránh, lưu giá trị ban đầu, có rollback độc lập và yêu cầu chấp thuận trước khi viết; không tuyên bố clean uninstall khi cơ chế không đảm bảo.
- Hook `system_server` có thể gây bootloop: cần guard, kill switch, crash counter/fallback, logging tối thiểu, phạm vi hook hẹp; trước khi kích hoạt phải có hướng dẫn recovery rõ ràng và sự chấp thuận. Không tự restart `system_server` hoặc reboot.

## 12. Bảo mật, quyền riêng tư, chia sẻ GitHub

- Root chỉ sử dụng khi người dùng bật rõ ràng; yêu cầu quyền tối thiểu, kiểm tra mọi IPC caller, không expose root-execution API cho app khác.
- Không thu thập, upload hoặc log nội dung ghi âm, ảnh, danh sách ứng dụng cá nhân; log debug tối thiểu, redacted, có thể tắt.
- Không đưa vào repo ADB serial, ảnh, audio, logcat riêng tư, token/credential, keystore hoặc proprietary ColorOS binaries.
- Không thêm SDK analytics/ads/network khi không có yêu cầu; app có thể hoạt động offline.
- Chuẩn bị LICENSE mã nguồn mở hợp lệ dựa trên dependency licenses, README (VI/EN), supported devices, known limitations, screenshots không lộ dữ liệu cá nhân, setup KSU/Vector/Shizuku, cài đặt, lỗi phổ biến và uninstall/recovery guide.
- Không vi phạm chế độ bảo mật app bên thứ ba: không tuyên bố có thể impersonate UID/signature của app đích chỉ vì có root; không xây cơ chế bypass xác thực/khóa tài khoản.

## 13. Công nghệ và cấu trúc mã

- Kotlin hiện đại, Jetpack Compose/Material3, coroutines/Flow, AndroidX Lifecycle, dependency injection tối giản (Hilt/Koin hoặc thủ công có lý do), Room/DataStore phù hợp, Gradle Kotlin DSL, version catalog.
- Phân ranh giới rõ: `ui`, `domain`, `storage`, `input`, `gesture`, `scheduler/rule`, `actions`, `permission/capabilities`, `diagnostics`, `root/shizuku`, `vector-hook`. Có thể đa module nếu thực sự giúp build/test, tránh overengineering.
- Logic rule/gesture viết JVM-testable. Các khối native/hook được cô lập để app vẫn build khi chưa bật module root, phù hợp 3 tiers.
- Đọc tài liệu và phụ thuộc tương thích Android 16/API 36 (xác minh exact SDK trên máy), không dùng API lỗi thời nếu có lựa chọn ổn định.
- Tránh hardcode key code của Plus Key, camera package, proprietary class/hook point khi chưa khám phá; cấu hình backend theo thiết bị và firmware có kiểm tra compatibility.
- Không thêm paperclip, multi-agent orchestration, MCP tùy tiện. Một agent chính tự triển khai; dùng công cụ hợp lý, tiết kiệm context.

## 14. Quy trình triển khai yêu cầu Codex phải thực hiện

### Phase 0 — Read-only device discovery (bắt đầu ngay)

1. Khảo sát repo/workspace, xác định có thực sự trống, công cụ Android SDK/Gradle/JDK/ADB đang có; không xóa/ghi đè ngoài phạm vi.
2. Chạy lệnh **chỉ đọc** phù hợp, ví dụ: `adb devices -l`, `adb shell getprop`, `adb shell dumpsys input`, `adb shell dumpsys window`, `adb shell pm list packages`, `adb shell cmd package resolve-activity`, `adb shell su -c id`, kiểm tra phiên bản Vector/KernelSU ở mức chỉ đọc. Tránh chạy lệnh có tác dụng sửa setting/enable/disable service.
3. Điều tra tên sự kiện/nút/driver, input capabilities, wakeup behavior, keylayout/policy mapping qua đọc file/log/dumpsys. Bất kỳ bài test phải yêu cầu người dùng bấm nút thật hoặc dùng input injection cần thông báo trước, đặc biệt Power/combo có SOS.
4. Khảo sát camera package/activity, secure intent, cách stock camera bắt shutter; dấu vết Plus Key được OEM xử lý; hệ thống package manager có thể enumerate Activities ra sao.
5. Khảo sát nguồn Vector/libxposed API 101 và giới hạn khi hook policy; chưa kích hoạt hook.
6. Viết `docs/DEVICE_DISCOVERY.md` gồm bảng FACT/INFERENCE/UNKNOWN, dẫn chứng lệnh đã chạy đã redacted, giới hạn, candidate architecture và các test cần user approval.

**Điểm kiểm soát:** Không sửa nút/setting/cài module/kill process/reboot. Có thể scaffold mã an toàn song song. Trước test tác động điện thoại, đưa rõ thao tác, rủi ro, cách rollback và hỏi chấp thuận.

### Phase 1 — Skeleton + rule engine + UI

- Khởi tạo repo, Gradle/CI, app Compose, navigation, theme/locales, domain models, storage, capability UI.
- Xây gesture parser/state machine và schedule resolver thật, unit tests đầy đủ; UI wizard và auto app picker.
- Mock/fake source cho test nhưng **ghi rõ fake**, không đưa màn hình giả làm feature hoàn chỉnh.
- Build debug APK; chạy JVM tests/static checks và commit checkpoint.

### Phase 2 — Basic tier

- Triển khai permission flow, action flashlight, app launcher, basic input path, recorder phù hợp hạn chế Android.
- Test khả năng thật và hiển thị limitation chính xác.

### Phase 3 — Shizuku/ADB tier

- Thiết kế backend riêng, real permission detection, event dedup và minimal privilege. Thử trên thiết bị chỉ sau chấp thuận nếu ảnh hưởng nút/cài permission.

### Phase 4 — Root/Vector tier

- Xác minh code path cho Plus/Vol/Power-double/combinations; triển khai root source và hook hẹp khi cần, không hook toàn hệ thống sớm.
- Bổ sung pass-through, emergency disable, safe fallbacks, crash protections và màn hình chẩn đoán.
- Test locked/screen-off theo từng nút và tổ hợp; chỉ kích hoạt hooks/device test sau khi người dùng đồng ý. Nếu gặp hardware wake-source limitation, ghi unsupported, không giữ wakelock để đối phó.

### Phase 5 — Stock ColorOS camera + activity launcher + recorder

- Triển khai camera stock secure/nonsecure launch -> shutter -> giữ camera mở; xác minh ảnh thật nếu có thể, chống duplicate.
- Hoàn thiện activity picker + root launch theo giới hạn danh tính/permission thực tế.
- Hoàn thiện recorder toggle/autostop + foreground service & privacy indicators.

### Phase 6 — Stabilization, release-readiness

- Test hiệu năng, pin, concurrency, khởi động lại, backend crash/loss, app stop/disable, uninstall restoration, profile/package changes, schedule qua ngày.
- README, LICENSE, docs, build instructions, CI, signed-debug APK (release artifact không hardcode secret). Không push/tag/public release nếu chưa được người dùng phê duyệt.

**Ưu tiên:** Chức năng nút thật và an toàn hệ thống > hoàn chỉnh hành động > ổn định > UX tinh chỉnh. Không tuyên bố “hoàn thiện” nếu case cốt lõi chưa test trên máy.

## 15. Ma trận nghiệm thu (phải dùng để báo cáo kết quả thật)

Kiểm thử tối thiểu (ghi `PASS/FAIL/NOT_TESTED/BLOCKED` và bằng chứng):

1. Plus single, double, long; Vol+ single/double/long; Vol- single/double/long.
2. Power double remap; Power single/long **không thay đổi**; tổ hợp Power nếu không đụng OEM protected behavior.
3. Plus+Vol+, Plus+Vol-, một tổ hợp Power an toàn khi tương thích; không double-fire.
4. Mỗi trường hợp ở screen-on unlocked, locked, screen-off immediately, idle/deep sleep khả dụng; sau reboot.
5. Trigger **không có rule** => chức năng ColorOS gốc; trigger active => action mới thay hành động OEM khi có thể.
6. Time windows cùng gesture trả các action khác nhau, crossing midnight, overlap, boundaries, weekdays và default fallback.
7. Flashlight toggle; open app; Activity picker tự quét + launch root được phân loại chính xác; hidden/protected Activity không gây crash.
8. ColorOS **stock camera** được mở đúng trạng thái secure/nonsecure, tự chụp đúng một ảnh và **giữ camera mở**.
9. Recorder bấm bắt đầu, bấm lại dừng, auto-stop, thông báo/indicator và chính sách lock screen đúng.
10. Permission/backend upgrade/downgrade; fallback không mất nút và không duplicate event.
11. Kill app, disable remap, uninstall APK, reboot: nút và settings ColorOS khôi phục; không còn daemon/module file mồ côi.
12. Không tăng wakeups/idle CPU bất hợp lý; không bận CPU/polling; không giữ wakelock liên tục.
13. Giao diện chỉ dùng bộ chọn, không bắt nhập package/activity/action; accessibility, locale, dark mode, empty/error states.

## 16. Báo cáo và bàn giao

Mỗi phase bàn giao:

- Các file/commit đã tạo, lý do quyết định kiến trúc.
- Câu lệnh build/test thực sự chạy và kết quả (không suy diễn).
- Bảng coverage + `PASS/FAIL/BLOCKED/NOT_TESTED` theo feature/tier/screen-state.
- Các quyền user cần chấp thuận trước khi thử tiếp trên máy và rollback plan.
- Checkpoint branch/commit và đường dẫn artifact đã tồn tại thật (không tự dựng link).

Kết quả cuối mong muốn: repository mới có source, unit tests, integration/device test instructions, Compose UI hoạt động, cơ chế backend rõ ràng, README/License, APK debug để người dùng nghiệm thu, tài liệu thiết bị và giới hạn đã kiểm chứng. Giữ `main` sạch, không merge/push/deploy khi chưa được phép.

## 17. Tài liệu xuất phát để đối chiếu, không thay thế kiểm chứng trên ROM

- Vector (repo chính): https://github.com/JingMatrix/Vector
- Vector libxposed bridge: https://github.com/JingMatrix/Vector/blob/master/xposed/README.md
- Android MediaStore secure camera intent: https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
- Android background FGS restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android FGS service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- AOSP SystemUI camera design: https://android.googlesource.com/platform/frameworks/base/+/master/packages/SystemUI/docs/camera.md

**BẮT ĐẦU NGAY:** Tự kiểm tra workspace/toolchain và ADB theo quy trình read-only. Trả lời ngắn gọn bằng tiếng Việt với `Device facts`, `Risk/uncertainty`, `Architecture direction`, `Phase 0 evidence`, và kế hoạch hành động cụ thể. Sau đó tự tạo repo/scaffold và triển khai các phần an toàn; chỉ tạm dừng đối với thao tác trực tiếp trên máy cần người dùng chấp thuận. Đừng chỉ trả về một bản kế hoạch lý thuyết.
