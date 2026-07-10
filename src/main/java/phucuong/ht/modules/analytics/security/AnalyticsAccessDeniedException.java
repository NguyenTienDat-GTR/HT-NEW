package phucuong.ht.modules.analytics.security;

import org.springframework.security.access.AccessDeniedException;
public class AnalyticsAccessDeniedException extends AccessDeniedException { public AnalyticsAccessDeniedException(String message) { super(message); } }
