export function validateProfessor({ name, email, professorId, password }, create = false) {
  if (!name.trim() || !email.trim() || !professorId.trim() || (create && !password.trim())) return 'Please fill in all fields.';
  if (name.trim().length > 100) return 'Name must be at most 100 characters.';
  if (email.trim().length > 254) return 'Email must be at most 254 characters.';
  if (!/^[^\s@]+@[^\s@]+$/.test(email.trim())) return 'Enter a valid email address.';
  // Browser email validation and backend @Email also validate address syntax.
  if (professorId.trim().length > 255) return 'Professor ID must be at most 255 characters.';
  if (create && (password.length < 8 || new TextEncoder().encode(password).length > 72)) return 'Password must be at least 8 characters and at most 72 UTF-8 bytes.';
  return '';
}

export function professorValidationError(error) {
  if (error?.response?.data?.code !== 'VALIDATION_FAILED') return 'Something went wrong. Please try again.';
  return 'Please check all required fields, email format, field lengths, and the password limit (8 characters minimum, 72 UTF-8 bytes maximum).';
}
