-- Số điện thoại huynh trưởng là duy nhất (như email) để import danh sách tham dự có thể tìm huynh trưởng theo email hoặc SĐT.
-- Chuẩn hóa SĐT rỗng về NULL trước khi tạo ràng buộc (NULL không bị coi là trùng).
UPDATE leader SET phone_number = NULL WHERE phone_number IS NOT NULL AND btrim(phone_number) = '';
UPDATE leader SET phone_number = btrim(phone_number) WHERE phone_number IS NOT NULL AND phone_number <> btrim(phone_number);

CREATE UNIQUE INDEX IF NOT EXISTS uk_leader_phone_number
    ON leader (phone_number)
    WHERE phone_number IS NOT NULL;
