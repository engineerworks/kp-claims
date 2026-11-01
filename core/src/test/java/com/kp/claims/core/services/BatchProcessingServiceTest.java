package com.kp.claims.core.services;

import com.kp.claims.core.models.PolicyState;
import com.kp.claims.core.repositories.PolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchProcessingServiceTest {

    private static final String HEADER =
            "PolicyId,"
                    + "Policy holder Id,"
                    + "Date of service,"
                    + "Coverage Main Category,"
                    + "Coverage Sub Category,"
                    + "Billed Amount\n";

    private FakePolicyRepository policyRepository;
    private BatchProcessingService batchProcessingService;

    @BeforeEach
    void setUp() {

        policyRepository =
                new FakePolicyRepository();

        batchProcessingService =
                new BatchProcessingService(
                        new ClaimEngineService(),
                        new CoverageRuleService(),
                        policyRepository
                );
    }

    @Test
    void shouldProcessClaimsSequentiallyForSamePolicyHolder()
            throws Exception {

        policyRepository.addPolicy(
                "1000011",
                createPolicy(
                        "P001",
                        6000.0,
                        12000.0,
                        0.0,
                        0.0,
                        true
                )
        );

        String inputCsv =
                HEADER
                        + "100001,1000011,2016-05-06,"
                        + "Inpatient Hospital Care,"
                        + "ROOM AND BOARD,1000\n"

                        + "100001,1000011,2016-05-07,"
                        + "Inpatient Hospital Care,"
                        + "ROOM AND BOARD,1000\n"

                        + "100001,1000011,2016-05-08,"
                        + "Inpatient Hospital Care,"
                        + "SURGERY,4000\n"

                        + "100001,1000011,2016-05-09,"
                        + "Inpatient Hospital Care,"
                        + "ROOM AND BOARD,1000\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(4, result.processedCount);

        String[] outputRows =
                result.outputCsv.split("\\R");

        assertEquals(5, outputRows.length);

        assertTrue(
                outputRows[1].contains(
                        "\"40% AFTER DEDUCTIBLE\""
                )
        );

        assertTrue(
                outputRows[1].contains(
                        "1000.00,1000.00,0.00"
                )
        );

        /*
         * Claim 2 must begin with the state produced by claim 1.
         */
        assertTrue(
                outputRows[2].contains(
                        ",1000.00,1000.00,,,"
                )
        );

        /*
         * Claim 3 must begin with the state produced by claim 2.
         */
        assertTrue(
                outputRows[3].contains(
                        ",2000.00,2000.00,,,"
                )
        );

        /*
         * Claim 4 begins after the individual deductible reaches $6,000.
         * The P001 inpatient rule is 40%, so on a $1,000 bill:
         * plan pays $400 and policyholder pays $600.
         */
        assertTrue(
                outputRows[4].contains(
                        "1000.00,600.00,400.00"
                )
        );

        assertTrue(
                outputRows[4].contains(
                        ",6000.00,6000.00,,,"
                )
        );

        assertTrue(
                outputRows[4].contains(
                        "INDIVIDUAL"
                )
        );
    }

    @Test
    void shouldLoadPolicyFromRepositoryOnlyOncePerBatch()
            throws Exception {

        policyRepository.addPolicy(
                "1000011",
                createPolicy(
                        "P001",
                        6000.0,
                        12000.0,
                        0.0,
                        0.0,
                        true
                )
        );

        String inputCsv =
                HEADER
                        + "100001,1000011,2016-05-06,"
                        + "Inpatient Hospital Care,"
                        + "ROOM AND BOARD,1000\n"

                        + "100001,1000011,2016-05-07,"
                        + "Inpatient Hospital Care,"
                        + "ROOM AND BOARD,1000\n"

                        + "100001,1000011,2016-05-08,"
                        + "Inpatient Hospital Care,"
                        + "SURGERY,4000\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(3, result.processedCount);

        assertEquals(
                1,
                policyRepository.getLookupCount(
                        "1000011"
                )
        );
    }

    @Test
    void shouldResolveCoverageRuleUsingPolicyPlan()
            throws Exception {

        policyRepository.addPolicy(
                "1000021",
                createPolicy(
                        "P002",
                        5000.0,
                        10000.0,
                        5000.0,
                        5000.0,
                        true
                )
        );

        String inputCsv =
                HEADER
                        + "100002,1000021,2016-07-20,"
                        + "Outpatient Services,"
                        + "LAB TESTS,1200\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"90% AFTER DEDUCTIBLE\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "1200.00,120.00,1080.00"
                )
        );
    }

    @Test
    void shouldReturnE0001WhenPolicyHolderDoesNotExist()
            throws Exception {

        String inputCsv =
                HEADER
                        + "100001,9999999,2016-10-12,"
                        + "Prescription Drugs,"
                        + "GENERIC,61.40\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"E0001\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "\"Policy holder does not exist\""
                )
        );

        assertEquals(
                1,
                policyRepository.getLookupCount(
                        "9999999"
                )
        );
    }

    @Test
    void shouldReturnE0002WhenCoverageIsInactive()
            throws Exception {

        policyRepository.addPolicy(
                "1000071",
                createPolicy(
                        "P003",
                        6000.0,
                        12000.0,
                        4460.82,
                        4460.82,
                        false
                )
        );

        String inputCsv =
                HEADER
                        + "100007,1000071,2016-07-10,"
                        + "Emergency And Urgent Care,"
                        + "URGENT CARE VISIT,250\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"E0002\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "\"Coverage has expired or is inactive\""
                )
        );
    }

    @Test
    void shouldUsePlanSpecificFlatDollarRule()
            throws Exception {

        policyRepository.addPolicy(
                "1000071",
                createPolicy(
                        "P003",
                        6000.0,
                        12000.0,
                        0.0,
                        0.0,
                        true
                )
        );

        String inputCsv =
                HEADER
                        + "100007,1000071,2016-07-10,"
                        + "Emergency And Urgent Care,"
                        + "URGENT CARE VISIT,250\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"$120 FLAT\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "250.00,130.00,120.00"
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        ",0.00,0.00,,,"
                )
        );
    }

    @Test
    void shouldReturnE0005WhenCoverageRuleIsMissing()
            throws Exception {

        policyRepository.addPolicy(
                "1000011",
                createPolicy(
                        "P001",
                        6000.0,
                        12000.0,
                        0.0,
                        0.0,
                        true
                )
        );

        String inputCsv =
                HEADER
                        + "100001,1000011,2016-07-20,"
                        + "Unknown Category,"
                        + "UNKNOWN SERVICE,1000\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"E0005\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "\"Coverage rule not found for plan, "
                                + "main category and subcategory\""
                )
        );
    }

    @Test
    void shouldPreserveMalformedRowAsE0004()
            throws Exception {

        String inputCsv =
                HEADER
                        + "100001,1000011,2016-07-20,"
                        + "Outpatient Services,"
                        + "LAB TESTS,NOT_A_NUMBER\n";

        ProcessingResult result =
                process(inputCsv);

        assertEquals(1, result.processedCount);

        assertTrue(
                result.outputCsv.contains(
                        "\"E0004\""
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "Invalid billed amount: NOT_A_NUMBER"
                )
        );

        assertTrue(
                result.outputCsv.contains(
                        "Original row:"
                )
        );

        assertEquals(
                0,
                policyRepository.getTotalLookupCount()
        );
    }

    @Test
    void shouldRejectInvalidCsvHeader() {

        String inputCsv =
                "Wrong,Header,Columns\n"
                        + "1,2,3\n";

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> process(inputCsv)
                );

        assertTrue(
                exception.getMessage().contains(
                        "Invalid CSV header"
                )
        );
    }

    @Test
    void shouldRejectEmptyCsv() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> process("")
                );

        assertEquals(
                "The uploaded CSV file is empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullInputStream() {

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchProcessingService.process(
                                null,
                                outputStream
                        )
                );

        assertEquals(
                "Input CSV stream is required",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullOutputStream() {

        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(
                        HEADER.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchProcessingService.process(
                                inputStream,
                                null
                        )
                );

        assertEquals(
                "Output CSV stream is required",
                exception.getMessage()
        );
    }

    private ProcessingResult process(
            String inputCsv)
            throws Exception {

        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(
                        inputCsv.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        int processedCount =
                batchProcessingService.process(
                        inputStream,
                        outputStream
                );

        String outputCsv =
                new String(
                        outputStream.toByteArray(),
                        StandardCharsets.UTF_8
                );

        return new ProcessingResult(
                processedCount,
                outputCsv
        );
    }

    private PolicyState createPolicy(
            String planId,
            double individualThreshold,
            double familyThreshold,
            double individualAccumulated,
            double familyAccumulated,
            boolean active) {

        PolicyState policy =
                new PolicyState();

        policy.planId =
                planId;

        policy.individualThreshold =
                individualThreshold;

        policy.familyThreshold =
                familyThreshold;

        policy.currentIndividualAccumulated =
                individualAccumulated;

        policy.currentFamilyAccumulated =
                familyAccumulated;

        policy.isActive =
                active;

        return policy;
    }

    private static class ProcessingResult {

        private final int processedCount;
        private final String outputCsv;

        private ProcessingResult(
                int processedCount,
                String outputCsv) {

            this.processedCount =
                    processedCount;

            this.outputCsv =
                    outputCsv;
        }
    }

    private static class FakePolicyRepository
            implements PolicyRepository {

        private final Map<String, PolicyState> policies =
                new HashMap<>();

        private final Map<String, Integer> lookupCounts =
                new HashMap<>();

        void addPolicy(
                String policyHolderId,
                PolicyState policyState) {

            policies.put(
                    policyHolderId,
                    policyState
            );
        }

        @Override
        public PolicyState findByPolicyHolderId(
                String policyHolderId) {

            lookupCounts.put(
                    policyHolderId,
                    getLookupCount(policyHolderId) + 1
            );

            return policies.get(
                    policyHolderId
            );
        }

        int getLookupCount(
                String policyHolderId) {

            Integer count =
                    lookupCounts.get(
                            policyHolderId
                    );

            return count == null
                    ? 0
                    : count;
        }

        int getTotalLookupCount() {

            int total = 0;

            for (Integer count
                    : lookupCounts.values()) {

                total += count;
            }

            return total;
        }
    }
}
