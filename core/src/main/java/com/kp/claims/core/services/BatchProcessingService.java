package com.kp.claims.core.services;

import com.google.gson.Gson;
import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.models.PolicyState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BatchProcessingService {

    private static final String FIREBASE_URL =
            "https://kp-claims-processing-default-rtdb.asia-southeast1"
                    + ".firebasedatabase.app";

    private final Gson gson;
    private final ClaimEngineService claimEngineService;

    public BatchProcessingService() {
        this.gson = new Gson();
        this.claimEngineService = new ClaimEngineService();
    }

    /**
     * Processes an input transaction CSV and writes a processed CSV.
     *
     * The same method is used by:
     * 1. BatchProcessorCLI
     * 2. BatchProcessorServlet
     *
     * @param inputStream  input transaction CSV
     * @param outputStream processed output CSV
     * @return number of transactions processed
     * @throws IOException when the input or output stream cannot be processed
     */
    public int process(
            InputStream inputStream,
            OutputStream outputStream) throws IOException {

        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Input CSV stream is required");
        }

        if (outputStream == null) {
            throw new IllegalArgumentException(
                    "Output CSV stream is required");
        }

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
            String headerLine = reader.readLine();

            if (headerLine == null
                    || headerLine.trim().isEmpty()) {

                throw new IllegalArgumentException(
                        "The uploaded CSV file is empty");
            }

            validateHeader(headerLine);

            writeOutputHeader(writer);

            String line;
            int sourceRowNumber = 1;

            while ((line = reader.readLine()) != null) {
                sourceRowNumber++;

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
                            sourceRowNumber,
                            e.getMessage());

                    processedCount++;
                    continue;
                }

                PolicyState state =
                        policyStateCache.get(
                                claim.policyHolderId);

                if (state == null) {
                    state = fetchPolicyFromFirebase(
                            claim.policyHolderId);

                    if (state != null) {
                        policyStateCache.put(
                                claim.policyHolderId,
                                state);
                    }
                }

                double preClaimIndividualAccumulated =
                        state != null
                                ? state.currentIndividualAccumulated
                                : 0.0;

                double preClaimFamilyAccumulated =
                        state != null
                                ? state.currentFamilyAccumulated
                                : 0.0;

                String activeRule =
                        determineCoverageRule(claim);

                ClaimOutput result =
                        claimEngineService.processClaim(
                                claim,
                                state,
                                activeRule);

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

    private void validateHeader(String headerLine) {

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
                "billed amount")) {

            throw new IllegalArgumentException(
                    "Invalid CSV header. Expected PolicyId, "
                            + "Policy holder Id, Date of service, "
                            + "Coverage Main Category, "
                            + "Coverage Sub Category and Billed Amount");
        }
    }

    private ClaimInput parseClaim(String line) {

        /*
         * This parser supports the sample assignment CSV.
         * The assignment data does not contain commas inside column values.
         *
         * Later, this can be replaced by Apache Commons CSV for full
         * quoted-field support.
         */
        String[] tokens = line.split(",", -1);

        if (tokens.length < 6) {
            throw new IllegalArgumentException(
                    "Expected six CSV columns");
        }

        ClaimInput claim = new ClaimInput();

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
                    Double.parseDouble(billedAmountText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid billed amount: "
                            + billedAmountText);
        }

        return claim;
    }

    private String determineCoverageRule(
            ClaimInput claim) {

        String subCategory =
                claim.subCategory == null
                        ? ""
                        : claim.subCategory
                        .trim()
                        .toUpperCase(Locale.ENGLISH);

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

    private PolicyState fetchPolicyFromFirebase(
            String holderId) {

        if (holderId == null
                || holderId.trim().isEmpty()) {

            return null;
        }

        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    FIREBASE_URL
                            + "/"
                            + holderId.trim()
                            + ".json");

            connection =
                    (HttpURLConnection)
                            url.openConnection();

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

    private void writeOutputHeader(
            BufferedWriter writer) throws IOException {

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

    private String cleanValue(String value) {

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

    private String formatAmount(double value) {

        return String.format(Locale.US, "%.2f", value);
    }

    private String csv(String value) {

        if (value == null) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");

        return "\"" + escaped + "\"";
    }
}
