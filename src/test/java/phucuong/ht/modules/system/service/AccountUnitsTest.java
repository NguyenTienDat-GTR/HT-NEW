package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountUnitsTest {

    private static Leader leaderInHierarchy(Diocese diocese, Deanery deanery, Parish parish) {
        deanery.setDiocese(diocese);
        parish.setDeanery(deanery);
        Leader leader = new Leader();
        leader.setParish(parish);
        return leader;
    }

    private static Diocese diocese(String name) {
        Diocese d = new Diocese();
        d.setId(UUID.randomUUID());
        d.setName(name);
        return d;
    }

    private static Deanery deanery(String name) {
        Deanery d = new Deanery();
        d.setId(UUID.randomUUID());
        d.setName(name);
        return d;
    }

    private static Parish parish(String name) {
        Parish p = new Parish();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    @Test
    void fallsBackToTheLeadersHierarchyWhenAccountHasNoUnits() {
        Diocese diocese = diocese("GP");
        Deanery deanery = deanery("GH");
        Parish parish = parish("GX");
        AccountUnits units = AccountUnits.of(new Account(), leaderInHierarchy(diocese, deanery, parish));

        assertEquals(diocese.getId(), units.dioceseId());
        assertEquals("GH", units.deaneryName());
        assertEquals(parish.getId(), units.parishId());
        assertEquals("GX", units.parishName());
    }

    @Test
    void accountOwnUnitsWinOverTheLeaders() {
        Account account = new Account();
        Diocese own = diocese("Của tài khoản");
        account.setDiocese(own);
        AccountUnits units = AccountUnits.of(account, leaderInHierarchy(diocese("GP khác"), deanery("GH"), parish("GX")));

        assertEquals("Của tài khoản", units.dioceseName());
        assertEquals(own.getId(), units.dioceseId());
        assertEquals("GH", units.deaneryName());
    }

    @Test
    void everythingIsNullWithoutAccountUnitsOrLeader() {
        AccountUnits units = AccountUnits.of(new Account(), null);

        assertNull(units.dioceseId());
        assertNull(units.deaneryName());
        assertNull(units.parishId());
    }

    @Test
    void leaderWithoutDeaneryOnlyYieldsParish() {
        Parish parish = parish("GX");
        Leader leader = new Leader();
        leader.setParish(parish);
        AccountUnits units = AccountUnits.of(new Account(), leader);

        assertEquals("GX", units.parishName());
        assertNull(units.deaneryId());
        assertNull(units.dioceseName());
    }
}
