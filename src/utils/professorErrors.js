// Fixed messages only: never display arbitrary server messages or payloads.
const MESSAGES = {
  "AUTHENTICATION_REQUIRED": "Please sign in to continue.",
  "ACCESS_DENIED": "You do not have permission to access this resource.",
  "RESOURCE_NOT_FOUND": "The requested resource is unavailable.",
  "INVALID_REQUEST": "Check the request fields and try again.",
  "INVALID_PAGINATION": "Page must be non-negative and size must be positive.",
  "RESOURCE_CONFLICT": "The change conflicts with existing data. Reload before trying again.",
  "SUBJECT_CODE_ALREADY_EXISTS": "This subject code is already used. Choose another code.",
  "SUBJECT_NAME_ALREADY_EXISTS": "This subject name is already used. Choose another name.",
  "SUBJECT_HAS_CONTENT": "This subject still contains weeks or learning modules. Remove its content before deleting the subject.",
  "WEEK_NUMBER_ALREADY_EXISTS": "This subject already has that week number. Choose another week number.",
  "STALE_MODULE_OPERATION": "This module changed. Reload it before trying again.",
  "MODULE_ALREADY_APPROVED": "This lesson is already approved. Reload to see its current status.",
  "MODULE_GENERATION_REQUIRED": "Generate the lesson successfully before this action.",
  "MODULE_NOT_EDITABLE": "Published lessons cannot be changed through the draft editor.",
  "MODULE_MATERIALS_REQUIRED": "Upload at least one original lesson file before publishing.",
  "MODULE_MATERIAL_UNREADABLE": "Replace the unreadable original lesson file and try again.",
  "MODULE_MATERIAL_UNSUPPORTED": "Replace unsupported original lesson files before publishing.",
  "MODULE_MATERIAL_MISMATCH": "Reload and check this module's attachments before publishing.",
  "MODULE_MATERIALS_TOO_LARGE": "Reduce the original lesson material size before publishing.",
  "MODULE_INDEX_UNAVAILABLE": "Lesson material preparation is unavailable. Try again later.",
  "UPLOAD_FILE_UNSUPPORTED": "Only readable PDF, PNG, and JPEG files are supported.",
  "UPLOAD_FILE_UNREADABLE": "The file is empty, corrupt, protected, or unreadable. Choose a readable file.",
  "UPLOAD_FILE_TOO_LARGE": "Each lesson file must be no larger than 3 MB.",
  "UPLOAD_FILENAME_REQUIRED": "A filename is required.",
  "MODULE_FILE_LIMIT_EXCEEDED": "A module can contain at most 10 lesson files.",
  "PROFESSOR_REQUEST_FAILED": "The request could not be completed. Please try again later.",
  "VALIDATION_FAILED": "Check the required fields, text lengths, year level, positive week number, and lesson sources.",
  "INVALID_MULTIPART_REQUEST": "The upload could not be parsed. Select your files again and retry."
};
export function professorErrorMessage(error, fallback = 'The request failed. Please try again.') {
  const code = error?.response?.data?.code;
  if (Object.hasOwn(MESSAGES, code)) return MESSAGES[code];
  const status = error?.response?.status;
  if (status === 401) return MESSAGES.AUTHENTICATION_REQUIRED;
  if (status === 403) return MESSAGES.ACCESS_DENIED;
  if (status === 404) return MESSAGES.RESOURCE_NOT_FOUND;
  if (status === 409) return MESSAGES.RESOURCE_CONFLICT;
  if (status === 413) return MESSAGES.UPLOAD_FILE_TOO_LARGE;
  if (status === 400) return MESSAGES.VALIDATION_FAILED;
  if (status >= 500) return MESSAGES.PROFESSOR_REQUEST_FAILED;
  if (['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)) return 'The request took too long. Check the current state before retrying.';
  if (error?.code === 'ERR_NETWORK') return 'Cannot reach the server. Check your connection and retry.';
  return fallback;
}
