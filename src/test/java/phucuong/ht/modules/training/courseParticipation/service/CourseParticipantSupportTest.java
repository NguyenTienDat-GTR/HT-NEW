package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.TrainingTestFixtures;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseParticipantSupportTest {

    private final TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
    private final CourseParticipationRepository participationRepository = mock(CourseParticipationRepository.class);
    private final CourseParticipantSupport support = new CourseParticipantSupport(courseRepository, participationRepository,
            new CommonValidationHelper(new securityUtils()));

    private CourseParticipation participation(String id, String first, String team) {
        Leader leader = TrainingTestFixtures.leader("Giuse", "Nguyễn", first, first.toLowerCase() + "@x.vn", null);
        return TrainingTestFixtures.participation(id, leader, new TrainingCourse(), team);
    }

    @Test
    void requireRegularCourseReturnsTheCourse() {
        TrainingCourse course = new TrainingCourse();
        when(courseRepository.findByCodeWithType("KH-1")).thenReturn(Optional.of(course));
        assertEquals(course, support.requireRegularCourse("KH-1", "điểm danh"));
    }

    @Test
    void requireRegularCourseRejectsUnknownAndPastCourses() {
        ApiException missing = assertThrows(ApiException.class, () -> support.requireRegularCourse("KH-9", "điểm danh"));
        assertEquals(404, missing.getStatus().value());

        TrainingCourse past = new TrainingCourse();
        past.setPastCourse(true);
        when(courseRepository.findByCodeWithType("PAST")).thenReturn(Optional.of(past));
        ApiException error = assertThrows(ApiException.class, () -> support.requireRegularCourse("PAST", "thẻ tham dự"));
        assertEquals(400, error.getStatus().value());
        assertEquals("Khóa quá khứ không áp dụng thẻ tham dự", error.getMessage());
    }

    @Test
    void participantsAreApprovedActiveAndNotRejected() {
        CourseParticipation ok = participation("ok", "An", null);
        CourseParticipation pending = participation("pending", "Bình", null);
        pending.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        CourseParticipation rejected = participation("rejected", "Chi", null);
        rejected.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        rejected.setParticipationStatus(ParticipationStatus.REJECTED);
        CourseParticipation approvedButRejectedStatus = participation("odd", "Dũng", null);
        approvedButRejectedStatus.setParticipationStatus(ParticipationStatus.REJECTED);
        CourseParticipation disabled = participation("disabled", "Em", null);
        disabled.setStatus(Boolean.FALSE);
        CourseParticipation noLeader = participation("noleader", "Giang", null);
        noLeader.setLeader(null);
        CourseParticipation nullStatus = participation("nullstatus", "Hạnh", null);
        nullStatus.setStatus(null);
        when(participationRepository.findAllByCourseCode("KH-1"))
                .thenReturn(List.of(ok, pending, rejected, approvedButRejectedStatus, disabled, noLeader, nullStatus));

        List<String> ids = support.participantsOf("KH-1").stream().map(CourseParticipation::getId).toList();

        assertEquals(List.of("ok", "nullstatus"), ids);
        assertTrue(CourseParticipantSupport.isParticipant(ok));
        assertFalse(CourseParticipantSupport.isParticipant(pending));
        assertFalse(CourseParticipantSupport.isParticipant(rejected));
        assertFalse(CourseParticipantSupport.isParticipant(disabled));
        assertFalse(CourseParticipantSupport.isParticipant(noLeader));
    }

    @Test
    void ordersByNaturalTeamOrderThenNameWithUnassignedLast() {
        List<CourseParticipation> list = new ArrayList<>(List.of(
                participation("a", "Zeta", "Đội 10"),
                participation("b", "Alpha", null),
                participation("c", "Beta", "Đội 2"),
                participation("d", "Alpha", "Đội 2"),
                participation("e", "Alpha", " "),
                participation("f", "Cuong", "Đội 1")));
        list.sort(CourseParticipantSupport.byTeamThenName());
        assertEquals(List.of("f", "d", "c", "a", "b", "e"), list.stream().map(CourseParticipation::getId).toList());
    }

    @Test
    void namesAreSortedByGivenNameBeforeFamilyName() {
        Leader first = TrainingTestFixtures.leader("G", "Trần", "An", "a@x.vn", null);
        Leader second = TrainingTestFixtures.leader("G", "Lê", "Bình", "b@x.vn", null);
        List<CourseParticipation> list = new ArrayList<>(List.of(
                TrainingTestFixtures.participation("2", second, new TrainingCourse(), "Đội 1"),
                TrainingTestFixtures.participation("1", first, new TrainingCourse(), "Đội 1")));
        list.sort(CourseParticipantSupport.byTeamThenName());
        assertEquals("1", list.get(0).getId());
    }

    @Test
    void vietnameseAccentsSortNextToTheirBaseLetter() {
        List<CourseParticipation> list = new ArrayList<>(List.of(
                participation("z", "Zeta", null), participation("d", "Đạt", null), participation("a", "Ân", null), participation("b", "Bảo", null)));
        list.sort(CourseParticipantSupport.byTeamThenName());
        assertEquals(List.of("a", "b", "d", "z"), list.stream().map(CourseParticipation::getId).toList());
    }

    @Test
    void naturalCompareHandlesNumbersAndText() {
        assertTrue(CourseParticipantSupport.naturalCompare("Đội 2", "Đội 10") < 0);
        assertTrue(CourseParticipantSupport.naturalCompare("Đội 10", "Đội 2") > 0);
        assertEquals(0, CourseParticipantSupport.naturalCompare("Đội 3", "Đội 3"));
        assertTrue(CourseParticipantSupport.naturalCompare("Đội 1", "Đội 1A") < 0);
        assertTrue(CourseParticipantSupport.naturalCompare("Alpha", "beta") < 0);
        assertTrue(CourseParticipantSupport.naturalCompare("Đội 99999999999999999999", "Đội 100000000000000000000") < 0);
    }

    @Test
    void namesJoinHolyNameFamilyAndGivenNameSkippingBlanks() {
        Leader leader = TrainingTestFixtures.leader(" Giuse ", "Nguyễn Văn", "An", "a@x.vn", null);
        assertEquals("Giuse Nguyễn Văn An", CourseParticipantSupport.fullName(leader));
        assertEquals("Nguyễn Văn An", CourseParticipantSupport.plainName(leader));

        Leader sparse = TrainingTestFixtures.leader(null, " ", "An", "a@x.vn", null);
        assertEquals("An", CourseParticipantSupport.fullName(sparse));
        assertEquals("", CourseParticipantSupport.fullName(null));
        assertEquals("", CourseParticipantSupport.plainName(null));
    }
}
