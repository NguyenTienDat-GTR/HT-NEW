package phucuong.ht.modules.mobile.push.sender;

import java.util.List;
import java.util.Map;

/** Cổng gửi thông báo đẩy tới thiết bị (hiện chỉ có Expo Push Service); thay được bằng bản giả khi kiểm thử. */
public interface PushSender {

    /** Gửi một lô tin; kết quả cùng thứ tự với đầu vào. Không ném lỗi: lỗi mạng trả về kết quả thất bại. */
    List<Result> send(List<Message> messages);

    record Message(String to, String title, String body, Map<String, Object> data, String channelId) {
    }

    /** error: mã lỗi của Expo (vd. DeviceNotRegistered) hoặc null nếu thành công / lỗi mạng không rõ mã. */
    record Result(String to, boolean ok, String error) {

        public boolean deviceNotRegistered() {
            return "DeviceNotRegistered".equals(error);
        }
    }
}
