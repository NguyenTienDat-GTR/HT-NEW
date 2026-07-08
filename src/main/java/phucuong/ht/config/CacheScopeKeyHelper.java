package phucuong.ht.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component("cacheScopeKeyHelper")
public class CacheScopeKeyHelper {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    public CacheScopeKeyHelper(securityUtils securityUtils, AccountRepository accountRepository) {
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
    }

    public String detailKey(Authentication authentication, Object id) {
        return authSignature(authentication) + "|id=" + Objects.toString(id, "null");
    }

    public String listKey(Authentication authentication, BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        return authSignature(authentication)
                + "|page=" + source.getPage()
                + "|size=" + source.getSize()
                + "|status=" + Objects.toString(source.getStatus(), "null")
                + "|search=" + Objects.toString(source.getSearch(), "")
                + "|sortBy=" + Objects.toString(source.getSortBy(), "")
                + "|sortDirection=" + Objects.toString(source.getSortDirection(), "")
                + "|searchFields=" + joinCollection(source.getSearchFields())
                + "|filters=" + joinFilters(source.getFilters());
    }

    private String authSignature(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        String authorities = authentication == null ? "" : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String dioceseId = username == null ? "" : accountRepository.findActiveDioceseIdByUsername(username)
                .map(Object::toString)
                .orElse("");
        String deaneryId = username == null ? "" : accountRepository.findActiveDeaneryIdByUsername(username)
                .map(Object::toString)
                .orElse("");
        String parishId = username == null ? "" : accountRepository.findActiveParishIdByUsername(username)
                .map(Object::toString)
                .orElse("");
        return "user=" + Objects.toString(username, "")
                + "|auth=" + authorities
                + "|diocese=" + dioceseId
                + "|deanery=" + deaneryId
                + "|parish=" + parishId;
    }

    private String joinCollection(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String joinFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(",");
        filters.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> joiner.add(entry.getKey() + "=" + Objects.toString(entry.getValue(), "null")));
        return joiner.toString();
    }
}
