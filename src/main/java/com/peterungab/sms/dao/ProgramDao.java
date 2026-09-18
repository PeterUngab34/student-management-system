package com.peterungab.sms.dao;

import com.peterungab.sms.model.Program;

import java.util.List;
import java.util.Optional;

public interface ProgramDao {

    List<Program> findAll();

    Optional<Program> findById(int id);
}
