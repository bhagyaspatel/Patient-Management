package com.pm.repository;

import com.pm.model.Patient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(@NotNull @Email String email, UUID id);
    // search patient who matches the passed email but does not match the passed id
}
