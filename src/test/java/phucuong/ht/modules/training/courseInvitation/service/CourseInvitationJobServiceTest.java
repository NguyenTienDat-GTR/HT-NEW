package phucuong.ht.modules.training.courseInvitation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.JobResponse;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Result;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseInvitationJobServiceTest {

    private CourseInvitationService invitationService;
    private CourseInvitationAsyncRunner runner;
    private CourseInvitationJobService jobs;

    @BeforeEach
    void setUp() {
        invitationService = mock(CourseInvitationService.class);
        runner = mock(CourseInvitationAsyncRunner.class);
        jobs = new CourseInvitationJobService(invitationService, runner);
    }

    @Test
    void submitValidatesThenQueuesAPendingJob() {
        SendRequest request = new SendRequest(null, true, false);
        CourseInvitationJobService.Job job = jobs.submit("KH-1", request);

        verify(invitationService).validateForSend("KH-1", request);
        verify(runner).runJob(job, request);
        assertEquals(CourseInvitationJobService.Status.PENDING, job.getStatus());
        assertNotNull(job.getId());
        assertEquals("PENDING", jobs.toResponse(job).status());
    }

    @Test
    void invalidRequestIsRejectedBeforeAnyJobExists() {
        SendRequest request = new SendRequest(null, true, false);
        doThrow(new CommonValidationHelper(new securityUtils()).conflict("Phải bật điểm danh")).when(invitationService).validateForSend("KH-1", request);

        assertThrows(ApiException.class, () -> jobs.submit("KH-1", request));
        verify(runner, never()).runJob(any(), any());
    }

    @Test
    void jobsHaveUniqueIds() {
        SendRequest request = new SendRequest(null, false, false);
        assertNotEquals(jobs.submit("KH-1", request).getId(), jobs.submit("KH-1", request).getId());
    }

    @Test
    void jobsAreOnlyVisibleThroughTheirOwnCourse() {
        CourseInvitationJobService.Job job = jobs.submit("KH-1", new SendRequest(null, false, false));
        assertEquals(job, jobs.get("KH-1", job.getId()));
        assertNull(jobs.get("KH-2", job.getId()));
        assertNull(jobs.get("KH-1", "khong-co"));
    }

    @Test
    void responseCarriesResultOrErrorOnceFinished() {
        CourseInvitationJobService.Job done = jobs.submit("KH-1", new SendRequest(null, false, false));
        Result result = new Result(3, 2, 0, 1, false, List.of());
        done.markDone(result);
        JobResponse response = jobs.toResponse(done);
        assertEquals("DONE", response.status());
        assertEquals(result, response.result());
        assertNull(response.error());

        CourseInvitationJobService.Job failed = jobs.submit("KH-1", new SendRequest(null, false, false));
        failed.markFailed("SMTP không phản hồi");
        JobResponse failure = jobs.toResponse(failed);
        assertEquals("FAILED", failure.status());
        assertEquals("SMTP không phản hồi", failure.error());
        assertNull(failure.result());
    }

    @Test
    void asyncRunnerStoresTheResultOnTheJob() {
        CourseInvitationAsyncRunner real = new CourseInvitationAsyncRunner(invitationService);
        Result result = new Result(1, 1, 0, 0, true, List.of());
        when(invitationService.sendInvitations("KH-1", List.of("p-1"), true, true)).thenReturn(result);
        CourseInvitationJobService.Job job = new CourseInvitationJobService(invitationService, real)
                .submit("KH-1", new SendRequest(List.of("p-1"), true, true));
        assertEquals(CourseInvitationJobService.Status.DONE, job.getStatus());
        assertEquals(result, job.getResult());
    }

    @Test
    void asyncRunnerTurnsExceptionsIntoAFailedJobWithAMessage() {
        CourseInvitationAsyncRunner real = new CourseInvitationAsyncRunner(invitationService);
        when(invitationService.sendInvitations(eq("KH-1"), any(), anyBoolean(), anyBoolean())).thenThrow(new IllegalStateException("Hết kết nối"));
        CourseInvitationJobService.Job job = new CourseInvitationJobService(invitationService, real).submit("KH-1", new SendRequest(null, false, false));
        assertEquals(CourseInvitationJobService.Status.FAILED, job.getStatus());
        assertEquals("Hết kết nối", job.getError());

        when(invitationService.sendInvitations(eq("KH-1"), any(), anyBoolean(), anyBoolean())).thenThrow(new IllegalStateException());
        CourseInvitationJobService.Job silent = new CourseInvitationJobService(invitationService, real).submit("KH-1", new SendRequest(null, false, false));
        assertEquals("Không thể gửi email thông tin tham dự", silent.getError());
    }

    @Test
    void autoSendNeverPropagatesFailures() {
        CourseInvitationAsyncRunner real = new CourseInvitationAsyncRunner(invitationService);
        when(invitationService.sendInvitations(eq("KH-1"), any(), anyBoolean(), anyBoolean())).thenThrow(new IllegalStateException("lỗi"));
        real.autoSend("KH-1", List.of("p-1"));
        verify(invitationService).sendInvitations("KH-1", List.of("p-1"), true, false);
    }
}
