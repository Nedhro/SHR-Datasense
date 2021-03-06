package org.sharedhealth.datasense.handler;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sharedhealth.datasense.BaseIntegrationTest;
import org.sharedhealth.datasense.helpers.DatabaseHelper;
import org.sharedhealth.datasense.helpers.TestConfig;
import org.sharedhealth.datasense.launch.DatabaseConfig;
import org.sharedhealth.datasense.model.*;
import org.sharedhealth.datasense.model.fhir.BundleContext;
import org.sharedhealth.datasense.model.fhir.EncounterComposition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.sharedhealth.datasense.helpers.ResourceHelper.loadFromXmlFile;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource("/test-shr-datasense.properties")
@ContextConfiguration(classes = {DatabaseConfig.class, TestConfig.class})
public class DiagnosticReportResourceHandlerIT extends BaseIntegrationTest {
    @Autowired
    private DiagnosticReportResourceHandler diagnosticReportResourceHandler;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private EncounterComposition composition;
    private Resource diagnosticReport;

    private static final String PATIENT_HID = "98001046534";
    private static final String SHR_ENCOUNTER_ID = "shrEncounterId";

    @Before
    public void setUp() throws Exception {
        super.loadConfigParameters();
    }

    private void setUpData(String fileName, String diagnosticOrderResourceUuid) throws IOException, ParseException {
        Bundle bundle = loadFromXmlFile(fileName);
        BundleContext bundleContext = new BundleContext(bundle, SHR_ENCOUNTER_ID);
        composition = bundleContext.getEncounterCompositions().get(0);
        Patient patient = new Patient();
        patient.setDateOfBirth(new SimpleDateFormat("yyyy-MM-dd").parse("2013-12-28"));
        patient.setHid(PATIENT_HID);
        Encounter encounter = new Encounter();
        encounter.setEncounterId(SHR_ENCOUNTER_ID);
        composition.getEncounterReference().setValue(encounter);
        composition.getPatientReference().setValue(patient);
        Reference resourceReference = new Reference().setReference(diagnosticOrderResourceUuid);
        diagnosticReport = bundleContext.getResourceForReference(resourceReference);

    }

    @After
    public void tearDown() throws Exception {
        DatabaseHelper.clearDatasenseTables(jdbcTemplate);
    }

    @Test
    public void canHandleDiagnosticReportResource() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report.xml", "urn:uuid:b8d65b9c-8242-4db5-bb17-9a064ef8df16");
        assertTrue(diagnosticReportResourceHandler.canHandle(diagnosticReport));
    }

    @Test
    public void shouldSaveDiagnosticReport() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report.xml", "urn:uuid:b8d65b9c-8242-4db5-bb17-9a064ef8df16");
        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        assertEquals(1, savedDiagnosticReports.size());
        DiagnosticReport savedDiagnosticReport = savedDiagnosticReports.get(0);
        assertEquals(PATIENT_HID, savedDiagnosticReport.getPatientHid());
        assertEquals(SHR_ENCOUNTER_ID, savedDiagnosticReport.getEncounterId());
        assertEquals("812", savedDiagnosticReport.getFulfiller());
        assertEquals("RAD", savedDiagnosticReport.getReportCategory());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        assertEquals("22-03-2016", simpleDateFormat.format(savedDiagnosticReport.getReportDate()));
        assertEquals("BN00ZZZ", savedDiagnosticReport.getCode());
        assertEquals("501qb827-a67c-4q1f-a705-e5efe0q6a972", savedDiagnosticReport.getReportConcept());
        assertNotNull(savedDiagnosticReport.getUuid());

    }

    @Test
    public void shouldSaveLabReportWithoutCategory() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report_for_lab.xml", "urn:uuid:2f933127-feb9-4131-be06-4c92980d0b7b");
        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        assertEquals(1, savedDiagnosticReports.size());
        DiagnosticReport savedDiagnosticReport = savedDiagnosticReports.get(0);
        assertEquals(PATIENT_HID, savedDiagnosticReport.getPatientHid());
        assertEquals(SHR_ENCOUNTER_ID, savedDiagnosticReport.getEncounterId());
        assertEquals("812", savedDiagnosticReport.getFulfiller());
        assertEquals("LAB", savedDiagnosticReport.getReportCategory());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        assertEquals("31-08-2015", simpleDateFormat.format(savedDiagnosticReport.getReportDate()));
        assertEquals("20563-3", savedDiagnosticReport.getCode());
        assertEquals("79647ed4-a60e-4cf5-ba68-cf4d55956cba", savedDiagnosticReport.getReportConcept());
        assertNotNull(savedDiagnosticReport.getUuid());

    }

    @Test
    public void shouldSaveResultOfDiagnosticReportAsObservation() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report_for_lab.xml", "urn:uuid:2f933127-feb9-4131-be06-4c92980d0b7b");
        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        DiagnosticReport savedDiagnosticReport = savedDiagnosticReports.get(0);

        List<Observation> observations = findObsByEncounterId(SHR_ENCOUNTER_ID);
        assertEquals(1, observations.size());
        Observation observation = observations.get(0);
        assertEquals(savedDiagnosticReport.getReportId(), observation.getReportId());
        assertEquals("20.0", observation.getValue());
    }


    @Test
    public void shouldAssociateDiagnosticReportWithOrderUsingShrOrderUuid() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report.xml", "urn:uuid:b8d65b9c-8242-4db5-bb17-9a064ef8df16");
        String concatenatedShrOrderUuid = "shr-enc-12:880ae396-jfjf-4629-1930-f15206e63ab0#TR:501qb827-a67c-4q1f-a705-e5efe0q6a972";

        ProcedureRequest order = new ProcedureRequest();
        order.setEncounterId("shr-enc-12");
        order.setPatientHid(PATIENT_HID);
        order.setOrderer("someone");
        order.setOrderCategory("RAD");
        order.setOrderStatus("requested");
        order.setOrderConcept("501qb827-a67c-4q1f-a705-e5efe0q6a972");
        order.setShrOrderUuid(concatenatedShrOrderUuid);
        order.setOrderDate(new Date());
        Integer savedOrderId = saveOrder(order);

        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        DiagnosticReport savedDiagnosticReport = savedDiagnosticReports.get(0);
        assertEquals(savedOrderId, savedDiagnosticReport.getOrderId());
    }

    @Test
    public void shouldAssociateDiagnosticReportWithOrderUsingEncIdAndConceptId() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_diagnostic_report_for_lab.xml", "urn:uuid:2f933127-feb9-4131-be06-4c92980d0b7b");

        ProcedureRequest order = new ProcedureRequest();
        order.setEncounterId("shrEncounterId3");
        order.setPatientHid(PATIENT_HID);
        order.setOrderer("someone");
        order.setOrderCategory("LAB");
        order.setOrderStatus("requested");
        order.setcode("20563-3");
        order.setOrderConcept("79647ed4-a60e-4cf5-ba68-cf4d55956cba");
        order.setOrderDate(new Date());
        Integer savedOrderId = saveOrder(order);

        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        DiagnosticReport savedDiagnosticReport = savedDiagnosticReports.get(0);
        assertEquals(savedOrderId, savedDiagnosticReport.getOrderId());
    }

    @Test
    public void shouldNotSaveLocalDiagnosticReports() throws Exception {
        setUpData("stu3/p98001046534_encounter_with_local_diagnostic_report.xml", "urn:uuid:b8d65b9c-8242-4db5-bb17-9a064ef8df16");

        diagnosticReportResourceHandler.process(diagnosticReport, composition);
        List<DiagnosticReport> savedDiagnosticReports = findByEncounterId(SHR_ENCOUNTER_ID);
        assertEquals(0, savedDiagnosticReports.size());
    }


    private List<DiagnosticReport> findByEncounterId(String shrEncounterId) {
        String sql = "select report_id, patient_hid, encounter_id, report_datetime, report_category, code, fulfiller," +
                "report_concept, order_id from diagnostic_report where encounter_id= :encounter_id";
        HashMap<String, Object> map = new HashMap<>();
        map.put("encounter_id", shrEncounterId);
        return jdbcTemplate.query(sql, map, new RowMapper<DiagnosticReport>() {
            @Override
            public DiagnosticReport mapRow(ResultSet rs, int rowNum) throws SQLException {
                DiagnosticReport report = new DiagnosticReport();
                report.setOrderId(rs.getInt("order_id"));
                report.setReportId(rs.getInt("report_id"));
                report.setPatientHid(rs.getString("patient_hid"));
                report.setEncounterId(rs.getString("encounter_id"));
                report.setReportDate(rs.getDate("report_datetime"));
                report.setReportCategory(rs.getString("report_category"));
                report.setCode(rs.getString("code"));
                report.setFulfiller(rs.getString("fulfiller"));
                report.setReportConcept(rs.getString("report_concept"));
                return report;
            }
        });
    }

    private List<Observation> findObsByEncounterId(String shrEncounterId) {
        String sql = "select observation_id, patient_hid, encounter_id, concept_id, code, datetime, parent_id, value," +
                " uuid, report_id from observation where encounter_id= :encounter_id";
        return jdbcTemplate.query(sql, Collections.singletonMap("encounter_id", shrEncounterId), new
                RowMapper<Observation>() {

                    @Override
                    public Observation mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Observation observation = new Observation();
                        observation.setObservationId(rs.getInt("observation_id"));

                        Encounter encounter = new Encounter();
                        encounter.setEncounterId(rs.getString("encounter_id"));
                        observation.setEncounter(encounter);

                        Patient patient = new Patient();
                        patient.setHid(rs.getString("patient_hid"));
                        observation.setPatient(patient);

                        observation.setConceptId(rs.getString("concept_id"));
                        observation.setReferenceCode(rs.getString("code"));
                        observation.setParentId(rs.getString("parent_id"));
                        observation.setValue(rs.getString("value"));
                        observation.setUuid(rs.getString("uuid"));
                        observation.setReportId(rs.getInt("report_id"));

                        return observation;
                    }
                });
    }

    private Integer saveOrder(ProcedureRequest procedureRequest) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("patient_hid", procedureRequest.getPatientHid());
        map.put("encounter_id", procedureRequest.getEncounterId());
        map.put("order_datetime", procedureRequest.getOrderDate());
        map.put("order_category", procedureRequest.getOrderCategory());
        map.put("code", procedureRequest.getCode());
        map.put("orderer", procedureRequest.getOrderer());
        map.put("order_concept", procedureRequest.getOrderConcept());
        map.put("order_status", procedureRequest.getOrderStatus());
        map.put("uuid", procedureRequest.getUuid());
        map.put("shr_order_uuid", procedureRequest.getShrOrderUuid());

        String sql = "insert into procedure_request (patient_hid, encounter_id, order_datetime, order_category," +
                " code , orderer, order_concept, order_status, uuid, shr_order_uuid) values(:patient_hid, :encounter_id, " +
                ":order_datetime, :order_category, :code, :orderer, :order_concept, :order_status, :uuid, :shr_order_uuid)";

        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource(map), generatedKeyHolder);
        return generatedKeyHolder.getKey().intValue();
    }
}
