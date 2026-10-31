package com.kp.claims.core.models;

public class ClaimOutput {
    public String policyId;
    public String policyHolderId;
    public String dateOfService;
    public String mainCategory;
    public String subCategory;
    public double billedAmount;

    public double policyHolderPays;
    public double planPays;
    public String ruleUsed;
    public double updatedIndividualAccumulated;
    public double updatedFamilyAccumulated;

    public String errorCode;
    public String errorMessage;
    public String processingMessage;
}
