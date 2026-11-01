package com.kp.claims.core.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CoverageRuleService {

    private static final String COVERAGE_FILE =
            "/plan_coverage.csv";

    private final Map<String, String> coverageRules =
            new HashMap<>();

    public CoverageRuleService() {
        loadCoverageRules();
    }

    public String findRule(
            String planId,
            String mainCategory,
            String subCategory) {

        String normalizedPlanId =
                normalize(planId);

        String normalizedMainCategory =
                normalize(mainCategory);

        String normalizedSubCategory =
                normalize(subCategory);

        if (normalizedPlanId.isEmpty()
                || normalizedMainCategory.isEmpty()
                || normalizedSubCategory.isEmpty()) {

            return null;
        }

        String key = buildKey(
                normalizedPlanId,
                normalizedMainCategory,
                normalizedSubCategory);

        return coverageRules.get(key);
    }

    private void loadCoverageRules() {

        try (
                InputStream inputStream =
                        CoverageRuleService.class
                                .getResourceAsStream(
                                        COVERAGE_FILE)
        ) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "Coverage reference file was not found: "
                                + COVERAGE_FILE);
            }

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            inputStream,
                                            StandardCharsets.UTF_8))
            ) {
                String header = reader.readLine();

                if (header == null) {
                    throw new IllegalStateException(
                            "Coverage reference file is empty");
                }

                String line;
                int rowNumber = 1;

                while ((line = reader.readLine()) != null) {
                    rowNumber++;

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    List<String> columns =
                            parseCsvLine(line);

                    if (columns.size() != 5) {
                        throw new IllegalStateException(
                                "Invalid PlanCoverage row "
                                        + rowNumber
                                        + ". Expected five columns.");
                    }

                    String mainCategory =
                            normalize(columns.get(0));

                    String subCategoriesValue =
                            columns.get(1);

                    String p001Rule =
                            normalizeRule(columns.get(2));

                    String p002Rule =
                            normalizeRule(columns.get(3));

                    String p003Rule =
                            normalizeRule(columns.get(4));

                    String[] subCategories =
                            subCategoriesValue.split("\\|");

                    for (String subCategory : subCategories) {

                        String normalizedSubCategory =
                                normalize(subCategory);

                        addRule(
                                "P001",
                                mainCategory,
                                normalizedSubCategory,
                                p001Rule);

                        addRule(
                                "P002",
                                mainCategory,
                                normalizedSubCategory,
                                p002Rule);

                        addRule(
                                "P003",
                                mainCategory,
                                normalizedSubCategory,
                                p003Rule);
                    }
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load coverage reference data",
                    e);
        }
    }

    private void addRule(
            String planId,
            String mainCategory,
            String subCategory,
            String rule) {

        String key = buildKey(
                normalize(planId),
                normalize(mainCategory),
                normalize(subCategory));

        coverageRules.put(key, rule);
    }

    private String buildKey(
            String planId,
            String mainCategory,
            String subCategory) {

        return planId
                + "|"
                + mainCategory
                + "|"
                + subCategory;
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ENGLISH);
    }

    private String normalizeRule(String value) {

        String rule = value == null
                ? ""
                : value.trim();

        if (rule.matches("\\d+(\\.\\d+)?")) {
            return "$" + rule + " FLAT";
        }

        return rule;
    }

    private List<String> parseCsvLine(String line) {

        List<String> values = new ArrayList<>();

        StringBuilder currentValue =
                new StringBuilder();

        boolean insideQuotes = false;

        for (int index = 0;
             index < line.length();
             index++) {

            char currentCharacter =
                    line.charAt(index);

            if (currentCharacter == '"') {

                if (insideQuotes
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {

                    currentValue.append('"');
                    index++;

                } else {
                    insideQuotes = !insideQuotes;
                }

            } else if (currentCharacter == ','
                    && !insideQuotes) {

                values.add(
                        currentValue
                                .toString()
                                .trim());

                currentValue.setLength(0);

            } else {
                currentValue.append(
                        currentCharacter);
            }
        }

        values.add(
                currentValue
                        .toString()
                        .trim());

        return values;
    }
}
