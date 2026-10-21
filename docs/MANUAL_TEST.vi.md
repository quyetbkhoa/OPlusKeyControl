# Tự kiểm tra OPlusKeyControl — 2026-10-22

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
