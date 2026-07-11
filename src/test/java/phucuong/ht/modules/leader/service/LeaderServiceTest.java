package phucuong.ht.modules.leader.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.leader.helper.LeaderScopeHelper;
import phucuong.ht.modules.leader.helper.LeaderValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderServiceTest {

    @Mock
    private LeaderRepository leaderRepository;

    @Mock
    private LeaderValidationHelper leaderValidationHelper;

    @Mock
    private LeaderScopeHelper leaderScopeHelper;

    @InjectMocks
    private LeaderService leaderService;

    @Test
    void createShouldValidateScopeAndDelegateToRepository() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("Phaolo");
        request.setFullName("Nguyen An");
        request.setEmail("leader@example.com");
        request.setImageUrl("https://cdn.example.com/leaders/an.jpg");
        request.setGender(LeaderGender.NAM);
        request.setLeaderLevel(LeaderLevel.HT_XU);
        request.setParishId(parishId);
        Parish parish = parish(parishId);
        Leader created = leader(leaderId, parish);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(leaderValidationHelper.requireExistingParish(parishId)).thenReturn(parish);
        when(leaderValidationHelper.splitFullName("Nguyen An"))
                .thenReturn(new LeaderValidationHelper.LeaderNameParts("An", "Nguyen"));
        when(leaderRepository.save(argThat(entity ->
                "leader@example.com".equals(entity.getEmail())
                        && LeaderGender.NAM == entity.getGender()
                        && LeaderLevel.HT_XU == entity.getLeaderLevel()
                        && "An".equals(entity.getFirstName())
                        && "Nguyen".equals(entity.getLastName())
                        && "https://cdn.example.com/leaders/an.jpg".equals(entity.getImageUrl())
                        && parishId.equals(entity.getParish().getId())
                        && "LEADER1".equals(entity.getCreatedBy())
        ))).thenAnswer(invocation -> {
            Leader entity = invocation.getArgument(0);
            entity.setId(leaderId);
            return entity;
        });
        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(created);

        LeaderResponse result = leaderService.create(request, authentication);

        assertEquals(leaderId, result.getId());
        assertEquals(parishId, result.getParishId());
        verify(leaderValidationHelper).validateCreateInput(request);
        verify(leaderScopeHelper).enforceCreateScope(request, authentication);
        verify(leaderValidationHelper).validateCreateEmailUniqueness("leader@example.com");
        verify(leaderValidationHelper).splitFullName("Nguyen An");
    }

    @Test
    void findAllShouldApplyParishScopeFilter() {
        UUID parishId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(java.util.Map.of("email", "ignored@example.com")));
        Leader summary = new Leader();
        UUID leaderId = UUID.randomUUID();
        summary.setId(leaderId);
        Parish parish = parish(parishId);
        Leader hydrated = leader(leaderId, parish);
        Page<Leader> page = new PageImpl<>(List.of(summary));
        Authentication authentication = authenticationWithPermission(LeaderAccessEvaluator.READ_PARISH);

        when(leaderScopeHelper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH)).thenReturn(parishId);
        when(leaderRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(leaderRepository.findAllByIdsWithParish(List.of(leaderId))).thenReturn(List.of(hydrated));

        Page<LeaderResponse> result = leaderService.findAll(request, authentication);

        assertEquals(leaderId, result.getContent().getFirst().getId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(leaderRepository).findAll(captor.capture());
        assertEquals(parishId, captor.getValue().getFilters().get("parish.id"));
        assertEquals("ignored@example.com", captor.getValue().getFilters().get("email"));
    }

    @Test
    void updateShouldNotChangeLevel() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        LeaderUpdateRequest request = new LeaderUpdateRequest();
        request.setFullName("Nguyen Binh");
        request.setGender(LeaderGender.NU);
        request.setImageUrl("https://cdn.example.com/leaders/binh.jpg");
        Leader existing = leader(leaderId, parish(parishId));
        existing.setEmail("leader@example.com");
        existing.setLeaderLevel(LeaderLevel.HLV_I);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(leaderValidationHelper.splitFullName("Nguyen Binh"))
                .thenReturn(new LeaderValidationHelper.LeaderNameParts("Binh", "Nguyen"));

        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing, existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(leaderRepository.update(leaderId, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setFirstName("Binh");
            existing.setLastName("Nguyen");
            existing.setGender(LeaderGender.NU);
            existing.setImageUrl("https://cdn.example.com/leaders/binh.jpg");
            return existing;
        });

        LeaderResponse result = leaderService.update(leaderId, request, authentication);

        assertEquals(LeaderLevel.HLV_I, result.getLeaderLevel());
        assertEquals(LeaderGender.NU, result.getGender());
        assertEquals("Binh", result.getFirstName());
        assertEquals("Nguyen", result.getLastName());
        assertEquals("https://cdn.example.com/leaders/binh.jpg", result.getImageUrl());
    }

    @Test
    void toggleStatusShouldReturnUpdatedLeader() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        Leader existing = leader(leaderId, parish(parishId));
        existing.setStatus(false);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing, existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(leaderRepository.toggleStatus(leaderId, "LEADER1")).thenAnswer(invocation -> {
            existing.setStatus(false);
            return existing;
        });

        LeaderResponse result = leaderService.toggleStatus(leaderId, request, authentication);

        assertEquals(leaderId, result.getId());
        verify(leaderValidationHelper).validateToggleConfirmation("Xác nhận");
        verify(leaderRepository).toggleStatus(leaderId, "LEADER1");
    }

    private Authentication authenticationWithPermission(String permission) {
        org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority(permission);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin-parish", null, List.of(authority));
    }

    private Leader leader(UUID id, Parish parish) {
        Leader leader = new Leader();
        leader.setId(id);
        leader.setHolyName("Phaolo");
        leader.setFirstName("An");
        leader.setLastName("Nguyen");
        leader.setLeaderLevel(LeaderLevel.HLV_I);
        leader.setGender(LeaderGender.NAM);
        leader.setImageUrl("https://cdn.example.com/leaders/sample.jpg");
        leader.setParish(parish);
        leader.setStatus(true);
        return leader;
    }

    private Parish parish(UUID parishId) {
        Parish parish = new Parish();
        parish.setId(parishId);
        return parish;
    }
}
