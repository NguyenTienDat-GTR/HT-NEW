package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationCandidateLevelFilterTest {

    @Test
    void noFilterKeepsEveryone() {
        assertTrue(CourseParticipationService.matchesLevelFilter(null, "HT_I"));
        assertTrue(CourseParticipationService.matchesLevelFilter(null, null));
    }

    @Test
    void filterKeepsOnlyLeadersOfThatLevel() {
        assertTrue(CourseParticipationService.matchesLevelFilter("HT_I", "HT_I"));
        assertFalse(CourseParticipationService.matchesLevelFilter("HT_I", "HT_II"));
    }

    @Test
    void leadersWithoutALevelCountAsNone() {
        assertTrue(CourseParticipationService.matchesLevelFilter("NONE", null));
        assertTrue(CourseParticipationService.matchesLevelFilter("NONE", "NONE"));
        assertFalse(CourseParticipationService.matchesLevelFilter("HT_I", null));
    }
}
