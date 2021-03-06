package org.sharedhealth.datasense.client;

import org.apache.log4j.Logger;
import org.sharedhealth.datasense.client.exceptions.ConnectionException;
import org.sharedhealth.datasense.config.DatasenseProperties;
import org.sharedhealth.datasense.model.Patient;
import org.sharedhealth.datasense.util.MapperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static org.sharedhealth.datasense.util.HeaderUtil.getHrmAccessTokenHeaders;

@Component
public class MciWebClient {

    private DatasenseProperties properties;
    private IdentityServiceClient identityServiceClient;
    private Logger log = Logger.getLogger(MciWebClient.class);

    @Autowired
    public MciWebClient(DatasenseProperties properties, IdentityServiceClient identityServiceClient) {
        this.properties = properties;
        this.identityServiceClient = identityServiceClient;
    }

    public Patient identifyPatient(String healthId) throws URISyntaxException, IOException {
        String response = getResponse(healthId);
        if (response != null) {
            return MapperUtil.readFrom(response, Patient.class);
        }
        return null;
    }

    public String get(URI url) throws URISyntaxException, IOException {
        log.info("Reading from " + url);
        Map<String, String> headers = getHrmAccessTokenHeaders(identityServiceClient.getOrCreateToken(), properties);
        headers.put("Accept", "application/atom+xml");
        String response = null;
        try {
            response = new WebClient().get(url, headers);
        } catch (ConnectionException e) {
            log.error(String.format("Could not fetch. Exception: %s", e));
            if (e.getErrorCode() == 401) {
                log.error("Unauthorized, clearing token.");
                identityServiceClient.clearToken();
            }
            throw new IOException("Could not fetch from url" + url, e);
        }
        return response;
    }

    private String getResponse(final String healthId) throws URISyntaxException, IOException {
        URI mciURI = getMciURI(healthId);
        log.info("Reading from " + mciURI);
        Map<String, String> headers = getHrmAccessTokenHeaders(identityServiceClient.getOrCreateToken(), properties);
        headers.put("Accept", "application/json");
        String response = null;
        try {
            response = new WebClient().get(mciURI, headers);
        } catch (ConnectionException e) {
            String message = String.format("Could not identify patient with healthId [%s]", healthId);
            log.error(message, e);
            if (e.getErrorCode() == 401) {
                log.error("Unauthorized, clearing token.");
                identityServiceClient.clearToken();
            }
            throw new IOException(message, e);
        }
        return response;
    }

    private URI getMciURI(String healthId) throws URISyntaxException {
        return new URI(properties.getMciPatientUrl() + "/" + healthId);
    }
}
