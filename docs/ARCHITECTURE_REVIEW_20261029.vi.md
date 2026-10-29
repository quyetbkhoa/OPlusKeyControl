# Đánh giá kiến trúc phục hồi và điều phối — 29/10/2026

Trace thực tế sau khởi động ghi DOWN `peer=true rev=0 owned=false`, UP `rev=5` sau đó: Binder đã nối nhưng cấu hình chưa được áp dụng tại thời điểm DOWN. Không đủ bằng chứng để kết luận nguyên nhân duy nhất là deep idle hoặc khẳng định lỗi đã hết.

Backend chỉ nhận phím khi policy đã được xác minh và lease cho phép. CONFIG được ACK sau khi áp dụng; phía app chỉ báo connected sau ACK revision hiện hành. Timeout bắt tay ban đầu và timeout ACK từng CONFIG dẫn đến reconnect có backoff. Callback cũ/revision cũ không được xác nhận. Trong thời gian mất quyền, mất kết nối, hoặc chưa nhận cấu hình đầu tiên, không thể bảo đảm nhấn đầu được xử lý: DOWN chưa sở hữu tiếp tục thuộc OEM, không buffer, giả lập hay phát lại phím. ACK không khôi phục được DOWN đó.

Busy là trạng thái điều phối, không còn thay đổi readiness được công bố. Yêu cầu action root vào hàng đợi FIFO giới hạn 16; tràn được ghi `queue-overflow`; yêu cầu chờ quá 5 giây bị từ chối và ghi `request-expired`. Executor dùng mutex để tuần tự hóa với cả action UI. Trước khi chạy, yêu cầu được kiểm tra lại peer, revision, quyền, quy tắc và lịch; yêu cầu cũ bị từ chối. Recorder vẫn theo đường riêng. Khi camera báo unavailable hay cấu hình/quyền đổi thật, revision vẫn thay đổi và có thể làm yêu cầu cũ bị từ chối; đây là fail-closed, không phải bảo đảm không mất hành động trong outage. Trạng thái và log hàng đợi không thay thế kiểm thử phím vật lý sau idle dài.


## Kiểm tra trên máy sau triển khai

218 JVM tests đạt; assemble/lint, audit DEX/scope và chữ ký APK đạt. Đã cài APK
artifacts/OPlusKeyControl-lifecycle-debug.apk và soft restart Zygote.
SHA-256: 2d110887eab2c838e85b976e2f9c43f50d9d3cac17d024049f408b592a0cfb63.
Readback xác nhận lifecycle-v3, connected/authorized/torchReady=true, features=23,
lastRequest=none khi màn hình Dozing: cấu hình đã ACK trước lần bấm tiếp theo.
Kill watcher 1224 -> watcher mới13488, app32283 giữ nguyên và tự khôi phục quyền.
Kill app32283 -> app14892 tự khôi phục kênh và ACK cấu hình, không mở Activity.
Kernel boot ID giữ nguyên; scope và file cấu hình giữ nguyên. Không thấy partial
wake lock OPlusKeyControl active sau kiểm tra. Bằng chứng nằm trong
private-evidence/idle-20261029/lifecycle-*.txt/json, không commit dữ liệu riêng.
Các kiểm tra này xác minh phục hồi tiến trình và ACK thực tế; chưa nghiệm thu
phím thật sau thời gian idle dài. Không tuyên bố lỗi vật lý đã hết hoàn toàn.
