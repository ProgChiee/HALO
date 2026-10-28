import { validateFullName } from './fullName.js';
export const registrationHints = {
 name: "Letters, spaces, hyphens, apostrophes, periods and commas are welcome.",
 email: 'Use your @paterostechnologicalcollege.edu.ph school email.',
 studentId: 'Required format: 2 digits + AHRT + hyphen + 4 digits, for example 29AHRT-0168. Include the hyphen.',
 section: 'For example, BSHM 1A (up to 50 characters).',
 yearLevel: 'Select your current year level.',
 password: 'At least 8 characters; maximum 72 UTF-8 bytes. Spaces count as characters.',
 confirmPassword: 'Enter the same password again.',
};
export const academicYearLevels = ['FIRST_YEAR', 'SECOND_YEAR'];
export const yearLabels = { FIRST_YEAR: '1st Year', SECOND_YEAR: '2nd Year' };
export function validateAcademicField(field, input) {
 const value = typeof input === 'string' ? (field === 'section' ? input.trim().replace(/\s+/g, ' ') : field === 'yearLevel' ? input : input.trim()) : '';
 const valid = field === 'studentId' ? /^[0-9]{2}AHRT-[0-9]{4}$/.test(value)
   : field === 'section' ? Boolean(value && value.length <= 50 && /^[\p{L}\p{M}\p{N} .'-]+$/u.test(value))
   : field === 'yearLevel' && academicYearLevels.includes(value);
 return { value, error: valid ? '' : registrationHints[field] || 'Select a valid academic field.' };
}
export function validateRegistration(input) {
 const name=validateFullName(input.name);
 const value={...input,name:name.value,email:input.email.trim().toLowerCase(),studentId:input.studentId.trim(),section:input.section.trim().replace(/\s+/g,' ')};
 const errors={};
 if(name.error)errors.name=name.error;
 if(value.email.length>254 || !/^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@paterostechnologicalcollege\.edu\.ph$/.test(value.email))errors.email=registrationHints.email;
 for (const field of ['studentId', 'section', 'yearLevel']) {
  const checked = validateAcademicField(field, value[field]);
  if (checked.error) errors[field] = checked.error;
 }
 if(!value.password.trim() || value.password.length<8 || new TextEncoder().encode(value.password).length>72)errors.password=registrationHints.password;
 if(!value.confirmPassword || value.password!==value.confirmPassword)errors.confirmPassword='Passwords must match.';
 return {value,errors};
}
export function registrationFailure(error){
 if(error?.response?.data?.code==='REGISTRATION_VALIDATION_FAILED')return {message:'Please correct the indicated fields.',errors:error.response.data.errors ?? {}};
 const status=error?.response?.status;
 return {errors:{},message:status===409?'An account with these details already exists. Check your email and Student ID.':status===400?'Check your registration details and year level.':status===429?'Too many attempts. Please wait before trying again.':status>=500?'Registration is temporarily unavailable. Please try again later.':error?.response?'Registration could not be completed. Please try again.':'Could not reach HALO. Check your connection before trying again.'};
}
