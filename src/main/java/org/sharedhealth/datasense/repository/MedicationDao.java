package org.sharedhealth.datasense.repository;

import org.sharedhealth.datasense.model.Encounter;
import org.sharedhealth.datasense.model.Medication;
import org.sharedhealth.datasense.model.MedicationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

@Component
public class MedicationDao {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Medication> findByEncounterId(String shrEncounterId) {
        String sql = "select datetime, encounter_id, status from medication where encounter_id=?";
        return jdbcTemplate.query(sql,new Object[]{shrEncounterId},new RowMapper<Medication>() {
            @Override
            public Medication mapRow(ResultSet rs, int rowNum) throws SQLException {
                Medication medication = new Medication();

                Date medicationDatetime = new Date(rs.getTimestamp("datetime").getTime());
                medication.setDateTime(medicationDatetime);

                Encounter encounter = new Encounter();
                encounter.setEncounterId(rs.getString("encounter_id"));
                medication.setEncounter(encounter);

                medication.setStatus(MedicationStatus.getMedicationStatus(rs.getString("status")));
                return medication;
            }
        });
    }

    public void save(Medication medication) {
        String sql = "insert into medication (datetime, encounter_id, status) " +
                "values(?,?, ?)";
        jdbcTemplate.update(sql,
                medication.getDateTime(),
                medication.getEncounter().getEncounterId(),
                medication.getStatus().getValue()
        );
    }
}
