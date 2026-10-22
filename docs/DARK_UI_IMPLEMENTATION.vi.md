# Dựng lại dark UI — 2026-10-22

Người dùng chốt dark mode và yêu cầu chuyển sang code. Mốc hoạt động `v1.0.1`
vẫn trỏ về `a35e8b4`; không ghi đè tag hoặc phát hành remote.

## Thay đổi

- Theme tối cố định: nền charcoal, card viền mảnh, xanh ngọc, màu trạng thái riêng;
  system bars dùng icon sáng. Không dùng dynamic color/light mode.
- Component Compose dùng chung trong `OpDesign.kt`: card, icon vector, tile,
  status pill, row, app bar; không dùng screenshot làm UI.
- Tổng quan: master riêng, trạng thái kết nối, ba kiểu nhấn Plus và hiệu lực
  hiện tại, banner ghi âm có dừng, nhắc pin có điều kiện.
- Thao tác: lọc nút, nhóm rule bật và bản nháp/chưa hỗ trợ, badge riêng cho ngoài
  lịch/chờ backend/thiếu quyền/chưa thử. Sửa/xóa qua menu, xóa có xác nhận.
  Khi bật rule trùng, UI nêu xung đột trước khi gửi thay đổi cho store.
- Editor bốn bước: chọn nút/cử chỉ, lịch, action có cấu hình phụ theo lựa chọn,
  review và thử hành động. Có Lưu bản nháp; ưu tiên vẫn 0–100. Sau thử Activity,
  trạng thái xác minh được cập nhật kể cả khi thông báo kết quả không đổi.
- Picker app và Activity: card, icon/tên/package, trạng thái launchability,
  loading/error/empty riêng biệt, giữ nguyên giới hạn protected/disabled.
- Cài đặt thành các trang con: quyền/kết nối, pin, bản ghi, thời gian nhận diện,
  sao lưu JSON, kiểm tra hành động, chẩn đoán, giới thiệu phiên bản APK thực tế.
- Bản ghi: tên file, badge định dạng thực tế, mở trình phát ngoài. Không có fake
  waveform, timer, lịch sử thực thi, xóa/chia sẻ hoặc thay đổi thư mục ghi âm.

## Phạm vi bảo toàn và kiểm tra

Đã chụp hash 31 file backend và domain trước khi sửa. Đối chiếu sau sửa không có
file nào đổi; phạm vi chức năng mới nằm ở MainActivity, UI và strings. Không sửa
RuleStore/schema, action executor, recorder/MP3 encoder, Binder, Vector hoặc policy.

Build runtime và bộ 110 JVM test đạt; lint không lỗi (còn cảnh báo, chủ yếu API
ẩn/dependency/unused resources). Kiểm tra strings không thiếu tham chiếu.
Đây là bằng chứng build/test; chưa thay thế kiểm tra bố cục và thao tác trên máy.
Lượt này chỉ dựng code và APK, không cài đè hoặc reboot điện thoại.

Tham chiếu ảnh và những chỗ phải sửa nội dung: `design/README.md`. Ảnh light mode
không nằm trong bộ tham chiếu được đưa vào repo sau khi người dùng đổi phạm vi.
