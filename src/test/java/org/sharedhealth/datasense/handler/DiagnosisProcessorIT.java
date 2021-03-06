package org.sharedhealth.datasense.handler;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sharedhealth.datasense.helpers.DatabaseHelper;
import org.sharedhealth.datasense.helpers.TestConfig;
import org.sharedhealth.datasense.launch.DatabaseConfig;
import org.sharedhealth.datasense.model.Diagnosis;
import org.sharedhealth.datasense.model.Encounter;
import org.sharedhealth.datasense.model.Patient;
import org.sharedhealth.datasense.model.fhir.BundleContext;
import org.sharedhealth.datasense.model.fhir.EncounterComposition;
import org.sharedhealth.datasense.repository.DiagnosisDao;
import org.sharedhealth.datasense.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.*;
import static org.sharedhealth.datasense.helpers.ResourceHelper.loadFromXmlFile;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource("/test-shr-datasense.properties")
@ContextConfiguration(classes = {DatabaseConfig.class, TestConfig.class})
public class DiagnosisProcessorIT {
    @Autowired
    DiagnosisResourceHandler processor;

    @Autowired
    DiagnosisDao diagnosisDao;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9997);

    @Before
    public void setUp() throws Exception {
        processor = new DiagnosisResourceHandler(diagnosisDao);
    }

    @After
    public void tearDown() {
        DatabaseHelper.clearDatasenseTables(jdbcTemplate);
    }

    @Test
    public void shouldSaveDiagnosis() throws Exception {
        Bundle bundle = loadFromXmlFile("stu3/p98001046534_encounter_with_diagnoses.xml");
        String shrEncounterId = "shrEncounterId";
        BundleContext context = new BundleContext(bundle, shrEncounterId);
        EncounterComposition composition = context.getEncounterCompositions().get(0);
        Encounter encounter = new Encounter();
        encounter.setEncounterId(shrEncounterId);
        composition.getEncounterReference().setValue(encounter);
        Patient patient = new Patient();
        String hid = "98001046534";
        patient.setHid(hid);
        composition.getPatientReference().setValue(patient);
        Reference resourceReference = new Reference();
        resourceReference.setReference("urn:uuid:04e9f317-680c-4ff1-9942-bcb5e2b5243b");

        processor.process(context.getResourceForReference(resourceReference), composition);

        List<Diagnosis> diagnoses = findByEncounterId(shrEncounterId);
        assertEquals(1, diagnoses.size());
        Diagnosis diagnosis = diagnoses.get(0);
        assertNotNull(diagnosis.getUuid());
        assertEquals(shrEncounterId, diagnosis.getEncounter().getEncounterId());
        assertEquals("A90", diagnosis.getDiagnosisCode());
        assertEquals("07952dc2-5206-11e5-ae6d-0050568225ca", diagnosis.getDiagnosisConcept());
        assertEquals("active", diagnosis.getDiagnosisStatus());
        assertTrue(DateUtil.parseDate("2015-09-04").equals(diagnosis.getDiagnosisDateTime()));
        assertEquals(hid, diagnosis.getPatient().getHid());
    }

    private List<Diagnosis> findByEncounterId(String encounterId) {
        return jdbcTemplate.query(
                "select diagnosis_id, patient_hid, encounter_id, diagnosis_datetime, diagnosis_code, " +
                        "diagnosis_concept_id, " +
                        "diagnosis_status, uuid from diagnosis where encounter_id= :encounter_id", Collections
                        .singletonMap("encounter_id", encounterId),
                new RowMapper<Diagnosis>() {
                    @Override
                    public Diagnosis mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Diagnosis diagnosis = new Diagnosis();
                        diagnosis.setDiagnosisId(rs.getInt("diagnosis_id"));
                        diagnosis.setDiagnosisDateTime(new java.util.Date(rs.getTimestamp("diagnosis_datetime")
                                .getTime()));
                        diagnosis.setDiagnosisCode(rs.getString("diagnosis_code"));
                        diagnosis.setDiagnosisConceptId(rs.getString("diagnosis_concept_id"));
                        diagnosis.setDiagnosisStatus(rs.getString("diagnosis_status"));
                        diagnosis.setUuid(rs.getString("uuid"));

                        Encounter encounter = new Encounter();
                        encounter.setEncounterId(rs.getString("encounter_id"));
                        diagnosis.setEncounter(encounter);

                        Patient patient = new Patient();
                        patient.setHid(rs.getString("patient_hid"));
                        diagnosis.setPatient(patient);
                        return diagnosis;
                    }
                });
    }
}