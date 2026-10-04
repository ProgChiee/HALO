# Module-scoped HALO mentor

The existing MentorService now retrieves only the session module's original files.
StudentMentorController endpoints and DTOs are unchanged. Role, ownership, year-level
subject eligibility, APPROVED/COMPLETED and previous-week checks remain enforced.

## Flow

Upload files through existing professor endpoints, then Generate. AiGenerationService
prepares a module index before lesson generation; Approve/Publish is unchanged.
Older published modules build the index on first mentor open. Each question is
classified against a compact catalog derived from only that module's file chunks.
Gemini selects up to six chunk IDs; the backend validates membership. The answer
stage receives only those original excerpts, bounded same-session history and the
published lesson as secondary context. It checks relevance again. Accepted responses
include file/page references. An unrelated question returns the fixed scope refusal
as a normal chat message, without calling the answer stage.

Source priority: original excerpts, then published lesson, then general knowledge
only to explain concepts already present in the originals. Questions can ask for
examples, simpler explanations or related terminology without matching exact wording.
Prompts reject scope expansion and instructions embedded in source/history/question.
Invalid model JSON, unknown chunk IDs or unsupported citations fail closed.

## Index

ModuleMaterialIndex is a support component, not a second mentor service.
PDFBox 3.0.8 extracts PDF text page-by-page using Loader.loadPDF:
https://pdfbox.apache.org/3.0/migration.html
Image-only pages and PNG/JPEG uploads use Gemini transcription. Mixed text/image PDF
pages use their extractable text; embedded diagrams on those pages are not separately
transcribed. OCR and topic-description quality require live evaluation.

Chunks are 2,400 characters with 240-character overlap. Gemini produces a short topic
catalog during indexing. Retrieval is LLM semantic selection over this catalog, not a
vector database. Per answer: at most six chunks (14,400 source-text characters), 8,000
published-lesson characters, and 6,000 conversation characters. The full PDF is not
resent on each chat message.

Cache: uploads/module-index/{moduleId}.json. Fingerprints include module ID, file IDs,
names, MIME types and file bytes. Added, removed and changed originals rebuild the
index. Each file's module ownership and the cached module ID are checked. No global
corpus is searched. Missing originals never fall back to general knowledge.

Limits: 10 files, 3 MB per file, 200 pages per PDF, 256 chunks per module. Exceeding
limits returns an explicit error rather than silently skipping files. First indexing
is synchronous and can be slow: one topic-description call per chunk plus transcription
for scanned/image materials. Subsequent requests reuse the cached index after file
fingerprint checks. Keep uploads private (already gitignored); no cache endpoint exists.

## Backend files changed

- pom.xml: PDFBox dependency.
- component/ModuleMaterialIndex.java: extraction, chunking, ownership and cache.
- service/MentorService.java: module-only retrieval and scoped answer generation.
- service/AiGenerationService.java: prepare index during Generate with files.
- test ModuleScopedMentorTest.java: real PDF fixtures with controlled Gemini responses.
- tests AiGenerationServiceTest.java and MentorOpenTest.java: constructor wiring.

All Java paths are under src/main/java/com/ptc/halo or src/test/java/com/ptc/halo.
No schema migration, new controller, duplicate service or relaxed security rule.
Text-only generation remains supported, but module chat requires original uploaded files.

## Tests

Maven compile and 40 focused backend tests passed with zero failures/errors. These
include ten module-scope checks: direct PDF answer, related explanation, unrelated
question, other-module context/IDs, multiple PDFs, changed/removed files, file ownership,
answer-stage refusal, long-PDF retrieval bounds and missing-original rejection.
Gemini is mocked: tests verify pipeline behavior and real PDF extraction, not live
semantic accuracy. LLM decisions cannot guarantee factual correctness or perfect
prompt-injection resistance; evaluate representative real course questions before use.

Run from HALO:

    .\mvnw.cmd "-Dtest=ModuleScopedMentorTest,MentorOpenTest,StudentLessonAccessTest,StudentMentorHttpTest,StudentLessonTransactionTest,AiGenerationServiceTest" test

Reload Maven in IntelliJ, rebuild and restart the workspace HALO project. Use HALO as
working directory and keep its uploads. A valid Gemini key is required for indexing
and chat. Generate/approve a hospitality PDF module; test a direct question, a related
explanation, Java/photosynthesis, a topic exclusive to another module, and two PDFs.

## Current frontend state

During continuation, the frontend on disk had changed to a mock version: mock login,
local subject/week catalog, and mock aiMentorService replies; the previous shared API
client, tests and docs were absent. Backend tests above do not establish that this
mock frontend is connected. Real integration requires the login JWT, backend week IDs,
GET /api/student/ai-learning-modules/week/{weekId}, POST /api/student/mentor/open/{moduleId},
and POST /api/student/mentor/message/{sessionId}. Never send weekId in place of moduleId.
