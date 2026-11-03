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
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderTransferRequest;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;

    @org.mockito.Mock
    private phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @org.mockito.Mock
    private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;

    @org.mockito.Mock
    private phucuong.ht.modules.media.service.MediaAssetService mediaAssetService;

    @org.mockito.Mock
    private phucuong.ht.modules.media.service.MediaUrls mediaUrls;

    @org.mockito.Mock
    private LeaderTransferNotifier leaderTransferNotifier;

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
        UUID uploadedAssetId = UUID.randomUUID();
        UUID attachedAssetId = UUID.randomUUID();
        request.setImageAssetId(uploadedAssetId);
        request.setGender(LeaderGender.NAM);
        request.setLeaderLevel("HT_XU");
        request.setParishId(parishId);
        Parish parish = parish(parishId);
        Leader created = leader(leaderId, parish);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(leaderLevelService.requireExistingOrNull(org.mockito.ArgumentMatchers.eq("HT_XU"), org.mockito.ArgumentMatchers.eq("Cấp độ"), any())).thenReturn("HT_XU");
        when(leaderValidationHelper.requireExistingParish(parishId)).thenReturn(parish);
        when(mediaAssetService.attach(eq(uploadedAssetId), eq(phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR), any(), eq("LEADER1")))
                .thenReturn(attachedAssetId);
        when(leaderValidationHelper.splitFullName("Nguyen An"))
                .thenReturn(new LeaderValidationHelper.LeaderNameParts("An", "Nguyen"));
        when(leaderRepository.save(argThat(entity ->
                "leader@example.com".equals(entity.getEmail())
                        && LeaderGender.NAM == entity.getGender()
                        && "HT_XU".equals(entity.getLeaderLevel())
                        && "An".equals(entity.getFirstName())
                        && "Nguyen".equals(entity.getLastName())
                        && attachedAssetId.equals(entity.getImageAssetId())
                        && parishId.equals(entity.getParish().getId())
                        && "LEADER1".equals(entity.getCreatedBy())
                        && "LEADER1".equals(entity.getUpdatedBy())
                        && entity.getCreatedAt() != null
                        && entity.getUpdatedAt() != null
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
    void accountCandidatesSearchMatchesAcrossHolyNameAndFullNameIgnoringAccents() {
        UUID parishId = UUID.randomUUID();
        UUID wantedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Parish parish = parish(parishId);
        Leader wanted = leader(wantedId, parish);
        wanted.setHolyName("Phêrô");
        wanted.setLastName("Nguyễn Tiến");
        wanted.setFirstName("Đạt");
        Leader other = leader(otherId, parish);
        other.setHolyName("Maria");
        other.setLastName("Trần");
        other.setFirstName("Bình");
        Leader summaryA = new Leader();
        summaryA.setId(wantedId);
        Leader summaryB = new Leader();
        summaryB.setId(otherId);
        Authentication authentication = authenticationWithPermission(LeaderAccessEvaluator.READ_PARISH);

        when(leaderScopeHelper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH)).thenReturn(parishId);
        when(leaderRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of(summaryA, summaryB)));
        when(leaderRepository.findAllByIdsWithParish(List.of(wantedId, otherId))).thenReturn(List.of(wanted, other));

        BaseSearchRequest request = new BaseSearchRequest();
        request.setSearch("Phero Nguyen tien DAT".replace("Phero", "Pherô"));
        List<LeaderResponse> result = leaderService.findAccountCandidates(request, authentication);

        assertEquals(List.of(wantedId), result.stream().map(LeaderResponse::getId).toList());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(leaderRepository).findAll(captor.capture());
        assertEquals(null, captor.getValue().getSearch(), "việc tìm theo cụm từ do service làm, không đẩy xuống từng cột");
    }

    @Test
    void accountCandidatesWithoutSearchReturnsEveryoneWithoutAccount() {
        UUID parishId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Leader summary = new Leader();
        summary.setId(id);
        Authentication authentication = authenticationWithPermission(LeaderAccessEvaluator.READ_PARISH);
        when(leaderScopeHelper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH)).thenReturn(parishId);
        when(leaderRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of(summary)));
        when(leaderRepository.findAllByIdsWithParish(List.of(id))).thenReturn(List.of(leader(id, parish(parishId))));
        when(accountRepository.existsByLeaderId(id)).thenReturn(false);

        assertEquals(1, leaderService.findAccountCandidates(new BaseSearchRequest(), authentication).size());
    }

    @Test
    void findAllShouldApplyDioceseScopeFilter() {
        UUID dioceseId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Leader summary = new Leader();
        summary.setId(leaderId);
        Leader hydrated = leader(leaderId, parish(parishId));
        Authentication authentication = authenticationWithPermission(LeaderAccessEvaluator.READ_DIOCESE);

        when(leaderScopeHelper.getRequiredActiveDioceseId(authentication, LeaderAccessEvaluator.READ_DIOCESE)).thenReturn(dioceseId);
        when(leaderRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of(summary)));
        when(leaderRepository.findAllByIdsWithParish(List.of(leaderId))).thenReturn(List.of(hydrated));

        Page<LeaderResponse> result = leaderService.findAll(new BaseSearchRequest(), authentication);

        assertEquals(leaderId, result.getContent().getFirst().getId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(leaderRepository).findAll(captor.capture());
        assertEquals(dioceseId, captor.getValue().getFilters().get("parish.deanery.diocese.id"));
    }

    @Test
    void findAllShouldApplyDeaneryScopeFilter() {
        UUID deaneryId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Leader summary = new Leader();
        summary.setId(leaderId);
        Leader hydrated = leader(leaderId, parish(parishId));
        Authentication authentication = authenticationWithPermission(LeaderAccessEvaluator.READ_DEANERY);

        when(leaderScopeHelper.getRequiredActiveDeaneryId(authentication, LeaderAccessEvaluator.READ_DEANERY)).thenReturn(deaneryId);
        when(leaderRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of(summary)));
        when(leaderRepository.findAllByIdsWithParish(List.of(leaderId))).thenReturn(List.of(hydrated));

        Page<LeaderResponse> result = leaderService.findAll(new BaseSearchRequest(), authentication);

        assertEquals(leaderId, result.getContent().getFirst().getId());
        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(leaderRepository).findAll(captor.capture());
        assertEquals(deaneryId, captor.getValue().getFilters().get("parish.deanery.id"));
    }

    @Test
    void updateShouldNotChangeLevel() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        LeaderUpdateRequest request = new LeaderUpdateRequest();
        request.setGender(LeaderGender.NU);
        UUID newAssetId = UUID.randomUUID();
        request.setImageAssetId(newAssetId);
        Leader existing = leader(leaderId, parish(parishId));
        existing.setEmail("leader@example.com");
        existing.setLeaderLevel("HLV_I");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing, existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(mediaAssetService.attach(eq(newAssetId), eq(phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR), any(), eq("LEADER1")))
                .thenReturn(newAssetId);
        when(mediaUrls.displayOrLegacy(any(), any(), any())).thenReturn("signed-url");
        when(leaderRepository.update(leaderId, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setGender(LeaderGender.NU);
            existing.setImageAssetId(newAssetId);
            return existing;
        });

        LeaderResponse result = leaderService.update(leaderId, request, authentication);

        assertEquals("HLV_I", result.getLeaderLevel());
        assertEquals(LeaderGender.NU, result.getGender());
        assertEquals(existing.getFirstName(), result.getFirstName());
        assertEquals(existing.getLastName(), result.getLastName());
        assertEquals("signed-url", result.getImageUrl());
        verify(leaderRepository).setImageAsset(leaderId, newAssetId);
    }

    @Test
    void transferShouldPersistParishAndAnnounce() {
        UUID oldParishId = UUID.randomUUID();
        UUID newParishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        LeaderTransferRequest request = new LeaderTransferRequest();
        request.setParishId(newParishId);
        Leader existing = leader(leaderId, parish(oldParishId));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing, existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("ADMIN1");
        Parish targetParish = parish(newParishId);
        when(leaderValidationHelper.requireExistingParish(newParishId)).thenReturn(targetParish);

        leaderService.transfer(leaderId, request, authentication);

        verify(leaderScopeHelper).enforceTransferScope(authentication, newParishId);
        verify(leaderRepository).setParish(leaderId, newParishId, "ADMIN1");
        verify(leaderTransferNotifier).announce(existing, existing.getParish(), targetParish, "ADMIN1");
    }

    @Test
    void transferToSameParishShouldBeRejected() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        LeaderTransferRequest request = new LeaderTransferRequest();
        request.setParishId(parishId);
        Leader existing = leader(leaderId, parish(parishId));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("ADMIN1");

        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.common.exception.ApiException.class,
                () -> leaderService.transfer(leaderId, request, authentication));

        verify(leaderRepository, org.mockito.Mockito.never()).setParish(any(), any(), any());
    }

    @Test
    void updateShouldRejectParishChange() {
        UUID leaderId = UUID.randomUUID();
        LeaderUpdateRequest request = new LeaderUpdateRequest();
        request.setParishId(UUID.randomUUID());
        Leader existing = leader(leaderId, parish(UUID.randomUUID()));
        existing.setEmail("leader@example.com");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(leaderValidationHelper.requireExistingLeaderWithParish(leaderId)).thenReturn(existing);
        when(leaderValidationHelper.getRequiredAuditActor(authentication)).thenReturn("ADMIN1");

        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.common.exception.ApiException.class,
                () -> leaderService.update(leaderId, request, authentication));

        verify(leaderRepository, org.mockito.Mockito.never()).setParish(any(), any(), any());
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
        leader.setLeaderLevel("HLV_I");
        leader.setGender(LeaderGender.NAM);
        leader.setLegacyImageData("data:image/png;base64,AAAA");
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
