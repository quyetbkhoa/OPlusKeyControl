# Yêu cầu tiếp tục — 2026-10-22

Người dùng đã yêu cầu tiếp tục. Xem kết quả triển khai tại [UX_MP3_UPDATE.vi.md](UX_MP3_UPDATE.vi.md). Giữ tên OPlusKeyControl. Bỏ qua module powerkey đã được người dùng tắt thủ công.

## Kết quả test do người dùng xác nhận

- App đã hoạt động trên máy thật.
- Khi tắt màn hình, hành động bị trễ nếu app chưa được cấp chế độ pin “Không giới hạn”. Người dùng tự cấp chế độ này thì hoạt động tốt hơn. Đây là quan sát của người dùng, chưa có phép đo độ trễ.

## Các sửa đổi cần thực hiện lượt sau

1. Ghi âm phải xuất file MP3 thực sự, không chỉ đổi phần mở rộng. Kiểm tra encoder và khả năng phát lại; giữ các file ghi âm hiện có.
2. Cho phép bật riêng từng gesture của Plus Key. Không yêu cầu đủ nhấn đơn, nhấn đúp và nhấn giữ mới kích hoạt remap. Kiểm tra nguyên nhân điều kiện phủ đủ ba gesture, xử lý độ trễ phân biệt gesture và hành vi khi gesture không có rule; không tự bật các rule người dùng đã tắt.
3. Rút gọn chữ thừa trong app, giảm rối mắt; thiết kế lại các phần khó hiểu để trạng thái, công tắc và hành động trực quan hơn. Giữ thông tin cần thiết ở đúng chỗ, đưa giải thích dài vào trợ giúp hoặc phần mở rộng.
4. Xử lý trải nghiệm hoạt động khi tắt màn hình: kiểm tra cơ chế giữ backend/kết nối và giới hạn pin của ColorOS. Phản ánh đúng trạng thái pin, hướng dẫn hoặc mở trang cấp quyền “Không giới hạn” khi cần; không tự tuyên bố đã khắc phục delay nếu chưa test trên máy thật.

## Bổ sung từ người dùng

- Nút nguồn: hiện chỉ thêm được nhấn đúp, nhưng rule thêm xong không bật được. Cần kiểm tra điều kiện cho phép bật rule Power DOUBLE và hiển thị rõ nguyên nhân nếu bị chặn. Không áp dụng yêu cầu đủ ba gesture cho Power. Báo cáo lỗi này không tự cho phép thử Power/SOS hoặc bật lại module powerkey bên ngoài đã bị người dùng tắt.
- Chọn Activity: phải liệt kê Activity theo ứng dụng để người dùng chọn, thay vì bắt nhập tên component thủ công. Chỉ đưa Activity có thể khởi chạy vào lựa chọn thông thường; nếu có mục nâng cao nhập tay thì để tùy chọn.
- Đổi chế độ quyền: hiện chưa thể biết chuyển thành công hay thất bại, đang xin/kết nối quyền nào. Cần phân biệt chế độ được chọn với quyền/backend thực tế đang hoạt động; hiển thị đang kết nối, đã kết nối, bị từ chối hoặc lỗi cùng nguyên nhân có ích. Không báo thành công chỉ vì đã chọn chế độ hoặc mở trang cấp quyền.

## Cách tiếp tục (chi tiết)

- Đọc lại hướng dẫn dự án và mã hiện tại trước khi sửa; trạng thái thiết bị, TCP và APK có thể đã đổi.
- Giữ cấu hình rule, dữ liệu và các chức năng đang chạy. Không dùng thao tác ghi âm hoặc Power/SOS làm test tự động ngoài phạm vi đã được cho phép.
- Build/cài thành công không chứng minh hành vi phím thật. Báo riêng kết quả test khi màn hình bật/tắt và với từng gesture bật độc lập.
- Lượt lưu ghi chú ban đầu không thay code, không cài APK và không reboot điện thoại; lần triển khai tiếp theo được ghi riêng trong UX_MP3_UPDATE.vi.md.
