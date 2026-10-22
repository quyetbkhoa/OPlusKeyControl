# Power nhấn đúp và tổ hợp Plus — candidate 22/10/2026

Đây là bản opt-in, giữ giao diện dark và cấu hình hiện có. Chỉ build khi bật cả
`opcRuntime=true` và `opcAdvancedInputs=true`; bản runtime mặc định không tự bật
hook mới. Người dùng đã chấp thuận cài, kích hoạt và soft reboot cho candidate này.

## Phạm vi

- Power: nhấn đúp, qua callback cử chỉ của PhoneWindowManager. Không consume
  raw Power, không inject/replay phím. Khi rule thực sự sẵn sàng, nhấn đơn phải
  chờ cửa sổ phân biệt nhiều lần của hệ thống. Nếu ROM bật early short press,
  tính năng Power bị loại khỏi capability thực tế.
- Tổ hợp: giữ Plus trước rồi nhấn Volume+ hoặc Volume−. Volume trước Plus,
  Volume đơn, tổ hợp chứa Power và Volume+ cùng Volume− chưa được hỗ trợ.
- Nếu có rule Plus nhấn giữ sẵn sàng, phải nhấn Volume trước khi hành động giữ
  được nhận diện. Nếu không có rule giữ, Plus có thể tiếp tục làm phím bổ trợ
  sau ngưỡng nhấn giữ. Không phát thêm hành động Plus sau một tổ hợp đã nhận.
- Master, tier, rule/action đã xác minh, lịch, lease và kết nối Binder vẫn là
  điều kiện bắt buộc. Không tự bật hoặc sửa rule của người dùng.

Power dùng timeout native, không dùng giá trị nhấn đúp Plus trong UI. Chỉ khi
rule Power sẵn sàng mới nâng max multi-press lên ít nhất 5; chỉ callback count=2
khớp chuỗi DOWN/UP vật lý mới có thể thay thế. Count khác tiếp tục gọi OEM.
Không hook onPress/onLongPress/onVeryLongPress. Đây là giới hạn thiết kế/code;
không phải bằng chứng nghiệm thu SOS hoặc hành vi khẩn cấp trên thiết bị.

Binder Policy.v3 báo riêng các bit Plus, Power double và Plus/Volume. UI phân
biệt cấu hình được với backend thực sự sẵn sàng. Khi mất kết nối/lease, input
mới trả về OEM; các cặp Volume đã chiếm được đóng và chuỗi chờ bị hủy.

## Build và bằng chứng

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PopcRuntime=true -PopcAdvancedInputs=true --console=plain
python tools/package_debug.py --runtime --advanced-inputs
```

139 JVM tests đạt; lint 0 lỗi, 50 cảnh báo. Bao gồm timeout, dedup, thiết bị
khác, repeat, cancel, mất lease/kết nối, queue đầy, count khác 2, Volume độc lập,
thứ tự tổ hợp và tránh phát cả LONG lẫn CHORD.

APK `artifacts/OPlusKeyControl-power-combos-debug.apk`, SHA-256:
`58dd163e0e5fcfca6f4550ba8016144630760ddafb25896be5f0b07a6a1a3f1d`.
Đã cài bằng install -r qua TCP; hash APK trên máy khớp. Cấu hình trước/sau cài
giống từng byte. Đã soft reboot bằng `setprop ctl.restart zygote`: kernel boot ID
giữ nguyên, system_server đổi PID, TCP kết nối lại được. Vector ghi nhận nạp
module thành công trong system_server mới; BackendKeeperService được bind bởi
android/UID 1000. UI hiển thị các rule Plus sẵn sàng.

Log khởi tạo RUNTIME_POWER_READY không còn trong logcat khi đọc sau boot; không
suy diễn việc nạp module/bind service thành bằng chứng hook Power đã thành công.
Chờ người dùng thử Power nhấn đúp và hai tổ hợp trên. Chưa nghiệm thu nút thật,
screen-off/deep sleep hoặc SOS cho candidate mới. Không tự phát phím Power,
ghi âm, chụp ảnh hay sửa rule trong lượt cài. Dừng thao tác UI khi nhận thấy
người dùng đang sử dụng điện thoại đồng thời.

## Khôi phục

Nếu có sự cố, tắt riêng module `dev.opluskeycontrol` trong Vector; không thay
đổi module PowerKey bên ngoài hoặc module khác. APK dark trước thay đổi nằm ở
`artifacts/OPlusKeyControl-dark-ui-debug.apk`, SHA-256
`db3786a9b98703f37ba99daa77e4d9e2bc4f7aca37b9d75641880a467fb24530`.
Cần soft reboot để bỏ mã hook đã nạp trong system_server sau khi thay APK.
Chi tiết cứu hộ: [RECOVERY.md](RECOVERY.md).
