package org.sharedhealth.datasense.repository;

import org.apache.commons.collections4.CollectionUtils;
import org.sharedhealth.datasense.model.Encounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class EncounterDao {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private final String qryGetLastEncounter = "SELECT created_at FROM encounter WHERE facility_id = :facility_id ORDER BY created_at DESC LIMIT 1";

    public void save(Encounter encounter) {

        HashMap<String, Object> map = new HashMap<>();
        map.put("encounter_id", encounter.getEncounterId());
        map.put("encounter_datetime", encounter.getEncounterDateTime());
        map.put("encounter_type", encounter.getEncounterType());
        map.put("visit_type", encounter.getEncounterVisitType());
        map.put("patient_hid", encounter.getPatient().getHid());
        map.put("location_id", encounter.getLocationCode());
        map.put("facility_id", encounter.getFacility().getFacilityId());
        jdbcTemplate.update("insert into encounter (encounter_id, encounter_datetime, encounter_type, visit_type, " +
                "patient_hid, encounter_location_id, facility_id) " +
                "values(:encounter_id, :encounter_datetime, :encounter_type , :visit_type , :patient_hid, " +
                ":location_id, :facility_id)", map);
    }

    public void deleteExisting(String encounterId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("encounter_id", encounterId);

        jdbcTemplate.update("delete from encounter where encounter_id = :encounter_id", map);

    }

    public Date getLastSyncedEncounterDateTime(String facilityId) {
        List<Date> results = jdbcTemplate.query(qryGetLastEncounter, Collections.singletonMap("facility_id", facilityId), new RowMapper<Date>() {
            @Override
            public Date mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Date(rs.getTimestamp("created_at").getTime());
            }
        });
        return CollectionUtils.isNotEmpty(results) ? results.get(0) : null;
    }

    public List<Map<String, Object>> getVisitTypesWithCount(String facilityId, String date) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("facility_id", facilityId);
        map.put("encounter_date", date);
        String query = "SELECT visit_type, COUNT(*) AS num FROM encounter WHERE facility_id = :facility_id AND DATE_FORMAT(encounter_datetime, '%d/%m/%Y') = :encounter_date GROUP BY visit_type";
        List<Map<String, Object>> maps = jdbcTemplate.query(query, map, new ColumnMapRowMapper());
        return maps;
    }


    public List<Map<String, Object>> getEncounterTypesWithCount(String facilityId, String startDate, String endDate) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("facility_id", facilityId);
        map.put("start_date", startDate);
        map.put("end_date", endDate);
        String query = "SELECT encounter_type ,count(*) as count FROM encounter WHERE facility_id = :facility_id AND \n" +
                "encounter_datetime >= STR_TO_DATE(:start_date, '%d/%m/%Y')\n" +
                "AND encounter_datetime <= STR_TO_DATE(:end_date, '%d/%m/%Y') group by encounter_type";
        List<Map<String, Object>> maps = jdbcTemplate.query(query, map, new ColumnMapRowMapper());
        return maps;
    }
}
