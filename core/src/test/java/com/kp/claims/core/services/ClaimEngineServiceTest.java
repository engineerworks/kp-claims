package com.kp.claims.core.services;

import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.models.PolicyState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimEngineServiceTest {

    private ClaimEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new ClaimEngineService();
    }

    @Test
    void shouldMakePolicyHolderPayFullAmountWhenDeductibleNotMet() {

        ClaimInput claim = createClaim(1500.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                1000.0,
                2000.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals(1500.0, output.policyHolderPays, 0.001);
        assertEquals(0.0, output.planPays, 0.001);

        assertEquals(
                2500.0,
                output.updatedIndividualAccumulated,
                0.001
        );

        assertEquals(
                3500.0,
                output.updatedFamilyAccumulated,
                0.001
        );

        assertNull(output.errorCode);
    }

    @Test
    void shouldApplyCoverageWhenIndividualDeductibleMet() {

        ClaimInput claim = createClaim(1500.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                6000.0,
                7000.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals(1200.0, output.planPays, 0.001);
        assertEquals(300.0, output.policyHolderPays, 0.001);

        assertTrue(
                output.processingMessage.contains(
                        "INDIVIDUAL"
                )
        );
    }

    @Test
    void shouldApplyCoverageWhenFamilyDeductibleMet() {

        ClaimInput claim = createClaim(1500.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                1000.0,
                12000.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals(1200.0, output.planPays, 0.001);
        assertEquals(300.0, output.policyHolderPays, 0.001);

        assertTrue(
                output.processingMessage.contains(
                        "FAMILY"
                )
        );
    }

    @Test
    void shouldHandleClaimThatCrossesDeductibleThreshold() {

        ClaimInput claim = createClaim(1200.0);

        PolicyState policy = createPolicy(
                5000.0,
                10000.0,
                4000.0,
                4000.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "90% AFTER DEDUCTIBLE"
        );

        /*
         * $1,000 is needed to reach deductible.
         *
         * Remaining claim = $200
         *
         * Plan pays 90% of $200 = $180
         * Holder pays $1,000 + $20 = $1,020
         */

        assertEquals(180.0, output.planPays, 0.001);
        assertEquals(1020.0, output.policyHolderPays, 0.001);

        assertEquals(
                5000.0,
                output.updatedIndividualAccumulated,
                0.001
        );
    }

    @Test
    void shouldProcessNoChargeRuleRegardlessOfDeductible() {

        ClaimInput claim = createClaim(300.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                0.0,
                0.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "No Charge"
        );

        assertEquals(300.0, output.planPays, 0.001);
        assertEquals(0.0, output.policyHolderPays, 0.001);

        assertEquals(
                0.0,
                output.updatedIndividualAccumulated,
                0.001
        );

        assertEquals(
                0.0,
                output.updatedFamilyAccumulated,
                0.001
        );
    }

    @Test
    void shouldProcessFlatDollarCoverage() {

        ClaimInput claim = createClaim(250.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                0.0,
                0.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "$120 FLAT"
        );

        assertEquals(120.0, output.planPays, 0.001);
        assertEquals(130.0, output.policyHolderPays, 0.001);

        assertEquals(
                130.0,
                output.updatedIndividualAccumulated,
                0.001
        );

        assertEquals(
                130.0,
                output.updatedFamilyAccumulated,
                0.001
        );
    }

    @Test
    void shouldReturnE0001WhenPolicyHolderDoesNotExist() {

        ClaimInput claim = createClaim(1500.0);

        ClaimOutput output = engine.processClaim(
                claim,
                null,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals("E0001", output.errorCode);

        assertEquals(
                "Policy holder does not exist",
                output.errorMessage
        );

        assertEquals(0.0, output.planPays, 0.001);
        assertEquals(0.0, output.policyHolderPays, 0.001);
    }

    @Test
    void shouldReturnE0002WhenCoverageInactive() {

        ClaimInput claim = createClaim(1500.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                6000.0,
                12000.0,
                false
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals("E0002", output.errorCode);

        assertEquals(
                "Coverage has expired or is inactive",
                output.errorMessage
        );
    }

    @Test
    void shouldReturnE0004ForInvalidBilledAmount() {

        ClaimInput claim = createClaim(0.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                0.0,
                0.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "80% AFTER DEDUCTIBLE"
        );

        assertEquals("E0004", output.errorCode);

        assertEquals(
                "Malformed fields or invalid billed amount",
                output.errorMessage
        );
    }

    @Test
    void shouldReturnE0005ForUnknownCoverageRule() {

        ClaimInput claim = createClaim(1000.0);

        PolicyState policy = createPolicy(
                6000.0,
                12000.0,
                6000.0,
                12000.0,
                true
        );

        ClaimOutput output = engine.processClaim(
                claim,
                policy,
                "UNKNOWN RULE"
        );

        assertEquals("E0005", output.errorCode);

        assertEquals(
                "Unparseable coverage rule format",
                output.errorMessage
        );
    }

    private ClaimInput createClaim(double billedAmount) {

        ClaimInput claim = new ClaimInput();

        claim.policyId = "100009";
        claim.policyHolderId = "1000099";
        claim.dateOfService = "2016-07-20";
        claim.mainCategory = "Outpatient Services";
        claim.subCategory = "LAB TESTS";
        claim.billedAmount = billedAmount;

        return claim;
    }

    private PolicyState createPolicy(
            double individualThreshold,
            double familyThreshold,
            double individualAccumulated,
            double familyAccumulated,
            boolean active) {

        PolicyState policy = new PolicyState();

        policy.planId = "P001";

        policy.individualThreshold =
                individualThreshold;

        policy.familyThreshold =
                familyThreshold;

        policy.currentIndividualAccumulated =
                individualAccumulated;

        policy.currentFamilyAccumulated =
                familyAccumulated;

        policy.isActive = active;

        return policy;
    }
}
