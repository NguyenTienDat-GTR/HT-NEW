package phucuong.ht.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitFilterEncodingTest {

    @Test
    void tooManyRequestsResponseIsUtf8Vietnamese() throws Exception {
        RedisRateLimitService service = mock(RedisRateLimitService.class);
        when(service.evaluate(anyString(), anyInt(), anyInt())).thenReturn(new RedisRateLimitService.RateLimitDecision(false, 121, 30));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        new ApiRateLimitFilter(service, properties, new ObjectMapper().findAndRegisterModules())
                .doFilter(new MockHttpServletRequest("GET", "/api/training/courses"), response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("Quá nhiều yêu cầu, vui lòng thử lại sau"));
    }

    @Test
    void loginIsOnlyCountedWhenItFails() throws Exception {
        RedisRateLimitService service = mock(RedisRateLimitService.class);
        when(service.peek(anyString(), anyInt(), anyInt())).thenReturn(new RedisRateLimitService.RateLimitDecision(true, 1, 300));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(service, properties, new ObjectMapper().findAndRegisterModules());

        MockHttpServletResponse failed = new MockHttpServletResponse();
        failed.setStatus(401);
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), failed, new MockFilterChain());
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), ok, new MockFilterChain());

        verify(service, times(1)).record(anyString(), anyInt());
        verify(service, never()).evaluate(anyString(), anyInt(), anyInt());
    }

    @Test
    void loginIsBlockedAfterTooManyFailures() throws Exception {
        RedisRateLimitService service = mock(RedisRateLimitService.class);
        when(service.peek(anyString(), anyInt(), anyInt())).thenReturn(new RedisRateLimitService.RateLimitDecision(false, 10, 120));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        new ApiRateLimitFilter(service, properties, new ObjectMapper().findAndRegisterModules())
                .doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), response, new MockFilterChain());

        assertEquals(429, response.getStatus());
    }
}
