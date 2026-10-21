"""Maintain the VI (default) / EN resource parity from a reviewable table."""
from pathlib import Path
from xml.sax.saxutils import escape

STRINGS = {
"app_name": ("OPlusKeyControl", "OPlusKeyControl"),
"overview": ("Tổng quan", "Overview"), "mappings": ("Thao tác", "Mappings"), "settings": ("Cài đặt", "Settings"),
"disable": ("Tắt ngay", "Disable"), "emergency_disable": ("Tắt OPlusKeyControl", "Disable OPlusKeyControl"),
"enabled": ("Đã bật", "Enabled"), "disabled": ("Đã tắt", "Disabled"),
"add_rule": ("Thêm thao tác", "Add mapping"), "edit": ("Sửa", "Edit"), "delete": ("Xóa", "Delete"),
"dismiss": ("Đóng", "Dismiss"), "save": ("Lưu", "Save"), "cancel": ("Hủy", "Cancel"), "back": ("Quay lại", "Back"), "next": ("Tiếp tục", "Next"),
"plus": ("Plus Key", "Plus Key"), "volume_up": ("Tăng âm lượng", "Volume up"), "volume_down": ("Giảm âm lượng", "Volume down"), "power": ("Nguồn", "Power"),
"single": ("Nhấn một lần", "Single click"), "double_click": ("Nhấn đúp", "Double click"), "long_press": ("Nhấn giữ", "Long press"), "chord": ("Tổ hợp hai nút", "Two-button chord"),
"basic": ("Cơ bản", "Basic"), "shizuku": ("ADB / Shizuku", "ADB / Shizuku"), "root": ("Root / Vector", "Root / Vector"),
"torch_toggle": ("Bật / tắt đèn pin", "Toggle flashlight"), "torch_on": ("Bật đèn pin", "Flashlight on"), "torch_off": ("Tắt đèn pin", "Flashlight off"),
"open_app": ("Mở ứng dụng", "Open app"), "open_activity": ("Mở màn hình ứng dụng (Root)", "Open app activity (Root)"),
"stock_camera": ("Camera gốc: mở và chụp", "Stock camera: open and capture"), "recorder_toggle": ("Bắt đầu / dừng ghi âm", "Start / stop recording"),
"your_buttons": ("Nút bấm, theo cách của bạn", "Your buttons, your way"),
"overview_description": ("Cấu hình theo giờ. Mặc định không chiếm nút nào.", "Time-based mappings. No button is remapped by default."),
"capabilities": ("Khả năng trên thiết bị", "Device capabilities"),
"no_verified_oem_backend": ("Bản phát triển: Plus, Power và thay thế gesture OEM còn cần kiểm chứng trên firmware. Không bật hook hệ thống tự động.", "Development build: Plus, Power and OEM gesture replacement need firmware verification. No automatic system hooks."),
"now_preview": ("Bây giờ thao tác sẽ làm gì?", "What would happen now?"), "no_rules": ("Chưa có thao tác", "No mappings yet"),
"no_rules_description": ("Chọn nút, khung giờ và hành động. Có thể lưu bản nháp khi backend chưa sẵn sàng.", "Choose a button, schedule and action. Save a draft while the backend is unavailable."),
"pass_through": ("Giữ thao tác gốc của hệ thống", "Keep system behavior"),
"preview_logical": ("Đây là kết quả chọn rule; việc thực thi còn phụ thuộc quyền và backend. Làm mới để cập nhật giờ.", "Rule preview only; execution depends on permissions and backend. Refresh to update the current time."),
"refresh_preview": ("Làm mới thời điểm hiện tại", "Refresh current time"),
"all_times": ("Mọi lúc · rule mặc định", "All times · default rule"),
"schedule_range": ("Từ %1$s đến %2$s · [bắt đầu, kết thúc)", "%1$s to %2$s · [start, end)"),
"overnight_hint": ("Khung giờ qua nửa đêm; ngày chọn là ngày bắt đầu.", "Overnight window; selected weekdays are start days."),
"priority_value": ("Độ ưu tiên: %1$d", "Priority: %1$d"),
"conflict": ("Rule trùng lịch và độ ưu tiên. Hãy đổi giờ hoặc ưu tiên trước khi bật/lưu.", "Overlapping rules have equal priority. Change their time or priority before enabling/saving."),
"backend": ("Chế độ quyền", "Privilege mode"),
"backend_explanation": ("Đổi chế độ sẽ tắt remap. Không tự chuyển từ Shizuku sang root.", "Changing mode disables remapping. Shizuku never silently falls back to root."),
"basic_limitation": ("Cơ bản chỉ quan sát phím Volume. Chưa bật thay thế vì cần giữ nguyên long/double không được gán.", "Basic observes Volume keys only. Replacement is gated to preserve unassigned long/double gestures."),
"shizuku_limitation": ("Có phát hiện binder/quyền Shizuku thật. Chưa có adapter bắt và thay thế nút đã xác minh.", "Real Shizuku binder/permission detection. No verified button capture/replacement adapter yet."),
"root_limitation": ("Chưa xác minh API 101 và điểm hook firmware. Không nạp hook hay daemon trong bản này.", "API 101 and firmware hook points are unverified. This build loads no hooks or daemons."),
"shizuku_granted": ("Shizuku: đã cấp quyền", "Shizuku: permission granted"), "shizuku_running": ("Shizuku: đang chạy, chưa cấp quyền", "Shizuku: running, permission required"), "shizuku_missing": ("Shizuku: chưa kết nối", "Shizuku: disconnected"),
"request_shizuku": ("Yêu cầu quyền Shizuku", "Request Shizuku permission"),
"permissions": ("Quyền sử dụng", "Permissions"),
"permissions_description": ("Camera cho đèn pin; microphone cho ghi âm; thông báo hiển thị nút dừng. Bạn có thể từ chối từng quyền.", "Camera for flashlight; microphone for recording; notifications for a visible Stop action. Each permission is optional."),
"grant_permissions": ("Cấp quyền hành động", "Grant action permissions"), "enable_accessibility": ("Mở cài đặt Trợ năng", "Open Accessibility settings"), "app_settings": ("Thông tin ứng dụng / pin", "App info / battery"),
"accessibility_description": ("Quan sát phím âm lượng để chẩn đoán. Không đọc nội dung màn hình. Bản phát triển chưa thay thế thao tác hệ thống.", "Observe volume keys for diagnostics. No screen content access. This development build does not replace system gestures."),
"accessibility_connected": ("Dịch vụ Trợ năng đã kết nối", "Accessibility service connected"), "accessibility_disconnected": ("Dịch vụ Trợ năng chưa kết nối", "Accessibility service disconnected"),
"action_tests": ("Kiểm tra hành động thủ công", "Manual action tests"),
"tests_description": ("Các nút bên dưới thực hiện hành động ngay khi bạn chọn. Chỉ bắt đầu ghi âm khi màn hình ứng dụng đang mở.", "These buttons perform real actions when tapped. Recording starts only while this app is visible."),
"open_camera_only": ("Chỉ mở camera gốc (không chụp)", "Open stock camera only (no capture)"),
"camera_unverified": ("Tự chụp bằng camera gốc: cần kiểm chứng shutter và tín hiệu ảnh đã lưu. Chưa cho bật action này.", "Stock auto-capture needs verified readiness, shutter and saved-photo acknowledgement. This action cannot be enabled yet."),
"camera_unavailable": ("Không tìm thấy duy nhất một camera hệ thống phù hợp.", "No unambiguous system camera handler found."),
"activity_unverified": ("Root không đồng nghĩa UID/chữ ký ứng dụng đích. Chỉ hiển thị activity đã quét; launch đặc quyền chưa được kiểm chứng.", "Root does not grant the target UID/signature. Discovered activities are shown; privileged launch is unverified."),
"activity_identity": ("Quyền caller không tự đổi thành ứng dụng đích. Activity nội bộ/bảo vệ không thể chọn làm rule hoạt động.", "The caller does not become the target app. Internal/protected activities cannot be selected as working mappings."),
"action_available": ("Có thể yêu cầu qua Android API; chưa kiểm thử runtime trên máy này.", "Available through Android APIs; runtime on this device is untested."),
"camera_permission": ("Cần quyền Camera để điều khiển đèn pin.", "Camera permission is required for flashlight control."),
"torch_unavailable": ("Đèn pin không khả dụng hoặc camera đang chiếm dụng.", "Flashlight unavailable or camera is in use."),
"action_requested": ("Đã gửi yêu cầu hành động.", "Action requested."), "launch_requested": ("Đã gửi yêu cầu mở app; chưa xác nhận màn hình đích.", "App launch requested; target screen is not confirmed."),
"app_unavailable": ("App đã bị gỡ, tắt hoặc không có màn hình mở.", "App removed, disabled, or has no launcher activity."),
"unlock_required": ("Hãy mở khóa để mở ứng dụng. Không bỏ qua khóa màn hình.", "Unlock to open the app. The lock screen is preserved."),
"action_busy": ("Một hành động khác đang xử lý.", "Another action is in progress."), "action_failed": ("Không thực hiện được hành động. Kiểm tra quyền và trạng thái ứng dụng.", "Action failed. Check permissions and application state."),
"recording": ("Đang ghi âm", "Recording"), "recording_notice": ("Microphone đang hoạt động. Nhấn Dừng để lưu bản ghi.", "Microphone is active. Tap Stop to save."), "stop": ("Dừng và lưu", "Stop and save"),
"recorder_visible": ("Bắt đầu ghi âm từ ứng dụng đang hiển thị; chưa hỗ trợ khởi động khi khóa/tắt màn hình. Có thể dừng bất kể lịch đã hết.", "Start recording from the visible app. Locked/screen-off start is unverified. Stop remains available after the schedule ends."),
"record_error": ("Ghi âm lỗi; bản ghi có thể chưa được lưu. Kiểm tra quyền, microphone và dung lượng.", "Recording failed; the file may not be saved. Check permissions, microphone and storage."),
"recordings": ("Bản ghi của bạn", "Your recordings"), "recordings_description": ("Tối đa 50 bản gần nhất trong Music/OPlusKeyControl. Không tải lên mạng.", "Up to 50 recent recordings in Music/OPlusKeyControl. No uploads."), "no_recordings": ("Chưa có bản ghi đã lưu.", "No saved recordings."),
"timings": ("Thời gian nhận diện", "Gesture timing"), "timings_description": ("Áp dụng cho lõi gesture. Backend chưa xác minh sẽ không được kích hoạt bởi các tùy chỉnh này.", "Used by the gesture engine. These controls do not activate unverified backends."),
"double_ms": ("Nhấn đúp: %1$d ms", "Double click: %1$d ms"), "long_ms": ("Nhấn giữ: %1$d ms", "Long press: %1$d ms"), "chord_ms": ("Cửa sổ tổ hợp: %1$d ms", "Chord window: %1$d ms"), "debounce_ms": ("Chống dội: %1$d ms", "Debounce: %1$d ms"),
"backup": ("Sao lưu cấu hình", "Configuration backup"), "backup_description": ("Chỉ chứa rules và mã app/activity đã chọn, không chứa âm thanh. Khôi phục luôn tắt remap.", "Contains rules and selected app/activity identifiers, no audio. Restore always disables remapping."),
"export_rules": ("Xuất cấu hình", "Export configuration"), "import_rules": ("Khôi phục từ file", "Restore from file"),
"backup_done": ("Đã xuất cấu hình.", "Configuration exported."), "restore_done": ("Đã nhận yêu cầu khôi phục, remap được tắt. Kiểm tra danh sách rule.", "Restore requested with remapping disabled. Review the mapping list."), "backup_error": ("Không đọc/ghi được cấu hình hợp lệ.", "Unable to read/write a valid configuration."), "storage_error": ("Lỗi cấu hình. Remap mặc định tắt; kiểm tra lại dữ liệu trước khi bật.", "Configuration error. Remapping defaults off; review data before enabling."),
"diagnostics": ("Chẩn đoán", "Diagnostics"), "diagnostics_description": ("Chỉ đọc thông tin máy. Kiểm tra root chỉ chạy khi bạn chủ động chọn Root và nhấn nút.", "Read-only device information. A root probe runs only when Root is selected and you tap the button."),
"firmware": ("%1$s · Android %2$s / API %3$d\n%4$s", "%1$s · Android %2$s / API %3$d\n%4$s"),
"probe_root": ("Kiểm tra UID root (chỉ đọc)", "Check root UID (read only)"), "diagnostics_redacted": ("Không lưu serial, nội dung màn hình, app list hay log cá nhân. API Vector runtime chưa xác minh.", "No serial, screen content, installed-app lists or personal logs are stored. Vector runtime API is unverified."),
"rule_editor": ("Thiết lập thao tác", "Mapping setup"), "step_of": ("Bước %1$d / %2$d", "Step %1$d / %2$d"),
"choose_trigger": ("Chọn nút và cử chỉ", "Choose button and gesture"), "choose_schedule": ("Chọn thời gian hiệu lực", "Choose a schedule"), "choose_action": ("Chọn hành động", "Choose an action"), "review_rule": ("Kiểm tra và lưu", "Review and save"),
"second_button": ("Nút thứ hai", "Second button"), "power_safety": ("Power single/long luôn thuộc hệ thống. Tổ hợp chứa Power và hai phím Volume được bảo lưu.", "Power single/long remain with the system. Power chords and both-volume chords are reserved."),
"reserved_chord": ("Tổ hợp này được bảo lưu hoặc không hợp lệ.", "This chord is reserved or invalid."),
"use_schedule": ("Chỉ áp dụng theo khung giờ", "Use a time window"), "start_time": ("Bắt đầu %1$s", "Start %1$s"), "end_time": ("Kết thúc %1$s", "End %1$s"),
"schedule_explanation": ("Bao gồm giờ bắt đầu, loại trừ giờ kết thúc. Qua nửa đêm dùng thứ của ngày bắt đầu; giờ được tính theo múi giờ hiện tại lúc bấm.", "Includes start, excludes end. Overnight uses the start weekday. The current time zone is evaluated on each press."),
"invalid_schedule": ("Chọn ít nhất một ngày; giờ bắt đầu và kết thúc phải khác nhau.", "Choose at least one day; start and end must differ."),
"default_rule_hint": ("Rule mặc định chỉ được chọn khi không có rule theo giờ đang khớp.", "The default is selected only when no scheduled rule matches."),
"autostop_seconds": ("Tự dừng sau %1$d giây", "Auto-stop after %1$d seconds"), "save_disabled_hint": ("Chưa đủ điều kiện bật. Bạn có thể lưu thao tác dưới dạng tắt để hoàn thiện sau.", "Not ready to enable. Save a disabled mapping to complete later."), "enable_rule": ("Bật thao tác này", "Enable this mapping"),
"choose_app": ("Chọn ứng dụng", "Choose app"), "choose_activity": ("Chọn màn hình ứng dụng", "Choose activity"), "refresh": ("Làm mới", "Refresh"), "search_apps": ("Tìm theo tên", "Search by name"), "include_system": ("Gồm ứng dụng hệ thống", "Include system apps"), "advanced": ("Chi tiết kỹ thuật / Advanced", "Technical details / Advanced"), "scan_failed": ("Không quét/mở được mục này. Thử làm mới hoặc kiểm tra ứng dụng.", "Unable to scan/open this item. Refresh or check the app."), "no_results": ("Không tìm thấy mục phù hợp.", "No matching items."), "select": ("Chọn", "Select"),
"launcher_activity": ("Màn hình mở ứng dụng · có launcher", "Launcher activity"), "exported_unverified": ("Exported · cần xác minh extras / trạng thái", "Exported · extras/state unverified"), "internal_activity": ("Không hỗ trợ: activity nội bộ", "Unsupported: internal activity"), "protected_activity": ("Không hỗ trợ: activity có quyền bảo vệ", "Unsupported: permission-protected activity"), "disabled_activity": ("Không hỗ trợ: đã tắt", "Unsupported: disabled activity"),
"exported_value": ("Exported: %1$s", "Exported: %1$s"), "current_profile_only": ("Chỉ hồ sơ hiện tại; không giả định quyền giữa các user/profile.", "Current profile only; cross-user/profile access is not assumed."),
"test_action": ("Thử hành động ngay", "Test action now"),
"test_action_confirmation": ("Thực hiện ngay: %1$s. Thao tác này sẽ tác động thiết bị hoặc mở app đã chọn.", "Run now: %1$s. This affects the device or opens the selected app."),
}

# Duration minimum is 10; use unit notation to avoid incorrect plural grammar.
STRINGS["autostop_seconds"] = ("Tự dừng: %1$d s", "Auto-stop: %1$d s")

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1] / "app/src/main/res"
    for index, folder in enumerate(("values", "values-en")):
        out = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<resources>"]
        for name, pair in STRINGS.items():
            text = escape(pair[index]).replace("'", "\\'").replace("\n", "\\n")
            out.append(f'    <string name="{name}">{text}</string>')
        out.append("</resources>")
        (root / folder / "strings.xml").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Generated {len(STRINGS)} strings in VI and EN")
