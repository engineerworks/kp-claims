package com.kp.claims.core.servlets;

import com.google.gson.Gson;
import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.models.PolicyState;
import com.kp.claims.core.services.ClaimEngineService;
import com.kp.claims.core.services.CoverageRuleService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION
                        + "=KP Claims Real-Time Adjudication API",
                "sling.servlet.methods="
                        + HttpConstants.METHOD_POST,
                "sling.servlet.paths="
                        + "/bin/kp/claims/process"
        }
)
public class ClaimProcessorServlet
        extends SlingAllMethodsServlet {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ClaimProcessorServlet.class);

    private static final String FIREBASE_URL =
            "https://kp-claims-processing-default-rtdb"
                    + ".asia-southeast1.firebasedatabase.app";

    private final Gson gson =
            new Gson();

    private final ClaimEngineService engine =
            new ClaimEngineService();

    private final CoverageRuleService coverageRuleService =
            new CoverageRuleService();

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {

            StringBuilder requestBody =
                    new StringBuilder();

            String line;

            try (
                    BufferedReader reader =
                            request.getReader()
            ) {
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }
            }

            ClaimRequestWrapper requestData =
                    gson.fromJson(
                            requestBody.toString(),
                            ClaimRequestWrapper.class);

            if (requestData == null
                    || requestData.claim == null
                    || requestData.policy == null) {

                response.setStatus(
                        SlingHttpServletResponse.SC_BAD_REQUEST);

                response.getWriter().write(
                        "{\"error\":"
                                + "\"claim and policy are required\"}");

                return;
            }

            ClaimInput claim =
                    requestData.claim;

            PolicyState requestPolicy =
                    requestData.policy;

            /*
             * Firebase is still used to verify that the policyholder
             * exists and to retrieve the policyholder's stored
             * policy information.
             */
            PolicyState cloudPolicyState =
                    fetchPolicyFromFirebase(
                            claim.policyHolderId);

            /*
             * A missing Firebase policy continues to flow to
             * ClaimEngineService so that the existing E0001
             * business error is returned.
             */
            if (cloudPolicyState == null) {

                ClaimOutput output =
                        engine.processClaim(
                                claim,
                                null,
                                "");

                response.getWriter().write(
                        gson.toJson(output));

                return;
            }

            /*
             * Assignment requirement:
             *
             * Real-time callers supply the individual and family
             * accumulated deductible values.
             */
            cloudPolicyState.currentIndividualAccumulated =
                    requestPolicy.currentIndividualAccumulated;

            cloudPolicyState.currentFamilyAccumulated =
                    requestPolicy.currentFamilyAccumulated;

            /*
             * Use Firebase planId as the authoritative plan
             * assignment when available.
             */
            String planId =
                    cloudPolicyState.planId;

            if (planId == null
                    || planId.trim().isEmpty()) {

                planId = requestPolicy.planId;
            }

            /*
             * Resolve the coverage rule from PlanCoverage.
             *
             * The caller no longer supplies the coverage rule.
             */
            String coverageRule =
                    coverageRuleService.findRule(
                            planId,
                            claim.mainCategory,
                            claim.subCategory);

            if (coverageRule == null
                    || coverageRule.trim().isEmpty()) {

                ClaimOutput output =
                        createCoverageRuleError(claim);

                response.getWriter().write(
                        gson.toJson(output));

                return;
            }

            ClaimOutput output =
                    engine.processClaim(
                            claim,
                            cloudPolicyState,
                            coverageRule);

            response.getWriter().write(
                    gson.toJson(output));

        } catch (Exception e) {

            LOGGER.error(
                    "Real-time claim processing failed",
                    e);

            response.setStatus(
                    SlingHttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR);

            response.getWriter().write(
                    "{\"error\":"
                            + "\"Unable to process claim\"}");
        }
    }

    private PolicyState fetchPolicyFromFirebase(
            String holderId) {

        if (holderId == null
                || holderId.trim().isEmpty()) {

            return null;
        }

        HttpURLConnection connection =
                null;

        try {

            URL url =
                    new URL(
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

                LOGGER.error(
                        "Firebase returned HTTP {} "
                                + "for policy holder {}",
                        responseCode,
                        holderId);

                return null;
            }

            StringBuilder responseBody =
                    new StringBuilder();

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            connection
                                                    .getInputStream(),
                                            StandardCharsets.UTF_8))
            ) {

                String line;

                while ((line =
                        reader.readLine()) != null) {

                    responseBody.append(line);
                }
            }

            String jsonResult =
                    responseBody
                            .toString()
                            .trim();

            if (jsonResult.isEmpty()
                    || "null".equals(jsonResult)) {

                return null;
            }

            return gson.fromJson(
                    jsonResult,
                    PolicyState.class);

        } catch (Exception e) {

            LOGGER.error(
                    "Unable to retrieve policy holder {} "
                            + "from Firebase",
                    holderId,
                    e);

            return null;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
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

    private static class ClaimRequestWrapper {

        public ClaimInput claim;

        /*
         * For REST mode this holds the deductible values
         * explicitly supplied by the caller.
         */
        public PolicyState policy;
    }
}
