package com.kp.claims.core.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoverageRuleServiceTest {

    private CoverageRuleService coverageRuleService;

    @BeforeEach
    void setUp() {
        coverageRuleService = new CoverageRuleService();
    }

    @Test
    void shouldResolveP001LabTestsRule() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Outpatient Services",
                "LAB TESTS"
        );

        assertEquals(
                "80% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveP002LabTestsRule() {

        String rule = coverageRuleService.findRule(
                "P002",
                "Outpatient Services",
                "LAB TESTS"
        );

        assertEquals(
                "90% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveP003LabTestsRule() {

        String rule = coverageRuleService.findRule(
                "P003",
                "Outpatient Services",
                "LAB TESTS"
        );

        assertEquals(
                "100% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveP001PrimaryCareRule() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Outpatient Services",
                "PRIMARY CARE OFFICE VISIT"
        );

        assertEquals(
                "40% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveP002PrimaryCareRule() {

        String rule = coverageRuleService.findRule(
                "P002",
                "Outpatient Services",
                "PRIMARY CARE OFFICE VISIT"
        );

        assertEquals(
                "50% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveP003PrimaryCareRule() {

        String rule = coverageRuleService.findRule(
                "P003",
                "Outpatient Services",
                "PRIMARY CARE OFFICE VISIT"
        );

        assertEquals(
                "60% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveNoChargeRuleForPreventiveCare() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Preventive Care",
                "ROUTINE PHYSICAL EXAM"
        );

        assertEquals(
                "No Charge",
                rule
        );
    }

    @Test
    void shouldResolveNoChargeRuleForMammograms() {

        String rule = coverageRuleService.findRule(
                "P002",
                "Preventive Care",
                "MAMMOGRAMS"
        );

        assertEquals(
                "No Charge",
                rule
        );
    }

    @Test
    void shouldResolveP003UrgentCareFlatRule() {

        String rule = coverageRuleService.findRule(
                "P003",
                "Emergency And Urgent Care",
                "URGENT CARE VISIT"
        );

        assertEquals(
                "$120 FLAT",
                rule
        );
    }

    @Test
    void shouldResolveP002UrgentCareFlatRule() {

        String rule = coverageRuleService.findRule(
                "P002",
                "Emergency And Urgent Care",
                "URGENT CARE VISIT"
        );

        assertEquals(
                "$100 FLAT",
                rule
        );
    }

    @Test
    void shouldResolveInpatientSurgeryRule() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Inpatient Hospital Care",
                "SURGERY"
        );

        assertEquals(
                "40% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolveInpatientRoomAndBoardRule() {

        String rule = coverageRuleService.findRule(
                "P003",
                "Inpatient Hospital Care",
                "ROOM AND BOARD"
        );

        assertEquals(
                "60% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldResolvePrescriptionGenericRule() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Prescription Drugs",
                "GENERIC"
        );

        assertEquals(
                "60% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldIgnoreCaseAndExtraSpaces() {

        String rule = coverageRuleService.findRule(
                "  p001  ",
                " outpatient services ",
                " lab tests "
        );

        assertEquals(
                "80% AFTER DEDUCTIBLE",
                rule
        );
    }

    @Test
    void shouldReturnNullWhenPlanDoesNotExist() {

        String rule = coverageRuleService.findRule(
                "P999",
                "Outpatient Services",
                "LAB TESTS"
        );

        assertNull(rule);
    }

    @Test
    void shouldReturnNullWhenCategoryDoesNotExist() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Unknown Category",
                "LAB TESTS"
        );

        assertNull(rule);
    }

    @Test
    void shouldReturnNullWhenSubCategoryDoesNotExist() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Outpatient Services",
                "UNKNOWN SERVICE"
        );

        assertNull(rule);
    }

    @Test
    void shouldReturnNullWhenPlanIdIsNull() {

        String rule = coverageRuleService.findRule(
                null,
                "Outpatient Services",
                "LAB TESTS"
        );

        assertNull(rule);
    }

    @Test
    void shouldReturnNullWhenMainCategoryIsNull() {

        String rule = coverageRuleService.findRule(
                "P001",
                null,
                "LAB TESTS"
        );

        assertNull(rule);
    }

    @Test
    void shouldReturnNullWhenSubCategoryIsNull() {

        String rule = coverageRuleService.findRule(
                "P001",
                "Outpatient Services",
                null
        );

        assertNull(rule);
    }
}
