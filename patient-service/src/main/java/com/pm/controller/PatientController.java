package com.pm.controller;

import com.pm.dto.PatientRequestDto;
import com.pm.dto.PatientResponseDTO;
import com.pm.dto.validators.CreatePatientValidators;
import com.pm.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@Tag(name="Patient", description = "API for managing Patients") // For swagger docs: http://server:port/context-path/swagger-ui.html
public class PatientController {
    @Autowired
    private PatientService patientService;

    @GetMapping
    @Operation(summary = "Get patients")
    public ResponseEntity<List<PatientResponseDTO>> getPatients(){
        List<PatientResponseDTO> patientResponseDTOList =  patientService.getPatients();
        return ResponseEntity.ok().body(patientResponseDTOList);
    }

    @PostMapping
    @Operation(summary = "Create patients")
    public ResponseEntity<PatientResponseDTO> createPatient(
            @Validated({Default.class, CreatePatientValidators.class}) @RequestBody PatientRequestDto patientRequestDto
    ){
        PatientResponseDTO patientResponseDTO = patientService.createPatient(patientRequestDto);
        return ResponseEntity.ok().body(patientResponseDTO);
    }

    @PutMapping("/{uid}")
    @Operation(summary = "Update patients")
    public ResponseEntity<PatientResponseDTO> updatePatient(
            @Valid @PathVariable(name = "uid") UUID uid,
            @Valid @RequestBody PatientRequestDto patientRequestDto
    ){
        PatientResponseDTO patientResponseDTO = patientService.updatePatient(uid, patientRequestDto);
        return ResponseEntity.ok().body(patientResponseDTO);
    }

    @DeleteMapping("/{uid}")
    @Operation(summary = "Delte patients")
    public ResponseEntity<Void> deletePatient(
            @Valid @PathVariable(name = "uid") UUID uid
    ){
        patientService.deletePatient(uid);
        return ResponseEntity.noContent().build();
    }
}
