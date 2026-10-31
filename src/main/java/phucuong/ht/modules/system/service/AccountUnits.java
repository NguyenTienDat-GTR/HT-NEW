package phucuong.ht.modules.system.service;

import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.UUID;

/**
 * Giáo phận / giáo hạt / giáo xứ của một tài khoản: ưu tiên đơn vị gán trực tiếp cho tài khoản,
 * thiếu thì suy ra từ giáo xứ của huynh trưởng gắn với tài khoản.
 */
record AccountUnits(Diocese diocese, Deanery deanery, Parish parish) {

    static AccountUnits of(Account account, Leader leader) {
        Parish leaderParish = leader == null ? null : leader.getParish();
        Deanery leaderDeanery = leaderParish == null ? null : leaderParish.getDeanery();
        Diocese leaderDiocese = leaderDeanery == null ? null : leaderDeanery.getDiocese();
        return new AccountUnits(
                account.getDiocese() != null ? account.getDiocese() : leaderDiocese,
                account.getDeanery() != null ? account.getDeanery() : leaderDeanery,
                account.getParish() != null ? account.getParish() : leaderParish);
    }

    UUID dioceseId() {
        return diocese == null ? null : diocese.getId();
    }

    String dioceseName() {
        return diocese == null ? null : diocese.getName();
    }

    UUID deaneryId() {
        return deanery == null ? null : deanery.getId();
    }

    String deaneryName() {
        return deanery == null ? null : deanery.getName();
    }

    UUID parishId() {
        return parish == null ? null : parish.getId();
    }

    String parishName() {
        return parish == null ? null : parish.getName();
    }
}
