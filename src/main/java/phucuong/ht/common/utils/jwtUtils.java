package phucuong.ht.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.common.exception.errorCode;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class jwtUtils {

    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_LEADER_ID = "leaderId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_JTI = "jti";

    private final AuthProperties authProperties;
    private final SecretKey accessSecretKey;
    private final SecretKey refreshSecretKey;

    public jwtUtils(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.accessSecretKey = Keys.hmacShaKeyFor(authProperties.getJwt().getAccessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshSecretKey = Keys.hmacShaKeyFor(authProperties.getJwt().getRefreshSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AuthUserDetails principal) {
        return generateToken(principal, ACCESS_TOKEN_TYPE, authProperties.getJwt().getAccessTokenExpirationMs());
    }

    public String generateRefreshToken(AuthUserDetails principal) {
        return generateToken(principal, REFRESH_TOKEN_TYPE, authProperties.getJwt().getRefreshTokenExpirationMs());
    }

    public AuthUserDetails parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE);
    }

    public AuthUserDetails parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE);
    }

    public long getAccessTokenExpirationMs() {
        return authProperties.getJwt().getAccessTokenExpirationMs();
    }

    public long getRefreshTokenExpirationMs() {
        return authProperties.getJwt().getRefreshTokenExpirationMs();
    }

    private String generateToken(AuthUserDetails principal, String tokenType, long ttlMs) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(ttlMs);

        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(authProperties.getJwt().getIssuer())
                .audience().add(authProperties.getJwt().getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .claim(CLAIM_LEADER_ID, principal.getLeaderId())
                .claim(CLAIM_ROLES, principal.getRoles())
                .claim(CLAIM_PERMISSIONS, principal.getPermissions())
                .signWith(resolveSecretKey(tokenType))
                .compact();
    }

    private AuthUserDetails parseToken(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(resolveSecretKey(expectedType))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, "Token không hợp lệ hoặc đã hết hạn");
        }

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(tokenType)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, "Loại token không được phép cho thao tác này");
        }

        return AuthUserDetails.tokenPrincipal(
                claims.getSubject(),
                claims.get(CLAIM_LEADER_ID, String.class),
                readClaimList(claims.get(CLAIM_ROLES)),
                readClaimList(claims.get(CLAIM_PERMISSIONS))
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> readClaimList(Object claimValue) {
        if (claimValue == null) {
            return List.of();
        }
        if (claimValue instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return (List<String>) claimValue;
    }

    private SecretKey resolveSecretKey(String tokenType) {
        if (REFRESH_TOKEN_TYPE.equals(tokenType)) {
            return refreshSecretKey;
        }
        return accessSecretKey;
    }
}
