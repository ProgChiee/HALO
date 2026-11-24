// Fixed messages only: never display arbitrary server messages or payloads.
const MESSAGES = {
  OBTL_RECOGNITION_UNVERIFIED: "The suggested boundaries could not be verified. Review the PDF using the existing source controls.",
  OBTL_RECOGNITION_BUDGET: "This extraction exceeds the recognition budget. Use manual source review.",
  OBTL_RECOGNITION_UNAVAILABLE: "Structure recognition is busy or unavailable. Manual source review is available.",
  SOURCE_REGION_REVIEW_REQUIRED: "Review one original row and enter a section label (up to 100 letters, numbers or standard punctuation).",
  SOURCE_REGION_MISMATCH: "Select every highlighted region of the same section in page order, then confirm again.",
  INSTRUCTIONAL_SOURCES_REQUIRED: "Source-Grounded mode requires instructional references. Add and confirm them, or explicitly confirm OBTL-Guided AI Lesson Expansion and regenerate before review and publication.",
  EXPANSION_GENERATION_REQUIRED: "Confirm the curriculum sources and generate the OBTL-guided lesson before academic review and publication. Supplementary PDFs are optional in this mode.",
  INVALID_SOURCE_SELECTION: "The selected source sections are invalid. Reload and review them.",
  EXPANSION_REVIEW_REQUIRED: "Review and correct the AI elaborations, then acknowledge responsibility for academic accuracy before approval.",
  EXPANSION_SCOPE_INVALID: "The lesson is incomplete or outside the confirmed curriculum scope. Correct it or regenerate.",
  INVALID_EXPANSION_CONTENT: "Complete objectives, explanations, examples and summary (up to 30,000 characters each).",
  SOURCE_BOUNDARY_REVIEW_REQUIRED: "Table boundaries could not be isolated safely. Review the original PDF and provide a clearly bounded chapter-specific reference, then extract again.",
  SCOPED_SOURCE_PDF_REQUIRED: "Chapter-scoped selection requires readable PDF files. Replace image sources with instructional PDFs.",
  SOURCE_CONFIRMATION_REQUIRED: "Extract the saved PDF or HALO workbook and confirm the selected sections before generation or approval.",
  SOURCE_EXTRACTION_FAILED: "Source extraction failed. Use a readable, unprotected PDF and try again.",
  SOURCE_REEXTRACTION_REQUIRED: "Materials or the extraction version changed. Use Extract / Refresh Sources, review the new boundaries, select the generation mode and confirm again. Published lessons stay unchanged.",
  SOURCE_SCOPE_UNAVAILABLE: "The saved source selection is unavailable. Re-extract and review the sources.",
  SOURCE_SELECTION_REQUIRED: "Select at least one chapter-specific source section.",
  SOURCE_TEXT_LIMIT: "Selected text exceeds the safe processing limit. Use smaller chapter-specific references.",
  QUIZ_GENERATION_FAILED: "The quiz could not be generated. Please try again.",
  QUIZ_GENERATION_INVALID: "The AI returned an incomplete or invalid quiz. Please retry quiz generation or lesson approval. No quiz was saved.",
  REVISION_REQUIRES_PUBLISHED: "Only published lessons can be revised.",
  REVISION_NOT_FOUND: "This revision is no longer available. Reload the lesson.",
  REVISION_REVIEW_REQUIRED: "Save and review the latest revision before publishing.",
  INVALID_YOUTUBE_URL: "Please provide a valid YouTube video URL.",
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
  "MATERIAL_NOT_RELEVANT": "The uploaded material does not appear to match this lesson. Please upload a relevant learning material.",
  "MATERIAL_RELEVANCE_UNAVAILABLE": "Material relevance could not be checked. Please try again.",
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
  "EXAM_RECORD_ONLY": "This examination is a record only. Confirm review guide coverage before generating.",
  "EXAM_COVERAGE_REQUIRED": "Select one examination and explicitly confirm its purpose and review guide coverage.",
  "INVALID_MULTIPART_REQUEST": "The upload could not be parsed. Select your files again and retry."
};
export function professorErrorMessage(error, fallback = 'The request failed. Please try again.') {
  const code = error?.response?.data?.code;
  if (code === 'INVALID_OBTL_WORKBOOK') return error.response.data.message || 'Check the HALO workbook template.';
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
