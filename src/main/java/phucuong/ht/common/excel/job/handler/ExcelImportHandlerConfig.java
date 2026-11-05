package phucuong.ht.common.excel.job.handler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import phucuong.ht.common.excel.job.ExcelImportHandler;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.modules.deanery.service.DeaneryImportService;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.parish.service.ParishImportService;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;

/** Khai báo từng loại nhập Excel; mỗi handler gọi service import tương ứng. */
@Configuration
public class ExcelImportHandlerConfig {

    @Bean
    ExcelImportHandler leaderImport(LeaderImportService service) {
        return ExcelImportHandler.of(ExcelJobKind.IMPORT_LEADERS,
                (target, file, auth, progress, images) -> service.importLeaders(file, auth, progress, images));
    }

    @Bean
    ExcelImportHandler deaneryImport(DeaneryImportService service) {
        return ExcelImportHandler.of(ExcelJobKind.IMPORT_DEANERIES,
                (target, file, auth, progress, images) -> service.importDeaneries(file, auth, progress));
    }

    @Bean
    ExcelImportHandler parishImport(ParishImportService service) {
        return ExcelImportHandler.of(ExcelJobKind.IMPORT_PARISHES,
                (target, file, auth, progress, images) -> service.importParishes(file, auth, progress));
    }

    @Bean
    ExcelImportHandler participantImport(PastCourseParticipantImportService service) {
        return ExcelImportHandler.of(ExcelJobKind.IMPORT_PARTICIPANTS,
                (target, file, auth, progress, images) -> service.importParticipants(target, file, auth, progress, false, images));
    }

    @Bean
    ExcelImportHandler teamParticipantImport(PastCourseParticipantImportService service) {
        return ExcelImportHandler.of(ExcelJobKind.IMPORT_TEAM_PARTICIPANTS,
                (target, file, auth, progress, images) -> service.importParticipants(target, file, auth, progress, true, images));
    }
}
