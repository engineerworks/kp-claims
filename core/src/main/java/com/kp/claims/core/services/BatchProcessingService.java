package com.kp.claims.core.services;

import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.models.PolicyState;
import com.kp.claims.core.repositories.FirebasePolicyRepository;
import com.kp.claims.core.repositories.PolicyRepository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BatchProcessingService {

    private final ClaimEngineService
            claimEngineService;

    private final CoverageRuleService
            coverageRuleService;

    private final PolicyRepository
            policyRepository;

    /**
     * Production constructor.
     *
     * Used by BatchProcessorCLI and BatchProcessorServlet.
     */
    public BatchProcessingService() {

        this(
                new ClaimEngineService(),
                new CoverageRuleService(),
                new FirebasePolicyRepository()
        );
    }

    /**
     * Constructor injection for unit testing.
     *
     * JUnit can provide a fake PolicyRepository so tests do not
     * make real Firebase network calls.
     */
    public BatchProcessingService(
            ClaimEngineService claimEngineService,
            CoverageRuleService coverageRuleService,
            PolicyRepository policyRepository) {

        if (claimEngineService == null) {
            throw new IllegalArgumentException(
                    "ClaimEngineService is required");
        }

        if (coverageRuleService == null) {
            throw new IllegalArgumentException(
                    "CoverageRuleService is required");
        }

        if (policyRepository == null) {
            throw new IllegalArgumentException(
                    "PolicyRepository is required");
        }

        this.claimEngineService =
                claimEngineService;

        this.coverageRuleService =
                coverageRuleService;

        this.policyRepository =
                policyRepository;
    }

    public int process(
            InputStream inputStream,
            OutputStream outputStream)
            throws IOException {

        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Input CSV stream is required");
        }

        if (outputStream == null) {
            throw new IllegalArgumentException(
                    "Output CSV stream is required");
        }

        /*
         * Holds the latest in-memory policy state for each
         * policyholder during one batch execution.
         *
         * This ensures that the next claim for the same member
         * uses the deductible state updated by the previous claim.
         */
        Map<String, PolicyState> policyStateCache =
                new HashMap<>();

        int processedCount = 0;

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8));

                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        outputStream,
                                        StandardCharsets.UTF_8))
        ) {
            String headerLine =
                    reader.readLine();

            if (headerLine == null
                    || headerLine.trim().isEmpty()) {

                throw new IllegalArgumentException(
                        "The uploaded CSV file is empty");
            }

            validateHeader(headerLine);
            writeOutputHeader(writer);

            String line;
            int rowNumber = 1;

            while ((line = reader.readLine()) != null) {

                rowNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                ClaimInput claim;

                try {
                    claim = parseClaim(line);

                } catch (Exception e) {

                    writeMalformedInputRow(
                            writer,
                            line,
                            rowNumber,
                            e.getMessage());

                    processedCount++;
                    continue;
                }

                PolicyState state =
                        policyStateCache.get(
                                claim.policyHolderId);

                /*
                 * Call the repository only the first time this
                 * policyholder appears in the current batch.
                 */
                if (state == null) {

                    state =
                            policyRepository
                                    .findByPolicyHolderId(
                                            claim.policyHolderId);

                    if (state != null) {

                        policyStateCache.put(
                                claim.policyHolderId,
                                state);
                    }
                }

                double preClaimIndividualAccumulated =
                        state == null
                                ? 0.0
                                : state.currentIndividualAccumulated;

                double preClaimFamilyAccumulated =
                        state == null
                                ? 0.0
                                : state.currentFamilyAccumulated;

                /*
                 * When the policy does not exist, ClaimEngineService
                 * returns E0001 before using the coverage rule.
                 */
                String coverageRule =
                        state == null
                                ? ""
                                : coverageRuleService.findRule(
                                state.planId,
                                claim.mainCategory,
                                claim.subCategory);

                if (state != null
                        && (coverageRule == null
                        || coverageRule.trim().isEmpty())) {

                    ClaimOutput result =
                            createCoverageRuleError(
                                    claim);

                    writeOutputRow(
                            writer,
                            result,
                            preClaimIndividualAccumulated,
                            preClaimFamilyAccumulated);

                    processedCount++;
                    continue;
                }

                ClaimOutput result =
                        claimEngineService.processClaim(
                                claim,
                                state,
                                coverageRule);

                writeOutputRow(
                        writer,
                        result,
                        preClaimIndividualAccumulated,
                        preClaimFamilyAccumulated);

                processedCount++;
            }

            writer.flush();
        }

        return processedCount;
    }

    private void validateHeader(
            String headerLine) {

        String normalizedHeader =
                headerLine
                        .replace("\uFEFF", "")
                        .trim()
                        .toLowerCase(Locale.ENGLISH);

        if (!normalizedHeader.contains("policyid")
                || !normalizedHeader.contains(
                "policy holder id")
                || !normalizedHeader.contains(
                "date of service")
                || !normalizedHeader.contains(
                "coverage main category")
                || !normalizedHeader.contains(
                "coverage sub category")
                || !normalizedHeader.contains(
                "billed amount")) {

            throw new IllegalArgumentException(
                    "Invalid CSV header. Expected "
                            + "PolicyId, Policy holder Id, "
                            + "Date of service, "
                            + "Coverage Main Category, "
                            + "Coverage Sub Category and "
                            + "Billed Amount");
        }
    }

    private ClaimInput parseClaim(
            String line) {

        String[] tokens =
                line.split(",", -1);

        if (tokens.length < 6) {
            throw new IllegalArgumentException(
                    "Expected six CSV columns");
        }

        ClaimInput claim =
                new ClaimInput();

        claim.policyId =
                cleanValue(tokens[0]);

        claim.policyHolderId =
                cleanValue(tokens[1]);

        claim.dateOfService =
                cleanValue(tokens[2]);

        claim.mainCategory =
                cleanValue(tokens[3]);

        claim.subCategory =
                cleanValue(tokens[4]);

        String billedAmountText =
                cleanValue(tokens[5]);

        if (claim.policyId.isEmpty()
                || claim.policyHolderId.isEmpty()
                || claim.dateOfService.isEmpty()
                || claim.mainCategory.isEmpty()
                || claim.subCategory.isEmpty()
                || billedAmountText.isEmpty()) {

            throw new IllegalArgumentException(
                    "One or more required fields are missing");
        }

        try {
            claim.billedAmount =
                    Double.parseDouble(
                            billedAmountText);

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid billed amount: "
                            + billedAmountText);
        }

        return claim;
    }

    private ClaimOutput createCoverageRuleError(
            ClaimInput claim) {

        ClaimOutput output =
                new ClaimOutput();

        output.policyId =
                claim.policyId;

        output.policyHolderId =
                claim.policyHolderId;

        output.dateOfService =
                claim.dateOfService;

        output.mainCategory =
                claim.mainCategory;

        output.subCategory =
                claim.subCategory;

        output.billedAmount =
                claim.billedAmount;

        output.errorCode =
                "E0005";

        output.errorMessage =
                "Coverage rule not found for plan, "
                        + "main category and subcategory";

        output.processingMessage =
                "No matching PlanCoverage reference data";

        return output;
    }

    private void writeOutputHeader(
            BufferedWriter writer)
            throws IOException {

        writer.write(
                "PolicyId,"
                        + "Policy holder Id,"
                        + "Date of service,"
                        + "Coverage Main Category,"
                        + "Coverage Sub Category,"
                        + "Billed Amount,"
                        + "Policy Holder pays,"
                        + "Plan Pays,"
                        + "Rule used,"
                        + "Individual accumulated deductible "
                        + "as of service date,"
                        + "Family accumulated deductible "
                        + "as of service date,"
                        + "Error Code,"
                        + "Error Message,"
                        + "Processing message");

        writer.newLine();
    }

    private void writeOutputRow(
            BufferedWriter writer,
            ClaimOutput result,
            double preClaimIndividualAccumulated,
            double preClaimFamilyAccumulated)
            throws IOException {

        writer.write(
                csv(result.policyId)
                        + ","
                        + csv(result.policyHolderId)
                        + ","
                        + csv(result.dateOfService)
                        + ","
                        + csv(result.mainCategory)
                        + ","
                        + csv(result.subCategory)
                        + ","
                        + formatAmount(
                        result.billedAmount)
                        + ","
                        + formatAmount(
                        result.policyHolderPays)
                        + ","
                        + formatAmount(
                        result.planPays)
                        + ","
                        + csv(result.ruleUsed)
                        + ","
                        + formatAmount(
                        preClaimIndividualAccumulated)
                        + ","
                        + formatAmount(
                        preClaimFamilyAccumulated)
                        + ","
                        + csv(result.errorCode)
                        + ","
                        + csv(result.errorMessage)
                        + ","
                        + csv(result.processingMessage));

        writer.newLine();
    }

    private void writeMalformedInputRow(
            BufferedWriter writer,
            String sourceLine,
            int rowNumber,
            String errorMessage)
            throws IOException {

        writer.write(
                ","
                        + ","
                        + ","
                        + ","
                        + ","
                        + "0.00,"
                        + "0.00,"
                        + "0.00,"
                        + ","
                        + "0.00,"
                        + "0.00,"
                        + csv("E0004")
                        + ","
                        + csv(
                        "Malformed input at CSV row "
                                + rowNumber
                                + ": "
                                + errorMessage)
                        + ","
                        + csv(
                        "Original row: "
                                + sourceLine));

        writer.newLine();
    }

    private String cleanValue(
            String value) {

        if (value == null) {
            return "";
        }

        String cleaned =
                value.trim();

        if (cleaned.length() >= 2
                && cleaned.startsWith("\"")
                && cleaned.endsWith("\"")) {

            cleaned =
                    cleaned.substring(
                            1,
                            cleaned.length() - 1);
        }

        return cleaned.trim();
    }

    private String formatAmount(
            double value) {

        return String.format(
                Locale.US,
                "%.2f",
                value);
    }

    private String csv(
            String value) {

        if (value == null) {
            return "";
        }

        String escaped =
                value.replace(
                        "\"",
                        "\"\"");

        return "\""
                + escaped
                + "\"";
    }
}
