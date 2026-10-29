# Plus + nguồn: chặn nhấn đơn nguồn của tổ hợp

2026-10-25. Người dùng báo cả hai thứ tự của Plus + nguồn đều không đạt,
màn hình bị tắt; Power double vẫn hoạt động. Bản này chưa có xác nhận nút thật
tại thời điểm viết, không xem kết quả JVM là nghiệm thu vật lý.

Đối chiếu DEX services.jar của firmware CPH2745_17.0.0.102(EX01):
PowerKeyRule.onKeyGesture nhận ordinary/completed/count=1 rồi gọi onPress;
onPress chuyển đến IInputExt.powerPress cho nhánh nhấn đơn nguồn. Vì cặp Power
đã đưa cho OEM trước khi Plus được nhận vẫn cần có UP để cleanup, bản trước
không chặn callback nhấn đơn của chính tổ hợp đó.

Thay đổi:

- PlusPolicySession chỉ tạo claim Power downTime khi một Plus/Power chord
  thực sự resolve thành action; cả Plus-first và Power-first có test.
- PowerChordSuppression chỉ chặn type=0, action=1, count=1 với đúng downTime
  đã claim, timeout 5 giây, tối đa 8 claims; duplicate callbacks vẫn bị chặn.
- API 37 kiểm tra claim trước nhánh Power multi-press hiện có trong semantic
  callback. API 36 thêm guard onPress(J,I). Không chặn double/triple/long,
  cancellation hoặc count>=4; raw OEM cleanup vẫn giữ nguyên.
- Thu hồi kết nối xóa claims. Không phát lại phím hay sửa cấu hình nguồn OEM.
- Thêm trace 16 sự kiện Plus/Power gần nhất trong RAM, đưa qua Binder trên
  worker để đọc bằng Service.dump. Chỉ gồm thời gian, key/scan, driver, edge,
  cancel và consume; không có package/Activity đích, không ghi đĩa trên hook.
  Dấu chord-single-v1 giúp xác nhận hook mới thực sự đã được nạp.

Xác minh: 207 JVM tests đạt, runtime assemble/lint thành công, audit DEX/scope
và chữ ký APK đạt. APK cài qua adb install -r; reload riêng module OPlusKeyControl
với scope giữ nguyên rồi soft restart Zygote. Readback có chord-single-v1,
connected=true, authorized=true, features=23, oom_score_adj=-700.
Cấu hình trước/sau giống từng byte.

APK: artifacts/OPlusKeyControl-plus-power-screen-fix-debug.apk
SHA-256: 7468f7e0d37d5ef6afb5c9a94737f4ed060f4212d1f5fd66f4b1778869038282.
Bằng chứng local: private-evidence/plus-power-screen-off/.
