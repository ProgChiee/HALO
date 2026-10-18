import { validateFullName } from './fullName.js';
export const registrationHints = {
 name: "Letters, spaces, hyphens, apostrophes, periods and commas are welcome.",
 email: 'Use your @paterostechnologicalcollege.edu.ph school email.',
 studentId: 'Required format: YYYY-NNNNN, for example 2023-00123. Include the hyphen.',
 section: 'For example, BSHM 1A (up to 50 characters).',
 yearLevel: 'Select your current year level.',
 password: 'At least 8 characters; maximum 72 UTF-8 bytes. Spaces count as characters.',
 confirmPassword: 'Enter the same password again.',
};
export function validateRegistration(input) {
 const name=validateFullName(input.name);
 const value={...input,name:name.value,email:input.email.trim().toLowerCase(),studentId:input.studentId.trim(),section:input.section.trim().replace(/\s+/g,' ')};
 const errors={};
 if(name.error)errors.name=name.error;
 if(value.email.length>254 || !/^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@paterostechnologicalcollege\.edu\.ph$/.test(value.email))errors.email=registrationHints.email;
 if(!/^[0-9]{4}-[0-9]{5}$/.test(value.studentId))errors.studentId=registrationHints.studentId;
 if(!value.section || value.section.length>50 || !/^[\p{L}\p{M}\p{N} .'-]+$/u.test(value.section))errors.section=registrationHints.section;
 if(!['FIRST_YEAR','SECOND_YEAR'].includes(value.yearLevel))errors.yearLevel=registrationHints.yearLevel;
 if(!value.password.trim() || value.password.length<8 || new TextEncoder().encode(value.password).length>72)errors.password=registrationHints.password;
 if(!value.confirmPassword || value.password!==value.confirmPassword)errors.confirmPassword='Passwords must match.';
 return {value,errors};
}
export function registrationFailure(error){
 if(error?.response?.data?.code==='REGISTRATION_VALIDATION_FAILED')return {message:'Please correct the indicated fields.',errors:error.response.data.errors ?? {}};
 const status=error?.response?.status;
 return {errors:{},message:status===409?'An account with these details already exists. Check your email and Student ID.':status===400?'Check your registration details and year level.':status===429?'Too many attempts. Please wait before trying again.':status>=500?'Registration is temporarily unavailable. Please try again later.':error?.response?'Registration could not be completed. Please try again.':'Could not reach HALO. Check your connection before trying again.'};
}
