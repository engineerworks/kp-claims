package com.kp.claims.core.services;

import com.google.gson.Gson;
import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.models.PolicyState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BatchProcessorCLI {

    private static final String FIREBASE_URL =
            "https://kp-claims-processing-default-rtdb.asia-southeast1.firebasedatabase.app";

    private static final Gson gson = new Gson();

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Error: Missing input or output file argument.");
            System.out.println(
                    "Usage: BatchProcessorCLI <input.csv> <output.csv>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        ClaimEngineService engine = new ClaimEngineService();

        /*
         * Stores the policy state during this batch execution.
         *
         * The key is policyHolderId.
         * The value is the policyholder's latest deductible state.
         *
         * This ensures claims for the same policyholder are processed
         * sequentially using the state produced by the previous claim.
         */
        Map<String, PolicyState> policyStateCache = new HashMap<>();

        System.out.println(
                "Initializing KP Batch Pipeline Processing Module...");

        try (
                BufferedReader reader =
                        new BufferedReader(new FileReader(inputFile));

                BufferedWriter writer =
                        new BufferedWriter(new FileWriter(outputFile))
        ) {

            /*
             * Read and skip the input header.
             */
            reader.readLine();

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
                            + "Individual accumulated deductible as of service date,"
                            + "Family accumulated deductible as of service date,"
                            + "Error Code,"
                            + "Error Message,"
                            + "Processing message"
            );

            writer.newLine();

            String line;
            int counter = 0;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] tokens = line.split(",");

                if (tokens.length < 6) {
                    System.err.println(
                            "Skipping malformed CSV row: " + line);
                    continue;
                }

                ClaimInput claim = new ClaimInput();

                claim.policyId = cleanValue(tokens[0]);
                claim.policyHolderId = cleanValue(tokens[1]);
                claim.dateOfService = cleanValue(tokens[2]);
                claim.mainCategory = cleanValue(tokens[3]);
                claim.subCategory = cleanValue(tokens[4]);

                try {
                    claim.billedAmount =
                            Double.parseDouble(cleanValue(tokens[5]));
                } catch (NumberFormatException e) {
                    System.err.println(
                            "Skipping row with invalid billed amount: "
                                    + line);
                    continue;
                }

                /*
                 * First try to get the updated policy state from the
                 * in-memory cache.
                 */
                PolicyState state =
                        policyStateCache.get(claim.policyHolderId);

                /*
                 * Fetch from Firebase only when this policyholder has not
                 * already appeared during the current batch.
                 */
                if (state == null) {

                    state = fetchPolicyFromFirebase(
                            claim.policyHolderId);

                    if (state != null) {
                        policyStateCache.put(
                                claim.policyHolderId,
                                state);
                    }
                }

                /*
                 * Capture the deductible values before processing.
                 *
                 * These values represent the accumulated deductible as of
                 * the service date before the current claim is applied.
                 */
                double preClaimIndividualAccumulated =
                        state != null
                                ? state.currentIndividualAccumulated
                                : 0.0;

                double preClaimFamilyAccumulated =
                        state != null
                                ? state.currentFamilyAccumulated
                                : 0.0;

                /*
                 * Temporary coverage-rule mapping.
                 *
                 * We will replace this with PlanCoverage reference-data
                 * lookup in the next step.
                 */
                String activeRule =
                        determineCoverageRule(claim);

                /*
                 * ClaimEngineService mutates the PolicyState object.
                 *
                 * Because the same object is stored in policyStateCache,
                 * the updated accumulated values are automatically
                 * available to the next claim for that policyholder.
                 */
                ClaimOutput result =
                        engine.processClaim(
                                claim,
                                state,
                                activeRule);

                writeOutputRow(
                        writer,
                        result,
                        preClaimIndividualAccumulated,
                        preClaimFamilyAccumulated);

                counter++;
            }

            System.out.println(
                    "Processing Successful! "
                            + counter
                            + " transaction claims exported into: "
                            + outputFile);

        } catch (Exception e) {

            System.err.println(
                    "Fatal batch processing error: "
                            + e.getMessage());

            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String determineCoverageRule(
            ClaimInput claim) {

        String subCategory =
                claim.subCategory == null
                        ? ""
                        : claim.subCategory.toUpperCase();

        if (subCategory.contains("PHYSICAL EXAM")
                || subCategory.contains("MAMMOGRAMS")) {

            return "No Charge";
        }

        if (subCategory.contains("URGENT CARE")) {
            return "$120 FLAT";
        }

        if (subCategory.contains("PRIMARY CARE")) {
            return "50% AFTER DEDUCTIBLE";
        }

        if (subCategory.contains("LAB TESTS")) {
            return "90% AFTER DEDUCTIBLE";
        }

        return "40% AFTER DEDUCTIBLE";
    }

    private static PolicyState fetchPolicyFromFirebase(
            String holderId) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    FIREBASE_URL
                            + "/"
                            + holderId
                            + ".json");

            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "Accept",
                    "application/json");

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode =
                    connection.getResponseCode();

            if (responseCode
                    != HttpURLConnection.HTTP_OK) {

                System.err.println(
                        "Firebase returned HTTP "
                                + responseCode
                                + " for policy holder "
                                + holderId);

                return null;
            }

            StringBuilder responseBody =
                    new StringBuilder();

            try (
                    BufferedReader firebaseReader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            connection
                                                    .getInputStream(),
                                            StandardCharsets.UTF_8))
            ) {
                String responseLine;

                while ((responseLine =
                        firebaseReader.readLine()) != null) {

                    responseBody.append(responseLine);
                }
            }

            String jsonResult =
                    responseBody.toString().trim();

            if (jsonResult.isEmpty()
                    || "null".equals(jsonResult)) {

                return null;
            }

            return gson.fromJson(
                    jsonResult,
                    PolicyState.class);

        } catch (Exception e) {

            System.err.println(
                    "Failed to fetch policy holder "
                            + holderId
                            + " from Firebase: "
                            + e.getMessage());

            return null;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void writeOutputRow(
            BufferedWriter writer,
            ClaimOutput result,
            double preClaimIndividualAccumulated,
            double preClaimFamilyAccumulated)
            throws Exception {

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
                        + formatAmount(result.billedAmount)
                        + ","
                        + formatAmount(result.policyHolderPays)
                        + ","
                        + formatAmount(result.planPays)
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
                        + csv(result.processingMessage)
        );

        writer.newLine();
    }

    private static String cleanValue(String value) {

        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        if (cleaned.length() >= 2
                && cleaned.startsWith("\"")
                && cleaned.endsWith("\"")) {

            cleaned = cleaned.substring(
                    1,
                    cleaned.length() - 1);
        }

        return cleaned.trim();
    }

    private static String formatAmount(double value) {
        return String.format(
                java.util.Locale.US,
                "%.2f",
                value);
    }

    private static String csv(String value) {

        if (value == null) {
            return "";
        }

        String escaped =
                value.replace("\"", "\"\"");

        return "\"" + escaped + "\"";
    }
}
