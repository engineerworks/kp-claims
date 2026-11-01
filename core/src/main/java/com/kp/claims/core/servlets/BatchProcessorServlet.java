package com.kp.claims.core.servlets;

import com.kp.claims.core.services.BatchProcessingService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION
                        + "=KP Claims CSV Batch Processing API",

                "sling.servlet.methods="
                        + HttpConstants.METHOD_POST,

                "sling.servlet.paths="
                        + "/bin/kp/claims/batch-process"
        }
)
public class BatchProcessorServlet
        extends SlingAllMethodsServlet {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    BatchProcessorServlet.class);

    private static final long MAX_UPLOAD_SIZE_BYTES =
            10L * 1024L * 1024L;

    private final BatchProcessingService
            batchProcessingService =
            new BatchProcessingService();

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        RequestParameter fileParameter =
                request.getRequestParameter("file");

        if (fileParameter == null) {
            writeJsonError(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Missing CSV upload. "
                            + "Expected multipart field named 'file'.");

            return;
        }

        String fileName =
                fileParameter.getFileName();

        if (fileName == null
                || fileName.trim().isEmpty()) {

            writeJsonError(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "The uploaded file does not have a file name.");

            return;
        }

        if (!fileName.toLowerCase()
                .endsWith(".csv")) {

            writeJsonError(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Only CSV files are supported.");

            return;
        }

        long fileSize = fileParameter.getSize();

        if (fileSize <= 0) {
            writeJsonError(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "The uploaded CSV is empty.");

            return;
        }

        if (fileSize > MAX_UPLOAD_SIZE_BYTES) {
            writeJsonError(
                    response,
                    SlingHttpServletResponse
                            .SC_REQUEST_ENTITY_TOO_LARGE,
                    "The uploaded CSV exceeds "
                            + "the 10 MB demo limit.");

            return;
        }

        response.setStatus(
                SlingHttpServletResponse.SC_OK);

        response.setContentType(
                "text/csv");

        response.setCharacterEncoding(
                "UTF-8");

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"output_claims.csv\"");

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate");

        try (
                InputStream inputStream =
                        fileParameter.getInputStream();

                OutputStream outputStream =
                        response.getOutputStream()
        ) {
            int processedCount =
                    batchProcessingService.process(
                            inputStream,
                            outputStream);

            LOGGER.info(
                    "Processed {} claims from uploaded CSV {}",
                    processedCount,
                    fileName);

        } catch (IllegalArgumentException e) {

            LOGGER.warn(
                    "Invalid CSV upload {}: {}",
                    fileName,
                    e.getMessage());

            /*
             * If response bytes were already written, the HTTP status
             * cannot reliably be replaced with JSON. Header validation
             * happens before output rows, so common validation errors
             * still reach this response cleanly.
             */
            if (!response.isCommitted()) {
                response.reset();

                writeJsonError(
                        response,
                        SlingHttpServletResponse.SC_BAD_REQUEST,
                        e.getMessage());
            }

        } catch (Exception e) {

            LOGGER.error(
                    "Batch processing failed for uploaded file {}",
                    fileName,
                    e);

            if (!response.isCommitted()) {
                response.reset();

                writeJsonError(
                        response,
                        SlingHttpServletResponse
                                .SC_INTERNAL_SERVER_ERROR,
                        "Unable to process the uploaded CSV.");
            }
        }
    }

    private void writeJsonError(
            SlingHttpServletResponse response,
            int status,
            String message)
            throws IOException {

        response.setStatus(status);
        response.setContentType(
                "application/json");
        response.setCharacterEncoding(
                "UTF-8");

        response.getWriter().write(
                "{\"error\":\""
                        + escapeJson(message)
                        + "\"}");
    }

    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
