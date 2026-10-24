package phucuong.ht.modules.badge.export.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeExportJobServiceTest {

    private BadgeExportService exportService;
    private BadgeExportJobService jobs;

    private final BadgeExportRequest request = new BadgeExportRequest(null, null, null, null, null);

    @BeforeEach
    void setUp() {
        exportService = mock(BadgeExportService.class);
        // Runner chạy đồng bộ trong test (bản thật chạy nền qua @Async).
        jobs = new BadgeExportJobService(new BadgeExportAsyncRunner(exportService));
    }

    @Test
    void successfulExportMarksTheJobDoneWithTheFile() {
        when(exportService.export("KH-1", request)).thenReturn(new BadgeExportService.PdfExport(new byte[]{37, 80, 68, 70}, "the.pdf", 12, 4));

        BadgeExportJobService.Job job = jobs.submit("KH-1", request);

        assertEquals(BadgeExportJobService.Status.DONE, job.getStatus());
        assertEquals("the.pdf", job.getFilename());
        assertEquals(12, job.getBadgeCount());
        assertEquals(4, job.getPageCount());
        assertNotNull(job.getContent());
        verify(exportService).export("KH-1", request);
    }

    @Test
    void failuresBecomeAMessageOnTheJob() {
        when(exportService.export(any(), any())).thenThrow(new IllegalStateException("Không có hồ sơ"));
        assertEquals("Không có hồ sơ", jobs.submit("KH-1", request).getError());

        org.mockito.Mockito.doThrow(new IllegalStateException()).when(exportService).export(any(), any());
        BadgeExportJobService.Job job = jobs.submit("KH-1", request);
        assertEquals(BadgeExportJobService.Status.FAILED, job.getStatus());
        assertEquals("Không thể tạo PDF thẻ tham dự", job.getError());
    }

    @Test
    void jobsAreScopedToTheirCourse() {
        when(exportService.export(any(), any())).thenReturn(new BadgeExportService.PdfExport(new byte[1], "a.pdf", 1, 1));
        BadgeExportJobService.Job job = jobs.submit("KH-1", request);
        assertSame(job, jobs.get("KH-1", job.getId()));
        assertNull(jobs.get("KH-2", job.getId()));
        assertNull(jobs.take("KH-2", job.getId()));
        assertNull(jobs.get("KH-1", "unknown"));
    }

    @Test
    void takeRemovesFinishedJobsSoTheFileIsDownloadedOnce() {
        when(exportService.export(any(), any())).thenReturn(new BadgeExportService.PdfExport(new byte[1], "a.pdf", 1, 1));
        BadgeExportJobService.Job job = jobs.submit("KH-1", request);
        assertSame(job, jobs.take("KH-1", job.getId()));
        assertNull(jobs.take("KH-1", job.getId()));
        assertNull(jobs.get("KH-1", job.getId()));
    }

    @Test
    void failedJobsStayReadableAfterTake() {
        when(exportService.export(any(), any())).thenThrow(new IllegalStateException("lỗi"));
        BadgeExportJobService.Job job = jobs.submit("KH-1", request);
        assertSame(job, jobs.take("KH-1", job.getId()));
        assertSame(job, jobs.get("KH-1", job.getId()));
    }
}
