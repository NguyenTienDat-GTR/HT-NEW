package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Email của các tài khoản SUPER_ADMIN đang hoạt động: tài khoản gắn Huynh Trưởng dùng email của Huynh Trưởng. */
@Component
@RequiredArgsConstructor
public class SuperAdminEmailResolver {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final JdbcTemplate jdbc;

    public List<String> emails() {
        return jdbc.queryForList("""
                SELECT DISTINCT lower(trim(COALESCE(NULLIF(trim(l.email), ''), NULLIF(trim(a.email), '')))) AS email
                FROM account a
                JOIN account_role ar ON ar.username = a.username AND ar.status = true
                LEFT JOIN leader l ON l.id = a.leader_id
                WHERE ar.role_code = ?
                  AND a.status = true
                  AND COALESCE(NULLIF(trim(l.email), ''), NULLIF(trim(a.email), '')) IS NOT NULL
                ORDER BY email
                """, String.class, SUPER_ADMIN_ROLE);
    }
}
