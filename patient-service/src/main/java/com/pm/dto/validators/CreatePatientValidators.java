package com.pm.dto.validators;

public interface CreatePatientValidators {
}
// just creating a group, inside PatientRequestDto, we have specified that registeredDate belongs to this group
// now when i use @Validated, with default group it validates only default group fields (other than registeredDate), if I
// also specify CreatePatientValidators, it will check registeredDate field too