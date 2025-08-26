package com.pm.exceptions;

import com.pm.dto.PatientRequestDto;

public class PatientNotFoundException extends RuntimeException{
    public PatientNotFoundException(String message){
        super(message);
    }
}
