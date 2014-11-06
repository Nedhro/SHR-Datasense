package org.sharedhealth.datasense.processors;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sharedhealth.datasense.client.MciWebClient;
import org.sharedhealth.datasense.model.EncounterBundle;
import org.sharedhealth.datasense.model.fhir.FHIRBundle;
import org.sharedhealth.datasense.model.Patient;
import org.sharedhealth.datasense.repository.PatientDao;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.sharedhealth.datasense.helpers.ResourceHelper.loadFromXmlFile;

public class PatientProcessorTest {

    @Mock
    PatientDao patientDao;

    @Mock
    MciWebClient webClient;

    @Mock
    Logger log;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void shouldDownloadAndSavePatientIfNotPresent() throws Exception {

        String healthId = "5927558688825933825";
        EncounterBundle bundle = new EncounterBundle();
        bundle.addContent(loadFromXmlFile("xmls/sampleEncounter.xml"));
        FHIRBundle fhirBundle = new FHIRBundle(bundle.getResourceOrFeed().getFeed());
        Patient patient = new Patient();
        when(webClient.identifyPatient(healthId)).thenReturn(patient);
        PatientProcessor processor = new PatientProcessor(null, webClient, patientDao);
        processor.process(fhirBundle.getEncounterCompositions().get(0));
        verify(patientDao).getPatientById(healthId);
        verify(patientDao).save(patient);
   }
}