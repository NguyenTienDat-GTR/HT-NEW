package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalTransitionPolicyTest {

    @Test
    void pendingAndSubmittedCanBeApprovedOrRejected() {
        for (WorkflowApprovalStatus status : new WorkflowApprovalStatus[]{WorkflowApprovalStatus.PENDING, WorkflowApprovalStatus.SUBMITTED}) {
            assertTrue(ApprovalTransitionPolicy.canDecideManually(status, true));
            assertTrue(ApprovalTransitionPolicy.canDecideManually(status, false));
            assertTrue(ApprovalTransitionPolicy.canReject(status));
        }
    }

    @Test
    void rejectedCanOnlyBeApprovedAgain() {
        assertTrue(ApprovalTransitionPolicy.canDecideManually(WorkflowApprovalStatus.REJECTED, true));
        assertFalse(ApprovalTransitionPolicy.canDecideManually(WorkflowApprovalStatus.REJECTED, false));
        assertFalse(ApprovalTransitionPolicy.canReject(WorkflowApprovalStatus.REJECTED));
    }

    @Test
    void finalStatesAreClosedToManualDecisions() {
        for (WorkflowApprovalStatus status : new WorkflowApprovalStatus[]{
                WorkflowApprovalStatus.APPROVED, WorkflowApprovalStatus.REVOKED, WorkflowApprovalStatus.NOT_REQUIRED}) {
            assertFalse(ApprovalTransitionPolicy.canDecideManually(status, true));
            assertFalse(ApprovalTransitionPolicy.canDecideManually(status, false));
            assertFalse(ApprovalTransitionPolicy.canReject(status));
        }
    }

    @Test
    void approveIsBlockedOnlyForApprovedAndRevoked() {
        assertFalse(ApprovalTransitionPolicy.canApprove(WorkflowApprovalStatus.APPROVED));
        assertFalse(ApprovalTransitionPolicy.canApprove(WorkflowApprovalStatus.REVOKED));
        assertTrue(ApprovalTransitionPolicy.canApprove(WorkflowApprovalStatus.PENDING));
        assertTrue(ApprovalTransitionPolicy.canApprove(WorkflowApprovalStatus.REJECTED));
    }
}
