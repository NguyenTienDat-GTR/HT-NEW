package phucuong.ht.common.layoutconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayoutConfigVersionServiceTest {

    private JdbcTemplate jdbc;
    private LayoutConfigVersionService service;
    private final UUID configId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new LayoutConfigVersionService(jdbc, new ObjectMapper());
    }

    @Test
    void recordsTheNextVersionNumberAsJson() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("CERTIFICATE"), eq(configId))).thenReturn(3);

        service.record("CERTIFICATE", configId, Map.of("name", "Mẫu A"), "admin");

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("insert into layout_config_version"), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertEquals("CERTIFICATE", args.getAllValues().get(1));
        assertEquals(4, args.getAllValues().get(3));
        assertTrue(args.getAllValues().get(4).toString().contains("Mẫu A"));
        assertEquals("admin", args.getAllValues().get(5));
    }

    @Test
    void trimsVersionsOlderThanTheLimit() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("BADGE"), eq(configId))).thenReturn(25);

        service.record("BADGE", configId, Map.of("name", "Thẻ"), "admin");

        // Bản mới là 26; giữ 20 bản gần nhất nên xóa các bản ≤ 6.
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("delete from layout_config_version"), eq("BADGE"), eq(configId), eq(26 - LayoutConfigVersionService.MAX_VERSIONS));
    }

    @Test
    void firstVersionStartsAtOne() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(null);
        service.record("CERTIFICATE", configId, Map.of(), "admin");
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("insert into layout_config_version"), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertEquals(1, args.getAllValues().get(3));
    }
}
