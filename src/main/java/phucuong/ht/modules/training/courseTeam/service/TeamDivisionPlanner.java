package phucuong.ht.modules.training.courseTeam.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Thuật toán chia đội (không phụ thuộc DB). Sĩ số các đội chênh nhau không quá 1 và không vượt teamSize.
 * Giới tính được chia theo hạn ngạch từng đội theo tỉ lệ nam / nữ của toàn bộ danh sách nên mỗi đội xấp xỉ cân bằng nhất có thể.
 * Có maxUnitsPerTeam thì mỗi đội chỉ gồm hồ sơ của tối đa N đơn vị: ưu tiên dồn cùng một đơn vị vào cùng đội; thử nhiều lần với thứ tự ngẫu nhiên khác nhau.
 */
final class TeamDivisionPlanner {

    enum Gender { FEMALE, MALE, OTHER }

    record Member(String id, String unit, Gender gender) {
    }

    private static final int MAX_ATTEMPTS = 300;
    private static final Gender[] ALLOCATION_ORDER = {Gender.FEMALE, Gender.OTHER, Gender.MALE};

    private TeamDivisionPlanner() {
    }

    /** Trả về danh sách id hồ sơ của từng đội (theo thứ tự đội). Ném IllegalArgumentException (thông báo tiếng Việt) khi không chia được. */
    static List<List<String>> plan(List<Member> members, int teamCount, int teamSize, Integer maxUnitsPerTeam, Random random) {
        int total = members.size();
        if (total == 0) {
            throw new IllegalArgumentException("Danh sách tham dự chưa có hồ sơ nào để chia đội");
        }
        if (teamCount > total) {
            throw new IllegalArgumentException("Số đội (" + teamCount + ") không được lớn hơn tổng số hồ sơ (" + total + ")");
        }
        if ((long) teamCount * teamSize < total) {
            throw new IllegalArgumentException("Số đội x số hồ sơ mỗi đội (" + (long) teamCount * teamSize + ") không đủ chứa " + total + " hồ sơ");
        }
        if (maxUnitsPerTeam != null) {
            Set<String> units = new HashSet<>();
            members.forEach(member -> units.add(member.unit()));
            if (units.size() > (long) teamCount * maxUnitsPerTeam) {
                throw new IllegalArgumentException("Danh sách có " + units.size() + " đơn vị nhưng " + teamCount + " đội chỉ chứa tối đa "
                        + (long) teamCount * maxUnitsPerTeam + " đơn vị; hãy tăng số đơn vị tối đa mỗi đội hoặc tăng số đội");
            }
        }
        for (int attempt = 0; attempt < (maxUnitsPerTeam == null ? 1 : MAX_ATTEMPTS); attempt++) {
            List<List<String>> result = tryPlan(members, teamCount, maxUnitsPerTeam, random);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Không thể chia để mỗi đội chỉ có tối đa " + maxUnitsPerTeam
                + " đơn vị với sĩ số và tỉ lệ nam nữ cân bằng; hãy tăng số đơn vị tối đa mỗi đội");
    }

    private static List<List<String>> tryPlan(List<Member> members, int teamCount, Integer maxUnits, Random random) {
        int total = members.size();
        int[] capacity = new int[teamCount];
        int base = total / teamCount;
        int extra = total % teamCount;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        for (int i = 0; i < teamCount; i++) {
            capacity[order.get(i)] = base + (i < extra ? 1 : 0);
        }

        Map<Gender, Integer> genderTotals = new HashMap<>();
        for (Member member : members) {
            genderTotals.merge(member.gender(), 1, Integer::sum);
        }
        int[][] quota = new int[Gender.values().length][teamCount];
        int[] remainingCapacity = capacity.clone();
        int remainingTotal = total;
        for (Gender gender : ALLOCATION_ORDER) {
            int count = genderTotals.getOrDefault(gender, 0);
            allocate(quota[gender.ordinal()], remainingCapacity, count, remainingTotal, random);
            for (int t = 0; t < teamCount; t++) {
                remainingCapacity[t] -= quota[gender.ordinal()][t];
            }
            remainingTotal -= count;
        }

        // Gom hồ sơ theo đơn vị rồi theo giới tính; khi giới hạn đơn vị, đơn vị đông xếp trước để dễ dồn vào cùng đội.
        Map<String, Map<Gender, List<Member>>> byUnit = new HashMap<>();
        for (Member member : members) {
            byUnit.computeIfAbsent(maxUnits == null ? "" : member.unit(), key -> new HashMap<>())
                    .computeIfAbsent(member.gender(), key -> new ArrayList<>()).add(member);
        }
        List<String> units = new ArrayList<>(byUnit.keySet());
        Collections.shuffle(units, random);
        if (maxUnits != null) {
            units.sort((left, right) -> Integer.compare(size(byUnit.get(right)), size(byUnit.get(left))));
        }

        List<List<String>> teams = new ArrayList<>();
        List<Set<String>> teamUnits = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            teams.add(new ArrayList<>());
            teamUnits.add(new HashSet<>());
        }
        for (String unit : units) {
            for (Gender gender : ALLOCATION_ORDER) {
                List<Member> bucket = byUnit.get(unit).get(gender);
                if (bucket == null) {
                    continue;
                }
                Collections.shuffle(bucket, random);
                for (Member member : bucket) {
                    int team = pickTeam(quota[gender.ordinal()], teamUnits, maxUnits, unit, random);
                    if (team < 0) {
                        return null;
                    }
                    quota[gender.ordinal()][team]--;
                    teams.get(team).add(member.id());
                    teamUnits.get(team).add(unit);
                }
            }
        }
        return teams;
    }

    private static int size(Map<Gender, List<Member>> genders) {
        return genders.values().stream().mapToInt(List::size).sum();
    }

    // Chia count phần tử vào các đội theo tỉ lệ sức chứa còn lại (phần dư theo phần thập phân lớn nhất, hòa thì ngẫu nhiên).
    private static void allocate(int[] target, int[] remainingCapacity, int count, int remainingTotal, Random random) {
        int teamCount = target.length;
        double[] fraction = new double[teamCount];
        int assigned = 0;
        for (int t = 0; t < teamCount; t++) {
            double exact = remainingTotal == 0 ? 0 : (double) remainingCapacity[t] * count / remainingTotal;
            target[t] = (int) Math.floor(exact);
            fraction[t] = exact - target[t] + random.nextDouble() * 1e-6;
            assigned += target[t];
        }
        List<Integer> candidates = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            candidates.add(t);
        }
        candidates.sort((left, right) -> Double.compare(fraction[right], fraction[left]));
        int index = 0;
        while (assigned < count) {
            int t = candidates.get(index % teamCount);
            if (target[t] < remainingCapacity[t]) {
                target[t]++;
                assigned++;
            }
            index++;
        }
    }

    // Đội còn hạn ngạch giới tính này; ưu tiên đội đã có đơn vị của hồ sơ, sau đó đội còn nhiều chỗ nhất (và còn được thêm đơn vị mới).
    private static int pickTeam(int[] genderQuota, List<Set<String>> teamUnits, Integer maxUnits, String unit, Random random) {
        int best = -1;
        double bestScore = -1;
        for (int t = 0; t < genderQuota.length; t++) {
            if (genderQuota[t] <= 0) {
                continue;
            }
            double score;
            if (maxUnits == null) {
                score = random.nextDouble();
            } else if (teamUnits.get(t).contains(unit)) {
                score = 2 + genderQuota[t] + random.nextDouble() * 0.5;
            } else if (teamUnits.get(t).size() < maxUnits) {
                score = 1 + genderQuota[t] / 1000.0 + random.nextDouble() * 0.0005;
            } else {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }
}
