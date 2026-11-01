package com.kp.claims.core.servlets;

import com.google.gson.Gson;
import com.kp.claims.core.models.ClaimInput;
import com.kp.claims.core.models.PolicyState;
import com.kp.claims.core.models.ClaimOutput;
import com.kp.claims.core.services.ClaimEngineService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=KP Claims Firebase Microservice Adjudication API",
                "sling.servlet.methods=" + HttpConstants.METHOD_POST,
                "sling.servlet.paths=" + "/bin/kp/claims/process"
        })
public class ClaimProcessorServlet extends SlingAllMethodsServlet {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ClaimProcessorServlet.class);
    private final Gson gson = new Gson();
    private final ClaimEngineService engine = new ClaimEngineService();

    // Live Cloud REST API Connection endpoint configuration mapping
    private static final String FIREBASE_URL = "https://kp-claims-processing-default-rtdb.asia-southeast1.firebasedatabase.app";


    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // 1. Extract the network stream JSON data
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            ClaimRequestWrapper requestData = gson.fromJson(sb.toString(), ClaimRequestWrapper.class);
            if (requestData == null ||
                    requestData.claim == null ||
                    requestData.policy == null ||
                    requestData.rule == null ||
                    requestData.rule.trim().isEmpty()) {

                response.setStatus(
                        SlingHttpServletResponse.SC_BAD_REQUEST);

                response.getWriter().write(
                        "{\"error\":\"claim, policy and rule are required\"}");

                return;
            }

            String targetHolderId = requestData.claim.policyHolderId;

            PolicyState cloudPolicyState =
                    fetchPolicyFromFirebase(targetHolderId);

            if (cloudPolicyState != null && requestData.policy != null) {
                cloudPolicyState.currentIndividualAccumulated =
                        requestData.policy.currentIndividualAccumulated;

                cloudPolicyState.currentFamilyAccumulated =
                        requestData.policy.currentFamilyAccumulated;
            }

            ClaimOutput output =
                    engine.processClaim(
                            requestData.claim,
                            cloudPolicyState,
                            requestData.rule);
            // 4. Return the computed data array layout to the AEM user dashboard
            response.getWriter().write(gson.toJson(output));

        } catch (Exception e) {
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"System processing exception: " + e.getMessage() + "\"}");
        }
    }

    private PolicyState fetchPolicyFromFirebase(String holderId) {
        try {
            URL url = new URL(FIREBASE_URL +"/"+ holderId + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) {
                return null;
            }

            StringBuilder responseSb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    responseSb.append(line);
                }
            }
            conn.disconnect();

            String jsonResult = responseSb.toString();
            if (jsonResult == null || "null".equals(jsonResult.trim())) {
                return null; // Triggers E0001 "Policy holder does not exist" instantly inside engine rules
            }

            return gson.fromJson(jsonResult, PolicyState.class);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to fetch policy holder {} from Firebase",
                    holderId,
                    e);
            return null;
        }
    }

    private static class ClaimRequestWrapper {
        public ClaimInput claim;
        public PolicyState policy;
        public String rule;
    }
}
