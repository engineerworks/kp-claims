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

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=KP Claims Direct Cloud Upload Proxy",
                "sling.servlet.methods=" + HttpConstants.METHOD_POST,
                "sling.servlet.paths=" + "/bin/kp/claims/upload-url"
        })
public class CloudUploadProxyServlet extends SlingAllMethodsServlet {

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String fileName = request.getParameter("fileName");

        if (fileName == null || fileName.isEmpty()) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Missing fileName parameter\"}");
            return;
        }

        // Enterprise Pattern: Instead of buffering files locally, we return a mock presigned cloud signature.
        // In a live cloud production environment, this would call the AWS S3 SDK / Azure Blob SDK.
        long timestamp = System.currentTimeMillis();
        String targetCloudBucketUrl = "https://amazonaws.com" + timestamp + "_" + fileName;
        String securityToken = "?AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE&Signature=vj7sdMmGBZURG%2B6m6mM%3D&Expires=" + (timestamp / 1000 + 3600);

        String jsonResponse = String.format(
                "{\"uploadUrl\": \"%s\", \"fileKey\": \"%s\", \"processingMessage\": \"Memory streaming vector initialized successfully\"}",
                targetCloudBucketUrl + securityToken, "uploads/" + timestamp + "_" + fileName
        );

        response.getWriter().write(jsonResponse);
    }
}
