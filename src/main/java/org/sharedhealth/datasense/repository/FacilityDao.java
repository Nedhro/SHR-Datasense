package org.sharedhealth.datasense.repository;


import org.sharedhealth.datasense.model.Facility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class FacilityDao {

    @Autowired
    JdbcTemplate jdbcTemplate;

    public Facility findFacilityById(String facilityId) {
        List<Facility> facilities = jdbcTemplate.query("select facility_id, name, type, location_id from facility where facility_id=?",
                new Object[]{facilityId},
                new RowMapper<Facility>() {
                    @Override
                    public Facility mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return new Facility(rs.getString("facility_id"), rs.getString("name"), rs.getString("type"), rs.getString("location_id"));
                    }
                });
        return facilities.isEmpty() ? null : facilities.get(0);
    }

    public void save(Facility facility) {
        jdbcTemplate.update("insert into facility (facility_id, name, type, location_id) values(?, ? ,? ,?)",
                facility.getFacilityId(), facility.getFacilityName(), facility.getFacilityType(), facility.getFacilityLocationCode());
    }
}
