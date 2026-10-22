# OPlusKeyControl — brief thiết kế lại toàn bộ giao diện

Tài liệu này có thể gửi nguyên văn cho AI/designer tạo giao diện. Không cần đọc source hoặc cuộc trò chuyện trước.

## 1. Nhiệm vụ và sản phẩm

Hãy thiết kế lại toàn bộ giao diện ứng dụng Android **OPlusKeyControl**, xuất ảnh giao diện chi tiết để một lập trình viên dựng lại bằng **Kotlin / Jetpack Compose**. Đây là tiện ích gán hành động cho nút vật lý trên OnePlus 15 / ColorOS 16 / Android 16. Ngôn ngữ giao diện chính là **tiếng Việt**, có bản địa hóa tiếng Anh.

Ưu tiên: dễ hiểu ngay, ít chữ thừa, thao tác nhanh, phân biệt rõ bật cấu hình với thực sự hoạt động. Thiết kế như ứng dụng Android hoàn chỉnh, không như trang web, dashboard máy tính hoặc landing page. Tên OPlusKeyControl giữ nguyên.

Mốc source local: Git tag `v1.0.1`, commit `a35e8b454b918c0ec825265e2d8987f339991e71`. Ngày 22/10/2026, người dùng báo các chức năng Plus có vẻ hoạt động tốt. Đây là xác nhận thực tế từ người dùng, chưa phải nghiệm thu mọi trường hợp. `v1.0.1` là mốc Git; metadata APK ở mốc này vẫn ghi `0.1.0-dev`. Không tự in một số phiên bản giả lên UI.

Thiết kế có thể thay bố cục và cách phân nhóm, nhưng phải giữ đủ chức năng dưới đây. Không thêm chức năng không có backend vào các ảnh như thể đã chạy được. Các phương án bố cục ở phần sau là đề xuất thiết kế mới, không phải mô tả tất cả chi tiết UI hiện tại.

## 2. Chức năng và giới hạn phải phản ánh đúng

### 2.1 Nút và cử chỉ

| Nút / chế độ | Hiện trạng | Cách thể hiện |
|---|---|---|
| Plus Key, Root / Vector | Có remap nhấn một lần, nhấn đúp, nhấn giữ; người dùng báo hoạt động tốt | Luồng chính, dễ cấu hình |
| Bật riêng một kiểu nhấn Plus | Đã hỗ trợ; không phải bật đủ ba | Các công tắc độc lập |
| Power / nút nguồn | Chỉ cho cấu hình nhấn đúp dưới dạng bản nháp; chưa có backend remap | Hiện lý do chưa hỗ trợ; không giả lập công tắc hoạt động |
| Power nhấn đơn / giữ | Không cho gán; thuộc hệ thống | Không đưa vào bộ chọn |
| Volume + / Volume − | Có mục cấu hình, chưa hỗ trợ thay thế thao tác | Bản nháp hoặc mục chưa hỗ trợ |
| Tổ hợp hai nút | Có mô hình rule, chưa có backend chạy | Đưa vào nâng cao, đánh dấu chưa hỗ trợ |
| Chế độ Cơ bản | Trợ năng chỉ quan sát phím Volume | Không báo remap sẵn sàng |
| ADB / Shizuku | Phát hiện kết nối và xin quyền; chưa có adapter remap | Phân biệt quyền đã cấp với remap chưa hỗ trợ |

Khi có ít nhất một rule Plus đang áp dụng và hành động sẵn sàng, app có thể nhận xử lý chuỗi bấm Plus. **Kiểu nhấn chưa được gán trong chuỗi này sẽ không làm gì**, không tự đổi thành nhấn đơn và không tự chạy chức năng gốc. Khi không có rule nào sẵn sàng, hoặc tắt app/remap/mất quyền, chuỗi mới do hệ thống xử lý. Đưa giải thích này vào trợ giúp ngắn ở nơi cấu hình, không lặp trên từng card.

### 2.2 Các hành động

| Hành động | Dữ liệu người dùng chọn | Điều kiện / phản hồi |
|---|---|---|
| Bật/tắt đèn pin | Không có cấu hình phụ | Cần Camera; báo lỗi khi camera đang chiếm đèn |
| Bật đèn pin | Không có cấu hình phụ | Như trên |
| Tắt đèn pin | Không có cấu hình phụ | Như trên |
| Mở ứng dụng | Chọn app từ danh sách có icon và tên | Báo app bị gỡ/tắt/không có launcher; yêu cầu mở khóa khi cần |
| Mở Activity | Chọn app → chọn Activity đã quét | Root; có bước thử mở trước khi cho bật rule; protected/disabled không chọn được |
| Camera gốc: mở và chụp | Không yêu cầu nhập tên package | Có code thử nghiệm; chỉ bật sau bước xác minh hành động thành công, không trình bày là đã nghiệm thu toàn bộ |
| Bắt đầu/dừng ghi âm | Thời gian tự dừng | MP3 thật; cần micro và thông báo; ghi âm nền qua Root / Vector |

Không có ô bắt nhập package, class, intent hoặc lệnh shell. Tên class chỉ là thông tin phụ đọc được để phân biệt Activity.

Ghi âm:

- Bấm lần đầu bắt đầu, bấm lại dừng và lưu. Có nút **Dừng và lưu** trong app và thông báo Android.
- MP3 mono 44,1 kHz, 128 kb/s, thư mục `Music/OPlusKeyControl`. Bản ghi M4A cũ vẫn tồn tại và mở được.
- Slider tự dừng hiện cho chọn 10–3600 giây, mặc định 300 giây. Có thể thiết kế hiển thị phút/giây dễ hiểu, nhưng giữ dải giá trị này.
- Có thông báo thường trực khi ghi và chỉ báo micro của Android. Hết lịch không được làm mất nút dừng.
- Lịch sử tối đa 50 bản gần nhất, tên file và nút mở bằng trình phát bên ngoài. Chưa có player, sửa/xóa/chia sẻ file trong app.
- Không vẽ waveform, biên độ micro hoặc đồng hồ đếm ngược như dữ liệu đang có: bản hiện tại chưa cung cấp các trường đó cho UI. Nếu muốn đề xuất, tách riêng ý tưởng bổ sung.

### 2.3 Rule và lịch

Mỗi rule gồm: nút + kiểu nhấn + hành động + tùy chọn lịch + ưu tiên + bật/tắt.

- Thêm, sửa, xóa, bật/tắt từng rule; có công tắc tổng tắt/bật remap.
- Cho phép nhiều rule cùng kiểu nhấn ở các khung giờ khác nhau.
- Chọn **Mọi lúc** hoặc **Theo lịch**; lịch có giờ bắt đầu/kết thúc bằng Time Picker và bảy ngày trong tuần.
- Hỗ trợ qua nửa đêm, ví dụ 22:00–06:00. Thứ được chọn là ngày bắt đầu lịch. Khoảng tính từ giờ bắt đầu đến trước giờ kết thúc.
- Rule theo lịch đang khớp ưu tiên hơn rule mặc định Mọi lúc. Trong cùng loại, ưu tiên số cao hơn thắng; phạm vi 0–100.
- Hai rule bật cùng trigger, trùng ưu tiên và chồng lịch tương đương bị chặn lưu/bật. Hiển thị lỗi tại phần lịch/ưu tiên kèm cách sửa, không đẩy lỗi thành một đoạn dài toàn màn hình.
- Có preview tại thời điểm hiện tại và nút làm mới. Đây là kết quả chọn rule, không phải bằng chứng hành động đã chạy.
- Rule Plus có thể chuẩn bị/bật khi backend tạm offline nếu hành động đủ điều kiện; UI phải ghi **Chờ kết nối**, không dùng màu xanh “Đang hoạt động”.
- Thao tác chưa được hỗ trợ có thể lưu bản nháp tắt. Vẫn cho người dùng tắt một rule cũ, kể cả khi quyền hiện không còn đủ.

Ví dụ dữ liệu cho ảnh: Plus nhấn đơn → Bật/tắt đèn pin → Mọi lúc; Plus nhấn đúp → Mở ứng dụng → 06:00–22:00; Plus nhấn giữ → Ghi âm MP3 → Mọi lúc, tự dừng 5 phút. Dùng tên/icon app minh họa phổ biến hoặc trung tính, không cần danh sách app cá nhân.

### 2.4 Quyền, kết nối, pin

Phải phân biệt bốn lớp thông tin:

1. **Chế độ đã chọn**: Cơ bản / ADB–Shizuku / Root–Vector.
2. **Quyền thực tế**: Trợ năng, Shizuku hoặc Root/scope Vector; đang xin, đã cấp, từ chối hoặc chưa xác minh được.
3. **Kết nối backend**: chưa nối, đang kiểm tra/kết nối, đã nối Plus. Root đã cấp không đồng nghĩa backend đã nối.
4. **Remap tổng** và từng rule: có thể đang tắt hoặc đang chờ dù quyền đã cấp.

Đổi sang chế độ khác sẽ tắt remap; cần báo ngắn gọn và không tự tuyên bố thành công. Chọn lại chế độ hiện tại không tắt remap. Nút kết nối lại kiểm tra quyền/scope; không phải nút reboot.

Quyền hành động: Camera, Micro, Thông báo; hiển thị trạng thái từng quyền và đường cấp quyền/mở thông tin app. Thông báo của hệ thống Android là native, không phải dialog riêng của app.

Pin: người dùng quan sát thấy trễ khi màn hình tắt nếu ColorOS giới hạn pin; cấp “Không giới hạn” thì tốt hơn. Có trạng thái **Đang tối ưu pin** / **Đã miễn tối ưu pin Android**, nút cho phép chạy nền và mở cài đặt pin app. Không báo đã kiểm tra toàn bộ hạn chế ColorOS chỉ dựa trên miễn tối ưu Android. Không cần cảnh báo pin màu đỏ khi mọi thứ bình thường.

### 2.5 Chức năng phụ còn phải giữ

- Thử đèn pin, thử ghi âm và chỉ mở camera gốc trong Cài đặt. “Chỉ mở camera” khác “Mở và chụp”.
- Trong editor có nút thử hành động và dialog xác nhận trước khi tác động thiết bị/mở app; phản hồi đang xử lý, yêu cầu đã gửi hoặc lỗi.
- Tùy chỉnh thời gian nhận diện: nhấn đúp 150–600 ms; nhấn giữ 400–1500 ms; cửa sổ tổ hợp 30–250 ms; chống dội 0–100 ms. Mặc định lần lượt là 300, 600, 100 và 30 ms. Thông số tổ hợp chưa làm backend tổ hợp hoạt động. Có nút Lưu.
- Xuất/nhập cấu hình JSON qua bộ chọn file Android. Chỉ sao lưu rule, không chứa âm thanh; nhập cấu hình luôn tắt remap để người dùng kiểm tra lại.
- Chẩn đoán: model, Android/API, firmware, kiểm tra UID Root chủ động và log sự kiện quan sát có giới hạn. Dành cho phần nâng cao, không đặt lên trang chủ.
- Tắt ngay từ app và ô Quick Settings của Android; không phải xóa rule hay tắt toàn bộ hệ thống.
- Sáng/tối và màu động theo Android hiện đã dùng; thiết kế mới cần có dark/light tương ứng. Chưa có bộ chọn theme riêng trong app.

## 3. Cấu trúc điều hướng đề xuất

Giữ ba tab chính **Tổng quan — Thao tác — Cài đặt**. Các trang con mở toàn màn hình hoặc bottom sheet phù hợp, không ép tất cả vào một trang Cài đặt dài.

Luồng chính: Tổng quan → Thao tác → Thêm/Sửa → chọn nút/cử chỉ → lịch → hành động → xem lại/lưu. Bộ chọn app và Activity mở từ hành động; quay lại không mất draft. Có thể thiết kế editor gọn hơn bốn màn, nhưng ảnh phải thể hiện đủ mọi trường và nhánh cấu hình.

Lịch sử ghi âm và thiết lập quyền/pin có thể thành trang con truy cập từ Cài đặt. Khi ghi âm, banner dừng xuất hiện ở các tab chính. Không dùng nhiều FAB nổi; chỉ một hành động thêm rule rõ ràng ở trang Thao tác.

## 4. Các màn hình bắt buộc

### S01 — Tổng quan

- Tên app và đường tắt Tắt ngay.
- Khu điều khiển tổng: công tắc, nhãn “Đang bật”/“Đang tắt”.
- Một cụm trạng thái ngắn: chế độ đang dùng, Plus kết nối hay chưa; CTA sửa lỗi khi cần.
- Tóm tắt các kiểu nhấn Plus và hành động hiệu lực hiện tại. Ưu tiên nhận diện nhấn đơn/đúp/giữ bằng icon + nhãn, không bằng ba đoạn văn.
- Phân biệt “chọn được rule”, “chờ kết nối”, “đã tắt”, “ngoài lịch”, “không có rule”. Không ghi “Giữ thao tác gốc” cho một gesture chưa gán trong chuỗi Plus app đang chiếm.
- Lời nhắc pin chỉ khi cần. Khi đang ghi, ưu tiên banner có Dừng và lưu.

### S02 — Danh sách thao tác

- CTA Thêm thao tác; rule có icon nút/cử chỉ, tên hành động và đích nếu có, lịch rút gọn, switch riêng.
- Sửa/Xóa rõ ràng; có thể dùng menu phụ thay vì lặp ba nút trên mọi card.
- Ưu tiên chỉ hiện nổi bật khi có ý nghĩa; chi tiết dài đưa vào màn sửa.
- Trạng thái rule: bật, tắt, ngoài lịch, chờ backend, thiếu quyền, cần thử hành động, chưa hỗ trợ. Chữ và icon bổ trợ màu.
- Power/Volume/tổ hợp chưa hỗ trợ không cạnh tranh thị giác với Plus hoạt động.
- Empty state ngắn có một CTA; không cần illustration khổng lồ.

### S03 — Chọn nút và cử chỉ

- Nút Plus nổi bật; các nút khác hiện đúng trạng thái hỗ trợ.
- Bộ chọn nhấn đơn/đúp/giữ dễ hiểu, chọn một rõ ràng.
- Power chỉ có nhấn đúp và trạng thái bản nháp, không có single/long.
- Nếu mở cấu hình tổ hợp: chọn hai nút, báo tổ hợp được bảo lưu/không hỗ trợ. Không cho tổ hợp Power hoặc cặp hai Volume.
- Tiến trình editor, quay lại/hủy/tiếp tục cố định trong safe area.

### S04 — Lịch áp dụng

- Mọi lúc / Theo lịch, giờ bắt đầu/kết thúc, chip Thứ 2…CN.
- Ví dụ qua nửa đêm; lỗi chưa chọn ngày hoặc hai giờ bằng nhau.
- Ưu tiên ở mục nâng cao; ví dụ conflict có chỉ dẫn ngắn và nút sửa.
- Preview lịch dùng ngôn ngữ tự nhiên, không hiển thị ký hiệu kỹ thuật `[start,end)` trên mặt chính.

### S05 — Chọn hành động

- Nhóm Đèn pin với ba lựa chọn; Mở ứng dụng; Mở Activity; Ghi âm MP3; Camera gốc thử nghiệm.
- Dùng hàng chạm được toàn vùng, một lựa chọn đang active. Không lặp nút radio cộng thêm một nút to riêng cho cùng hành động.
- Cấu hình phụ chỉ xuất hiện cho hành động đã chọn: chọn đích hoặc tự dừng ghi âm.
- Quyền thiếu và bước thử cần thiết xuất hiện ngay cạnh hành động đó.

### S06 — Chọn ứng dụng

- Search theo tên/package; bật/tắt ứng dụng hệ thống; danh sách icon + tên; package chỉ khi cần phân biệt.
- Loading, rỗng, không tìm thấy, lỗi quét và thử lại.
- Chọn app để mở launcher, hoặc chuyển tới danh sách Activity tùy luồng. Chọn không phải thử chạy.

### S07 — Chọn Activity

- Tên/icon app đang chọn; search theo nhãn/class; danh sách màn hình.
- Ưu tiên launcher rồi exported, sau đó nội bộ ở Root. Nhãn dễ đọc và tên class phụ, không để trống khi thiếu nhãn.
- Badge “Màn hình chính”, “Cần thử mở”, “Nội bộ · Root”, “Bị bảo vệ”, “Đã tắt”.
- Bộ lọc nâng cao hiển thị protected/disabled nhưng không cho chọn các mục này. Nội bộ chỉ chọn ở Root và vẫn phải qua bước thử để bật rule.
- Không có ô nhập component. Class dài được xuống dòng/ellipsis hợp lý, xem đủ qua chi tiết.

### S08 — Xem lại và lưu

- Tóm tắt nút/cử chỉ → hành động, app/Activity đích, lịch và ưu tiên khi cần.
- Công tắc bật rule, lưu bản nháp khi chưa đủ điều kiện.
- Nút thử hành động, phản hồi và dialog xác nhận; đây là hành động thật.
- Lỗi xung đột/thiếu quyền/backend được phân biệt, không dùng chung “Không được”.
- Nút Lưu rõ ràng; preview hiện tại không giả làm lịch sử thực thi.

### S09 — Cài đặt

- Các nhóm gọn: Quyền và kết nối; Chạy nền và pin; Bản ghi; Thời gian nhận diện; Sao lưu; Kiểm tra hành động; Chẩn đoán.
- Mỗi nhóm có tóm tắt trạng thái cần thiết và đường vào trang con. Tránh hàng loạt card to với giải thích dài.
- Có thể thêm phần Giới thiệu tối giản, nhưng không bịa phiên bản/URL hỗ trợ.

### S10 — Quyền và kết nối

- Bộ chọn ba chế độ; trạng thái quyền thực tế và backend riêng nhau.
- CTA theo ngữ cảnh: mở Trợ năng, yêu cầu Shizuku, kiểm tra/kết nối lại Root.
- Danh sách Camera/Micro/Thông báo và trạng thái từng quyền.
- Thiết kế cả đang chờ, thành công, từ chối và mất kết nối. Không dùng spinner vô hạn không có lời giải thích.
- Nhắc remap bị tắt sau đổi mode, không tự bật lại.

### S11 — Chạy nền và pin

- Trạng thái tối ưu pin Android, CTA cấp miễn tối ưu và mở pin ứng dụng.
- Giải thích ColorOS “Không giới hạn” trong phần mở rộng, ngắn và làm theo được.
- Không dùng điểm sức khỏe giả, số ms độ trễ giả hoặc nút “Tăng tốc” không có backend.

### S12 — Bản ghi và đang ghi

- Danh sách tên file, chỉ báo định dạng MP3/M4A theo file và thao tác mở nghe.
- Empty/loading/error. Không vẽ chức năng xóa/chia sẻ/trình phát trong app như tính năng đã có.
- Banner đang ghi: icon micro + “Đang ghi âm” + Dừng và lưu; đường tới danh sách bản ghi nếu hợp lý.
- Có ảnh thông báo Android với nút dừng để lập trình viên đối chiếu, dùng phong cách notification native.

### S13 — Cài đặt nâng cao và tác vụ phụ

- Trang thời gian nhận diện với slider, giá trị + đơn vị và Lưu.
- Sao lưu JSON: Xuất, Nhập, trạng thái thành công/thất bại; nhập xong remap tắt.
- Chẩn đoán: thông tin firmware, kiểm tra Root chỉ đọc, kết quả/log thu gọn.
- Kiểm tra thủ công: đèn pin, ghi âm, chỉ mở camera. Tách rõ các thao tác có tác động thật.

## 5. Bộ component cần bàn giao

| Component | Nội dung / biến thể |
|---|---|
| App bar, bottom navigation, editor footer | Back/cancel, save/continue, tab active, safe area |
| Master control | ON/OFF và remap chưa sẵn sàng dù đã ON |
| Backend status | Checking, connected, disconnected, permission missing, unsupported |
| Button/gesture selector | Selected, normal, unavailable; Plus, double, hold icons |
| Rule row/card | Trigger, action, target, lịch, trạng thái, switch, menu |
| Current-action summary | Kết quả rule hiện tại, ngoài lịch/chờ quyền/đã tắt |
| Action choice row | Icon, tên, selected, cấu hình phụ và hạn chế |
| App row | Icon, label, optional package, select |
| Activity row | Label, class, launchability badge, select disabled/available |
| Schedule control | Mọi lúc/Theo lịch, time fields, day chips, qua nửa đêm |
| Slider field | Label, value/unit, min/max; priority trong nâng cao |
| Permission row | Tên quyền, status, CTA tương ứng |
| Battery status | Optimized/exempt, CTA, giải thích mở rộng |
| Recording banner/notification | Đang ghi, stop/save, lỗi |
| Recording file row | Tên, định dạng, mở nghe |
| Search/filter | Empty/loading/results/no results/error, system/advanced chips |
| Confirmation dialog | Thử hành động thật; cancel/confirm rõ thứ tự |
| Feedback | Snackbar, inline error, warning/info disclosure; không tràn chữ |
| Empty state | Chưa có rule/bản ghi, một CTA phù hợp |
| Advanced group | Thu gọn/mở rộng, không tự ẩn lỗi quan trọng |
| Backup/diagnostic rows | Export/import/probe, pending/result/error |

Không dùng một màu/badge chung cho “rule đã bật”, “đang trong lịch”, “quyền đã cấp” và “backend đang nối”. Đây là các trạng thái khác nhau.

## 6. Yêu cầu thị giác và tương tác

- Android native, gọn, dễ chạm. Có thể hiện đại hơn UI hiện tại; designer tự chọn palette, icon và đường nét nhất quán.
- Ưu tiên dark mode dịu mắt, đồng thời xuất light mode tương ứng. Material 3 là hệ tham chiếu triển khai, không bắt buộc sao chép nguyên default components.
- Không gradient/glow/shadow quá mức; không quá nhiều card lồng card. Khoảng cách phải tiết kiệm diện tích nhưng không chật.
- Text chính 14–16sp trở lên tùy vai trò, phân cấp rõ; header không chiếm nửa màn hình. Touch target ít nhất 48dp.
- Trạng thái có cả chữ/icon, không chỉ màu; tương phản dễ đọc. Không ẩn lý do khi switch bị khóa.
- Nội dung dài như package/class/file không tràn, không cắt mất nút chọn. Hỗ trợ font lớn và màn hẹp 360dp.
- Viết tiếng Việt đúng dấu, câu ngắn. Không lặp “bản thử nghiệm/chưa kiểm chứng” trên mọi hàng; giữ cảnh báo đúng nơi cần quyết định.
- Không fake waveform, thống kê pin, lịch sử thực thi hoặc tỷ lệ thành công vì chưa có dữ liệu.
- Không thêm tài khoản, cloud sync, quảng cáo, trả phí, dashboard phân tích, tính năng remap chưa chạy.
- Không cần trưng bày Binder, system_server, UID trong luồng dùng bình thường; thông tin này thuộc chẩn đoán.

## 7. Ảnh và thông số cần xuất để dựng lại

Xuất riêng từng màn hình, không chỉ một collage phối cảnh điện thoại. Dùng một kích thước logic nhất quán, ví dụ **412 × 915dp**, ảnh PNG ít nhất 2× (824 × 1830px) hoặc 3×. Không nhúng khung máy/đổ bóng bên ngoài vào ảnh nguồn. Kích thước này là khung thiết kế, không phải khẳng định độ phân giải thiết bị thật.

1. Bộ dark mode cho S01–S13. S13 có thể thành nhiều ảnh riêng để nhìn rõ thời gian nhận diện, sao lưu và chẩn đoán.
2. Các biến thể thiết yếu: trang chủ OFF; mất backend; thiếu quyền/từ chối; danh sách rule rỗng; lỗi lịch/xung đột; Activity không khả dụng; đang ghi; pin bị giới hạn.
3. Light mode ít nhất cho Tổng quan, Danh sách thao tác và Editor; cung cấp palette đầy đủ để áp dụng phần còn lại.
4. Một component sheet chứa các trạng thái quan trọng, icon và biến thể control.
5. Bảng token: màu HEX theo vai trò, font/family, size/weight/line-height, spacing, corner radius, kích thước icon/touch target, elevation. Token dark/light cần khớp giữa các màn.
6. Chú thích tương tác bằng văn bản hoặc bảng riêng: chạm vào đâu → mở gì; thành phần fixed hay scroll; trường nào chỉ xuất hiện theo lựa chọn. Không ghi các chú thích này lên mặt UI thành chữ thừa.
7. Đặt tên file theo ID, ví dụ `S01_overview_dark.png`, `S02_rules_empty_dark.png`, `S07_activity_picker_dark.png`. Nếu màn dài, xuất thêm ảnh phần dưới thay vì thu nhỏ chữ để nhét hết.

Trước khi bàn giao, kiểm tra một người mới có trả lời ngay được: remap tổng đang bật chưa, backend đang nối chưa, một lần bấm Plus hiện làm gì, vì sao rule không bật được, và dừng ghi âm ở đâu. Nếu chưa, cần sửa phân cấp và nhãn trước khi thêm trang trí.

## 8. Phạm vi của lượt thiết kế

Lượt này tạo thiết kế và ảnh, không thay logic nút, quyền, ghi âm hay cấu hình. Sau khi có ảnh, lập trình viên sẽ dựng lại UI Compose trên nền code đã lưu ở `v1.0.1`, giữ các chức năng Plus đang hoạt động và dữ liệu của người dùng. Bất kỳ đề xuất thêm chức năng ngoài danh mục phải nằm trong phụ lục riêng, không được lẫn vào bộ ảnh cơ sở như thể đã được triển khai.
