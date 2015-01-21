package org.sharedhealth.datasense.handler.mappers;

import org.hl7.fhir.instance.model.CodeableConcept;
import org.hl7.fhir.instance.model.Coding;
import org.hl7.fhir.instance.model.Date;
import org.hl7.fhir.instance.model.DateTime;
import org.hl7.fhir.instance.model.Decimal;
import org.hl7.fhir.instance.model.String_;
import org.hl7.fhir.instance.model.Type;
import org.sharedhealth.datasense.util.DateUtil;

import java.util.List;

import static org.sharedhealth.datasense.util.FhirCodeLookupService.getConceptId;
import static org.sharedhealth.datasense.util.FhirCodeLookupService.getReferenceCode;

public class ObservationValueMapper {
    public String getObservationValue(Type value) {
        if (value instanceof String_) {
            return ((String_) value).getValue();
        } else if (value instanceof Decimal) {
            return ((Decimal) value).getValue().toString();
        } else if (value instanceof Date) {
            return DateUtil.parseDate(((Date) value).getValue().toString()).toString();
        } else if (value instanceof DateTime) {
            return DateUtil.parseDate(((DateTime) value).getValue().toString()).toString();
        } else if (value instanceof org.hl7.fhir.instance.model.Boolean){
            return ((org.hl7.fhir.instance.model.Boolean) value).getStringValue();
        }
        //TODO : Codeable concept should point to concept synced from TR (Can be done after we sync all concepts from TR).
        else if (value instanceof CodeableConcept) {
            List<Coding> codings = ((CodeableConcept) value).getCoding();
            String referenceCode = getReferenceCode(codings);
            if(referenceCode != null) {
                return referenceCode;
            } else {
                return getConceptId(codings);
            }
        }
        return null;
    }
}
