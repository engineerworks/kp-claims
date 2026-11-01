package com.kp.claims.core.repositories;

import com.google.gson.Gson;
import com.kp.claims.core.models.PolicyState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FirebasePolicyRepository
        implements PolicyRepository {

    private static final String FIREBASE_URL =
            "https://kp-claims-processing-default-rtdb"
                    + ".asia-southeast1.firebasedatabase.app";

    private static final int CONNECT_TIMEOUT_MS =
            5000;

    private static final int READ_TIMEOUT_MS =
            5000;

    private final Gson gson;

    public FirebasePolicyRepository() {
        this.gson = new Gson();
    }

    @Override
    public PolicyState findByPolicyHolderId(
            String policyHolderId) {

        if (policyHolderId == null
                || policyHolderId.trim().isEmpty()) {

            return null;
        }

        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    FIREBASE_URL
                            + "/"
                            + policyHolderId.trim()
                            + ".json");

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("GET");

            connection.setRequestProperty(
                    "Accept",
                    "application/json");

            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS);

            connection.setReadTimeout(
                    READ_TIMEOUT_MS);

            int responseCode =
                    connection.getResponseCode();

            if (responseCode
                    != HttpURLConnection.HTTP_OK) {

                System.err.println(
                        "Firebase returned HTTP "
                                + responseCode
                                + " for policy holder "
                                + policyHolderId);

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

                while ((line = reader.readLine())
                        != null) {

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

            System.err.println(
                    "Unable to retrieve policy holder "
                            + policyHolderId
                            + " from Firebase: "
                            + e.getMessage());

            return null;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
