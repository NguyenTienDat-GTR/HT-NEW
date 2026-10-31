package phucuong.ht.modules.certificate.config.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.layoutconfig.LayoutConfigStore;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayoutConfigRulesTest {

    @SuppressWarnings("unchecked")
    private final LayoutConfigStore<BadgeConfig> store = mock(LayoutConfigStore.class);
    private final CommonValidationHelper validation = mock(CommonValidationHelper.class);
    private final ActorScope diocese = new ActorScope("DIOCESE", "DIOCESE:1", List.of("SYSTEM", "DIOCESE:1"));

    private static BadgeConfig config(String scopeKey, boolean isDefault) {
        BadgeConfig config = new BadgeConfig();
        config.setId(UUID.randomUUID());
        config.setScopeKey(scopeKey);
        config.setIsDefault(isDefault);
        return config;
    }

    private static ApiException error(HttpStatus status) {
        return new ApiException(status, errorCode.CONFLICT, status.name());
    }

    @Test
    void visibleReturnsEverythingForUnrestrictedActorsAndScopedKeysOtherwise() {
        ActorScope all = new ActorScope("SYSTEM", "SYSTEM", null);
        List<BadgeConfig> everything = List.of(config("SYSTEM", true));
        when(store.findAllConfigs()).thenReturn(everything);
        when(store.findByScopeKeys(diocese.usableKeys())).thenReturn(List.of());

        assertSame(everything, LayoutConfigRules.visible(store, all));
        assertTrue(LayoutConfigRules.visible(store, diocese).isEmpty());
    }

    @Test
    void requireEditableRejectsUnseenMissingAndOtherLevelConfigs() {
        BadgeConfig own = config("DIOCESE:1", false);
        BadgeConfig system = config("SYSTEM", true);
        BadgeConfig hidden = config("DIOCESE:2", false);
        when(store.findById(own.getId())).thenReturn(Optional.of(own));
        when(store.findById(system.getId())).thenReturn(Optional.of(system));
        when(store.findById(hidden.getId())).thenReturn(Optional.of(hidden));
        when(validation.notFound(any())).thenReturn(error(HttpStatus.NOT_FOUND));
        when(validation.forbidden(any())).thenReturn(error(HttpStatus.FORBIDDEN));

        assertSame(own, LayoutConfigRules.requireEditable(store, validation, own.getId(), diocese, "x"));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> LayoutConfigRules.requireEditable(store, validation, system.getId(), diocese, "x")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> LayoutConfigRules.requireEditable(store, validation, hidden.getId(), diocese, "x")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> LayoutConfigRules.requireEditable(store, validation, UUID.randomUUID(), diocese, "x")).getStatus());
    }

    @Test
    void makeDefaultClearsTheOldDefaultAndFlushesBeforeSettingTheNewOne() {
        BadgeConfig old = config("DIOCESE:1", true);
        BadgeConfig next = config("DIOCESE:1", false);
        when(store.findDefault("DIOCESE:1")).thenReturn(Optional.of(old));
        when(store.merge(any())).thenAnswer(call -> call.getArgument(0));
        OffsetDateTime now = OffsetDateTime.now();

        BadgeConfig saved = LayoutConfigRules.makeDefault(store, next, "admin", now);

        assertSame(next, saved);
        assertFalse(old.getIsDefault());
        assertTrue(next.getIsDefault());
        assertEquals("admin", next.getUpdatedBy());
        var order = inOrder(store);
        order.verify(store).merge(old);
        order.verify(store).flush();
        order.verify(store).merge(next);
    }

    @Test
    void makeDefaultIsANoOpForTheCurrentDefault() {
        BadgeConfig current = config("DIOCESE:1", true);

        assertSame(current, LayoutConfigRules.makeDefault(store, current, "admin", OffsetDateTime.now()));
        verify(store, never()).merge(any());
        verify(store, never()).flush();
    }

    @Test
    void defaultConfigCannotBeDeleted() {
        when(validation.conflict(any())).thenReturn(error(HttpStatus.CONFLICT));

        assertThrows(ApiException.class, () -> LayoutConfigRules.requireNotDefault(config("SYSTEM", true), validation));
        LayoutConfigRules.requireNotDefault(config("SYSTEM", false), validation);
    }
}
