package com.kp.claims.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.framework.Constants;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=KP Claims Ingestion Add New Policy Profile API",
                "sling.servlet.methods=" + HttpConstants.METHOD_POST,
                "sling.servlet.paths=" + "/bin/kp/claims/add-policy"
        })
public class AddPolicyServlet extends SlingAllMethodsServlet {

    // Live Cloud REST API Connection endpoint configuration mapping
    private static final String FIREBASE_URL = "https://kp-claims-processing-default-rtdb.asia-southeast1.firebasedatabase.app";
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String targetHolderId = request.getParameter("policyHolderId");
        String payloadJson = request.getParameter("jsonData"); // Extracted structured model attributes

        if (targetHolderId == null || targetHolderId.trim().isEmpty() || payloadJson == null || payloadJson.trim().isEmpty()) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Missing required data profile context variables\"}");
            return;
        }

        try {
            // Establish an outbound network request channel directly to Firebase Realtime Database
            URL url = new URL(FIREBASE_URL + "/" + targetHolderId + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("PUT"); // PUT creates or overwrites the record at this specific ID node
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payloadJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int httpStatus = conn.getResponseCode();
            if (httpStatus == 200 || httpStatus == 201) {
                response.getWriter().write("{\"status\": \"Success\", \"message\": \"User profile record successfully synchronized directly to Firebase Cloud!\"}");
            } else {
                response.setStatus(httpStatus);
                response.getWriter().write("{\"status\": \"Failed\", \"message\": \"Cloud storage endpoint rejected data packet entry.\"}");
            }
            conn.disconnect();

        } catch (Exception e) {
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Firebase network communication breakdown: " + e.getMessage() + "\"}");
        }
    }
}
