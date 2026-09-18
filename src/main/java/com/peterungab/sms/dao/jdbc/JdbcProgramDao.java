package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.dao.ProgramDao;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.model.Program;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class JdbcProgramDao implements ProgramDao {

    private static final String SELECT = "SELECT program_id, code, name, department FROM programs";

    private final JdbcTemplate jdbc;

    public JdbcProgramDao(Database db) {
        this.jdbc = new JdbcTemplate(db);
    }

    @Override
    public List<Program> findAll() {
        return jdbc.query(SELECT + " ORDER BY name", JdbcProgramDao::map);
    }

    @Override
    public Optional<Program> findById(int id) {
        return jdbc.queryOne(SELECT + " WHERE program_id = ?", JdbcProgramDao::map, id);
    }

    private static Program map(ResultSet rs) throws SQLException {
        return new Program(rs.getInt("program_id"), rs.getString("code"),
                rs.getString("name"), rs.getString("department"));
    }
}
