package phucuong.ht.modules.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ToggleNotificationDispatcherTest {

    @Mock
    private TrainingNotificationService trainingNotificationService;

    @Test
    void dispatchAccountRoleDisabledShouldUseNaturalVietnameseMessage() {
        ToggleNotificationDispatcher dispatcher = new ToggleNotificationDispatcher(trainingNotificationService);

        dispatcher.dispatch(
                Set.of("target"),
                "ACCOUNT_ROLE_TOGGLED",
                "Huynh trưởng Xứ (HT_XU)",
                false,
                "admin"
        );

        ArgumentCaptor<TrainingNotificationCommand> commandCaptor = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService).notifyUsers(eq(List.of("target")), commandCaptor.capture());

        TrainingNotificationCommand command = commandCaptor.getValue();
        assertEquals("Vai trò gắn với tài khoản của bạn đã bị vô hiệu hóa", command.title());
        assertTrue(command.message().contains("Vai trò Huynh trưởng Xứ (HT_XU) gắn với tài khoản của bạn đã bị vô hiệu hóa bởi admin"));
        assertTrue(command.message().contains("Bạn có thể mất quyền truy cập vào các chức năng thuộc vai trò này."));
    }

    @Test
    void dispatchUnitDisabledShouldExplainLimitedAccessInVietnamese() {
        ToggleNotificationDispatcher dispatcher = new ToggleNotificationDispatcher(trainingNotificationService);

        dispatcher.dispatch(
                Set.of("target"),
                "UNIT_STATUS_TOGGLED",
                "Giáo xứ Phú Cường",
                false,
                "admin"
        );

        ArgumentCaptor<TrainingNotificationCommand> commandCaptor = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService).notifyUsers(eq(List.of("target")), commandCaptor.capture());

        TrainingNotificationCommand command = commandCaptor.getValue();
        assertEquals("Đơn vị của bạn đã bị tạm ngưng", command.title());
        assertTrue(command.message().contains("Giáo xứ Phú Cường của bạn đã bị tạm ngưng bởi admin"));
        assertTrue(command.message().contains("bạn chỉ có thể đăng nhập và đọc thông báo"));
    }
}
