package phucuong.ht.modules.training.courseTeam.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.courseTeam.service.TeamDivisionPlanner.Gender;
import phucuong.ht.modules.training.courseTeam.service.TeamDivisionPlanner.Member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamDivisionPlannerTest {

    private static List<Member> members(int deaneries, int perDeanery, int femaleEvery) {
        List<Member> list = new ArrayList<>();
        int index = 0;
        for (int d = 0; d < deaneries; d++) {
            for (int i = 0; i < perDeanery; i++) {
                list.add(new Member("m" + index, "d" + d, index % femaleEvery == 0 ? Gender.FEMALE : Gender.MALE));
                index++;
            }
        }
        return list;
    }

    @Test
    void randomDivisionBalancesSizeAndGender() {
        List<Member> members = members(5, 20, 2); // 100 người, 50 nữ
        List<List<String>> teams = TeamDivisionPlanner.plan(members, 7, 15, null, new Random(1));
        Map<String, Gender> gender = new HashMap<>();
        members.forEach(member -> gender.put(member.id(), member.gender()));
        int assigned = 0;
        for (List<String> team : teams) {
            assigned += team.size();
            assertTrue(team.size() == 14 || team.size() == 15);
            long female = team.stream().filter(id -> gender.get(id) == Gender.FEMALE).count();
            assertTrue(Math.abs(female - team.size() / 2.0) <= 1.0, "lệch giới tính: " + female + "/" + team.size());
        }
        assertEquals(100, assigned);
    }

    @Test
    void unitLimitKeepsEachTeamWithinLimit() {
        List<Member> members = members(6, 12, 2); // 72 người, 6 giáo hạt
        Map<String, String> unit = new HashMap<>();
        members.forEach(member -> unit.put(member.id(), member.unit()));
        List<List<String>> teams = TeamDivisionPlanner.plan(members, 6, 12, 2, new Random(7));
        Set<String> all = new HashSet<>();
        for (List<String> team : teams) {
            assertEquals(12, team.size());
            Set<String> units = new HashSet<>();
            team.forEach(id -> units.add(unit.get(id)));
            assertTrue(units.size() <= 2, "đội có " + units.size() + " đơn vị");
            all.addAll(team);
        }
        assertEquals(72, all.size());
    }

    @Test
    void rejectsImpossibleConfigurations() {
        List<Member> members = members(6, 12, 2);
        assertThrows(IllegalArgumentException.class, () -> TeamDivisionPlanner.plan(members, 2, 12, null, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> TeamDivisionPlanner.plan(members, 2, 40, 2, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> TeamDivisionPlanner.plan(members, 100, 1, null, new Random(1)));
    }
}
