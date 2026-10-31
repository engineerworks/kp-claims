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

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.BufferedReader;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=KP Claims Real-Time Processing API",
                "sling.servlet.methods=" + HttpConstants.METHOD_POST,
                "sling.servlet.paths=" + "/bin/kp/claims/process"
        })
public class ClaimProcessorServlet extends SlingAllMethodsServlet {

    private final Gson gson = new Gson();
    private final ClaimEngineService engine = new ClaimEngineService();

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // 1. Read incoming JSON request payload
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            // 2. Parse request wrapper object
            ClaimRequestWrapper requestData = gson.fromJson(sb.toString(), ClaimRequestWrapper.class);

            if (requestData == null || requestData.claim == null) {
                response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\": \"Invalid request envelope mapping\"}");
                return;
            }

            // 3. Process the payload via the core math engine
            ClaimOutput output = engine.processClaim(requestData.claim, requestData.policy, requestData.rule);

            // 4. Return processed metrics
            response.getWriter().write(gson.toJson(output));

        } catch (Exception e) {
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"System failure processing transaction: " + e.getMessage() + "\"}");
        }
    }

    // Direct binding inner class to simulate target system lookups cleanly
    private static class ClaimRequestWrapper {
        public ClaimInput claim;
        public PolicyState policy;
        public String rule;
    }
}
