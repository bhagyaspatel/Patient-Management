package com.pm.service;

import com.pm.dto.PatientRequestDto;
import com.pm.dto.PatientResponseDTO;
import com.pm.exceptions.PatientNotFoundException;
import com.pm.grpc.BillingServiceGrpcClient;
import com.pm.kafka.KafkaProducer;
import com.pm.mapper.PatientMapper;
import com.pm.model.Patient;
import com.pm.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.pm.exceptions.EmailAlreadyExistException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    @Autowired
    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient, KafkaProducer kafkaProducer){
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<PatientResponseDTO> getPatients(){
        List<Patient> patientList = patientRepository.findAll();
        return patientList.stream().map(PatientMapper::getDto).toList();
    }

    public PatientResponseDTO createPatient(PatientRequestDto patientRequestDto){
        if(patientRepository.existsByEmail(patientRequestDto.getEmail())){
            throw new EmailAlreadyExistException("The email already exist " + patientRequestDto.getEmail());
        }

        Patient patient = PatientMapper.toModel(patientRequestDto);
        Patient savedPatient = patientRepository.save(patient);

        billingServiceGrpcClient.createBillingAccount(patient.getId().toString(), patient.getName(), patient.getEmail());

        kafkaProducer.sendEvent(savedPatient);

        return PatientMapper.getDto(savedPatient);
    }

    public PatientResponseDTO updatePatient(UUID uid, PatientRequestDto patientRequestDto){
        Patient patient = patientRepository.findById(uid).orElseThrow(() -> new PatientNotFoundException("Patient with given id:" + uid + " does not exist"));

        if(patientRepository.existsByEmailAndIdNot(patientRequestDto.getEmail(), uid)){
            throw new EmailAlreadyExistException("The email already exist with other patient: " + patientRequestDto.getEmail());
        }

        patient.setName(patientRequestDto.getName());
        patient.setAddress(patientRequestDto.getAddress());
        patient.setEmail(patientRequestDto.getEmail());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDto.getDateOfBirth()));

        Patient updatedPatient = patientRepository.save(patient);

        return PatientMapper.getDto(updatedPatient);
    }

    public void deletePatient(UUID uid){
        patientRepository.deleteById(uid);
    }
}
