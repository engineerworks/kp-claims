package com.kp.claims.core.services;


import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.PolicyState;
import com.kp.claims.core.models.ClaimOutput;

public class ClaimEngineService {

    public ClaimOutput processClaim(ClaimInput claim, PolicyState policy, String ruleString) {
        ClaimOutput output = new ClaimOutput();

        // Map baseline data
        output.policyId = claim.policyId;
        output.policyHolderId = claim.policyHolderId;
        output.dateOfService = claim.dateOfService;
        output.mainCategory = claim.mainCategory;
        output.subCategory = claim.subCategory;
        output.billedAmount = claim.billedAmount;

        // Validation 1: Policy holder verification
        if (policy == null || !policy.isActive) {
            output.errorCode = "E0001";
            output.errorMessage = "Policy holder does not exist";
            return output;
        }

        output.ruleUsed = ruleString;
        double bill = claim.billedAmount;

        // Rule Type A: No Charge (e.g. Preventive Care)
        if ("No Charge".equalsIgnoreCase(ruleString)) {
            output.policyHolderPays = 0.0;
            output.planPays = bill;
            output.updatedIndividualAccumulated = policy.currentIndividualAccumulated;
            output.updatedFamilyAccumulated = policy.currentFamilyAccumulated;
            output.processingMessage = "Preventive Care - No Charge to policyholder";
            return output;
        }

        // Rule Type B: Flat Dollar Coverage (e.g., $120 FLAT)
        if (ruleString.startsWith("$")) {
            double flatPlanPays = Double.parseDouble(ruleString.replace("$", "").replace(" FLAT", "").trim());

            output.planPays = Math.min(flatPlanPays, bill);
            output.policyHolderPays = Math.max(0.0, bill - output.planPays);

            // Flat dollar payments by policyholder ALWAYS accumulate toward deductibles
            policy.currentIndividualAccumulated += output.policyHolderPays;
            policy.currentFamilyAccumulated += output.policyHolderPays;

            output.updatedIndividualAccumulated = policy.currentIndividualAccumulated;
            output.updatedFamilyAccumulated = policy.currentFamilyAccumulated;
            output.processingMessage = "Flat dollar coverage - plan pays " + ruleString + " regardless of deductible";
            return output;
        }

        // Rule Type C: Percentage After Deductible (e.g., 40% AFTER DEDUCTIBLE)
        if (ruleString.contains("AFTER DEDUCTIBLE")) {
            double planPercentage = Double.parseDouble(ruleString.split("%")[0].trim()) / 100.0;

            boolean indDeductibleMet = policy.currentIndividualAccumulated >= policy.individualThreshold;
            boolean famDeductibleMet = policy.currentFamilyAccumulated >= policy.familyThreshold;

            if (indDeductibleMet || famDeductibleMet) {
                // Deductible is already fully met prior to this claim
                output.planPays = bill * planPercentage;
                output.policyHolderPays = bill - output.planPays;

                output.processingMessage = indDeductibleMet ?
                        "ANNUAL DEDUCTIBLE (INDIVIDUAL) met, plan pays " + (int)(planPercentage*100) + "%" :
                        "ANNUAL DEDUCTIBLE (FAMILY) met, plan pays " + (int)(planPercentage*100) + "%";
            } else {
                // Deductible is NOT met yet. We evaluate how much of this bill fills the deductible bucket.
                double remainingIndDeductible = policy.individualThreshold - policy.currentIndividualAccumulated;

                if (bill <= remainingIndDeductible) {
                    // Entire bill goes to deductible bucket
                    output.policyHolderPays = bill;
                    output.planPays = 0.0;

                    policy.currentIndividualAccumulated += bill;
                    policy.currentFamilyAccumulated += bill;
                    output.processingMessage = "ANNUAL DEDUCTIBLE (INDIVIDUAL or FAMILY) not met, plan pays 0%";
                } else {
                    // This claim splits: part satisfies the remaining deductible, the rest gets cost-shared
                    double amountToMeetDeductible = remainingIndDeductible;
                    double remainderBill = bill - amountToMeetDeductible;

                    double sharedPlanPays = remainderBill * planPercentage;
                    double sharedHolderPays = remainderBill - sharedPlanPays;

                    output.policyHolderPays = amountToMeetDeductible + sharedHolderPays;
                    output.planPays = sharedPlanPays;

                    policy.currentIndividualAccumulated += amountToMeetDeductible;
                    policy.currentFamilyAccumulated += amountToMeetDeductible;
                    output.processingMessage = "ANNUAL DEDUCTIBLE (INDIVIDUAL) met, plan pays " + (int)(planPercentage*100) + "%";
                }
            }

            output.updatedIndividualAccumulated = policy.currentIndividualAccumulated;
            output.updatedFamilyAccumulated = policy.currentFamilyAccumulated;
            return output;
        }

        output.errorCode = "E0002";
        output.errorMessage = "Unparseable coverage rule format";
        return output;
    }
}
