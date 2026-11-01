package com.kp.claims.core.repositories;

import com.kp.claims.core.models.PolicyState;

public interface PolicyRepository {

    PolicyState findByPolicyHolderId(
            String policyHolderId);
}
