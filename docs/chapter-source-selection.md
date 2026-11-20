# Chapter/week source selection — local implementation and rollout boundary

This implementation has not been deployed. No existing local `halo_db`, production database, or uploaded object was changed. The Student Registry/authentication working tree is unrelated and must be preserved. The schema change below is pending separate operator review and approval.

## Professor workflow

### Explicit generation policy

Source-Grounded Lesson (default, including older snapshots) keeps the instructional-reference and sufficiency requirements. OBTL-Guided AI Lesson Expansion is an explicit separate mode: a confirmed outline alone is sufficient curriculum input; supplements are optional. The generated content is AI elaboration, not extracted PDF facts. A separate provider check evaluates completeness and topic scope. It does not certify factual accuracy.

For expansion: confirm the mode and exact chapter spans; Generate (or Review Changes for a revision); inspect the original curriculum previews and edit objectives, explanations/key concepts, examples/scenarios and summary in the academic-review panel. Check the responsibility acknowledgment and Save Academic Review. The backend revalidates corrections, records actor/time and a hash of all four fields, and changes the selection identity. Then explicitly Approve & Publish / Publish Revision. Edits, reconfirmation and regeneration require another acknowledgment. No automatic publication occurs.

The source snapshot adds optional `mode`, `generatedHash`, and `review` metadata; existing snapshots default to Source-Grounded mode. No additional SQL migration is required. New `POST .../sources/review` uses existing Professor/acting authorization, ownership and optimistic versions. Provider work uses a detached candidate outside the authorization transaction, followed by locked version checks. Original lessons and immutable revision history are not rewritten.

Expansion Mentor retrieval uses only the reviewed, published generated lesson as its teaching index, with explicit AI-generated provenance and content-bound source IDs. The OBTL remains curriculum provenance. It never falls back to the full PDF. Old conversation selection IDs cannot enter the new prompt. Responses disclose the approved AI-generated teaching reference; supplied IDs are still mandatory. Source-Grounded Mentor behavior remains strict. Academic acknowledgment is required before expansion assessment preparation, which uses the four reviewed lesson fields, not raw OBTL or unselected lesson text.

### Ruled-table extraction (obtl-table-spans-v3)

Version 3 excludes filled, page-sized background rectangles from table-rule detection; stroked borders, smaller filled geometry and real table separators remain subject to the existing checks. The reviewed three-page Middle East Chapter 1 supplement previously produced an empty `ambiguous-p1` proposal because its background paint was mistaken for a table. It now uses the normal single-heading proposal across pages 1–3, requiring Professor confirmation and the existing relevance/sufficiency check. This is not an automatic certification of instructional accuracy or a whole-OBTL override. Link captions and editorial text are preserved for Professor review.

`ObtlSupplementTest` uses the private fixture via `-Dsupplement.pdf=<local path>` (SHA-256 `25117BEFD2A87979AAD226B2C86E6437018FCAA77F416AD839B2D082919DC566`). Real-file tests skip if fixtures are absent; synthetic background/stroke and multi-chapter tests always run. Refresh older draft extractions explicitly; published snapshots are not rewritten.

PDFBox reads drawn cell borders and cell-local text. Chapter ownership follows the row's geometric bounds, not the vertically centered heading baseline. Exact extracted text offsets, page numbers and row coordinates are retained. Merged week cells are read across their actual borders. Blank chapter cells on matching continuation grids extend the previous section and require explicit Professor review.

Missing separators, recognized borderless curriculum tables, conflicting chapter columns, and incompatible continuation grids fail closed: ambiguous proposals cannot be confirmed (`SOURCE_BOUNDARY_REVIEW_REQUIRED`). Review the original PDF and provide a clearly bounded chapter-specific reference; a checkbox cannot override unsafe boundaries. Arbitrary borderless/mixed layouts, rotated tables and scanned pages are not guaranteed to be recoverable. No OCR is introduced.

Explicit Extract / Refresh Sources replaces older extraction caches and clears draft confirmation. Existing published snapshots are not rewritten and remain readable. Old extraction versions cannot be newly confirmed, regenerated or approved without refreshing. Outline-only, source-ID validation, publication and Mentor selection rules remain unchanged.

Regression fixture: the six-page International Cuisine OBTL, SHA-256 `BA446F26560B64301414FE965C53466F990142BCBB759174294AA62B30FBB108`. `ObtlTableBoundaryTest` accepts `-Dcuisine.pdf=<local path>`; it skips the original-document check when that privately supplied PDF is unavailable. Synthetic fixtures always run. No original PDF is added to the repository.

1. Save materials using the existing Author Lesson editor. For an existing published lesson, create a separate revision first; Students continue seeing the published version.
2. Choose **Extract / Refresh Sources**. The server reads the saved PDF originals and proposes Chapter, Unit, Module, Lesson and Week sections. Nothing is sent to Gemini during extraction.
3. Open each exact-text preview. Inspect page numbers, text offsets, topics/outcomes, table reading order, repeated headings and the proposed ending. Title-term matches are suggestions, not semantic approval or week-to-chapter equivalence.
4. Select only the sections belonging to the module. Unchecked sections are excluded. A PDF without recognizable headings receives a whole-document proposal requiring explicit single-topic review; it is never silently accepted as Chapter 1.
5. Leave curriculum outlines marked as outline-only. Check the instructional-material option only for sections containing actual explanations, definitions, procedures or worked examples. Confirm the selection explicitly. Confirmation clears previously generated draft content.
6. Choose the generation mode. In Source-Grounded mode an outline-only selection remains a Professor-only plan; substantive references must pass relevance/sufficiency checks. In OBTL-Guided expansion, confirmed outlines can generate detailed instruction without a supplement, subject to completeness/scope validation and the separate academic-review workflow above.
7. Add supplementary chapter-specific PDFs using the existing material-upload or revision-upload controls. Retain the original OBTL attachment; it does not need to be uploaded again. Refresh extraction and confirm the new selection. Unchanged document extraction can be reused. Sources, checksums and confirmation are revalidated before generation/publication.
8. Review the generated instructional lesson, then use existing approval/publication controls. Initial approval still generates its assessment. Revision publication retains existing quiz-review and Student progression rules.

Save material edits before using source controls. A stale version produces a conflict and requires reload. Extraction failures do not replace the saved selection. Ambiguous/wrong proposals must not be confirmed; supply a readable chapter-specific PDF if the detector cannot propose correct boundaries. This version does not provide a manual text-span editor or OCR.

## Persistence and migration

Review-only migration: `HALO/database/V20261118_01__lesson_source_scope.sql`.

```sql
ALTER TABLE ai_learning_modules ADD COLUMN source_scope LONGTEXT NULL;
ALTER TABLE mentor_messages ADD COLUMN source_selection_id VARCHAR(36) NULL;
```

No automatic migration runner was introduced. Existing schema-validation configuration is unchanged. Do not start this backend against an existing database missing these columns.

The smallest aggregate design reuses existing module and lesson-revision records rather than adding a parallel document/revision hierarchy:

- `source_scope` contains format version 1; a server-generated selection UUID; module ID; document identity, filename and SHA-256; extraction algorithm version and immutable extracted page text; exact UTF-16 start/end offsets; proposed sections and warnings; confirmed choices; confirming actor/time; instructional designation and verification result.
- The existing module primary key, foreign keys, optimistic `@Version`, revision version/base-version and module locking protect ownership, stale writes and publication. No new relational indexes or foreign keys are needed for this aggregate because lookups remain by module/session, never JSON scans.
- Existing draft/revision JSON payloads include `sourceScope`. Publishing copies that exact snapshot into the module and existing immutable revision-history payload. Draft changes do not change the published selection. Existing `revisedBy`, publication and history metadata retain the revision/actor provenance.
- Attachment row IDs may change during revision publication; the immutable snapshot retains its original identity, while the server checks the complete current filename/checksum multiset and module ID. Source-selection file IDs are serialized as strings to preserve large temporary revision IDs in JavaScript.
- Mentor messages get the current selection UUID when persisted. Null legacy messages remain intact. Existing session/request indexes suffice for the bounded recent-history query; no scan/index on the new tag is used.

Both additions are nullable, with no backfill or content conversion. The exact migration is exercised on disposable H2 in MySQL compatibility mode with synthetic legacy rows. This is **not** native MariaDB/MySQL migration verification. DDL is not transactionally reversible on those engines: rehearse both statements and verify both columns before starting the new backend. Do not automatically retry a partially applied migration.

## Generation and Student Mentor isolation

- New/unpublished PDF lessons require confirmed source selection before generation and approval. Existing published legacy lessons remain unchanged and unscoped; converting one requires a separate Professor-reviewed revision. An unconverted legacy revision retains the legacy source policy until explicit conversion.
- Scoped generation sends selected **instructional text chunks** only, with module/subject titles. It does not attach the complete PDF, use excluded file text, forward unselected raw lesson text/AI notes, or accept arbitrary provider source IDs.
- Source-Grounded outline plans contain selected outline spans only and cannot publish. Explicit expansion mode uses confirmed curriculum spans for generation and the academically reviewed lesson for Student teaching; it never presents elaborations as PDF-extracted facts.
- Provider sufficiency/relevance and returned JSON are checked. Generated source IDs must belong to the selected instructional chunks. Prompts treat documents as untrusted data and forbid unsupported elaboration. These controls constrain inputs and provenance; they cannot mathematically prove every generated claim is true. Professor review remains essential.
- Scoped indexes use the published selection UUID, document checksums and exact spans. They bypass legacy disk indexes entirely, so an old full-PDF cache cannot reintroduce other chapters. Changing selection changes source IDs/fingerprints; foreign/obsolete source IDs fail validation.
- Saved Mentor context is included only when its source-selection tag matches the current published selection. Previous messages remain visible in chat history but are not provider evidence for a new selection. Request reconciliation/polling rejects an exchange from an older selection with the existing `MENTOR_STATE_CHANGED` contract.
- Admin Student Preview has client-supplied conversation history without trusted selection provenance. For scoped modules only, that history is omitted from AI prompts; Preview follow-ups may ask for clarification. Normal Student and authorized Support exchanges retain context tagged with the current selection. Preview still writes no real Student messages.
- Scoped Mentor prompts omit old generated/entered lesson content as secondary evidence and require support from selected excerpts. Existing English/Filipino/Taglish intent and reply behavior remains in place.
- Assessment generation for scoped lessons uses reviewed generated instructional fields and omits unselected raw `lessonText`. Scoring, question validation, approval, quiz versioning and progression logic are unchanged.

This is source isolation for AI inputs, not a new file-download access-control feature. Existing authorized attachment downloads may still return the complete original OBTL. Legacy published lessons intentionally retain their previous full-document retrieval behavior until reviewed conversion.

## Extraction limits and deployment considerations

- Readable PDFs only in the scoped workflow; no OCR, image interpretation or external extraction service.
- Existing 3 MiB/file and ten-file limits; at most 200 pages/file, 600,000 extracted characters/module, 256 detected boundaries/file and 256 selected chunks/module.
- A child Java process performs parsing with a 96 MiB heap, 64 MiB Metaspace cap, one advertised processor, SerialGC, a 30-second deadline and bounded output. One extraction worker is admitted at a time. Failed/stuck workers are terminated and the slot is released.
- PDF encryption/extraction restrictions, actions/scripts, embedded files and supported external-action structures are rejected. Structural traversal is bounded. Documents are never executed and URLs are not fetched by extraction.
- JVM caps do not bound all native memory or provide an OS sandbox. Container memory/resource isolation and operational load testing remain necessary. No claim is made that this plus the main HALO process reliably fits Render Free 512 MiB.
- Source snapshots add bounded text to module/revision/history storage and retrieval. Reads verify original-object checksums and therefore incur storage I/O; scoped indexes deliberately avoid an additional cache. Measure real workloads before optimizing or deploying.
- Windows classpath worker operation and packaged Spring Boot `PropertiesLauncher` worker entry were tested without Spring startup. Linux/container launch and termination still require rehearsal.

## Verification and remaining acceptance work

Automated coverage uses synthetic PDFs, mocked Gemini/storage, MockMvc authorization and disposable in-memory H2. It covers eleven chapters, same-page/cross-page boundaries, repeated headings, week/chapter mismatch warnings, corrupt/encrypted/active-content PDFs, worker recovery, confirmation, outline publication rejection, supplementary-source reuse, checksum/module mismatches, source ID validation, stale draft edits, immutable published history, current-selection Mentor context and rejection of legacy cache/history source IDs. A service-level H2 test follows outline draft through confirmed instructional generation and publication; it is not a live browser/Gemini test.

The packaged offline worker was also run read-only on the existing local `OBTL 1.pdf`: 9 pages, 11 detected sections, Chapter 1 proposal on pages 2–3, 853 selected characters, matching SHA-256 and no later chapter heading in the selected text. These counts do not prove correct multi-column reading order or instructional sufficiency. The known outline-only content still requires supplemental instruction.

Final local results (November 18, 2026): 232 targeted backend tests, 230 passed and 2 pre-existing skips, no failures/errors; executable JAR packaging passed. Complete frontend suite: 475 passed, zero failures; lint and production build passed. The backend test selection is the feature/lesson/Mentor/assessment/authorization regression set, not the entire backend suite. Existing deprecation warnings remain; frontend test harnesses reported Vite HMR port-sharing warnings without test failures. No browser visual/interaction verification, live Gemini evaluation, native MariaDB/MySQL migration, live storage connectivity or production memory test is claimed.

Before any deployment:

1. Review the diff independently of unrelated Student Registry/authentication work. Review the two-column migration and backup/recovery procedure separately.
2. Restore a verified backup to a **disposable** database matching the intended engine/version. Apply only this migration there, validate both nullable columns, and check that legacy records/content remain unchanged. Keep `ddl-auto=validate` for runtime.
3. Run the scoped workflow in a disposable application environment with synthetic identities and local/mocked storage/provider. Verify actual desktop/mobile UI, Admin Support/Preview authorization, upload→confirm→outline→supplement→generate→publish→Mentor, concurrent updates and recovery from parser timeouts.
4. Have the Professor inspect the actual OBTL Chapter 1 text/page preview and separate instructional supplements. Do not convert or republish the current lesson automatically.
5. Test packaged parsing on the target Linux/container runtime and measure process memory under bounded concurrent traffic. Review existing diagnostic logging settings for production.
6. Obtain separate migration/deployment approval. Preserve module, revision/history, Mentor messages and original files together in backups. Do not drop provenance columns or run old full-PDF code against scoped publications as a routine rollback: that would lose source isolation. Stop scoped operations and restore a compatible application/data snapshot under an approved recovery procedure.

## Multi-format table extraction (version v4)

`obtl-table-spans-v4` separates filled shading from actual rules, recognizes numeric Week cells through the curriculum header, and carries column semantics across pages only when the physical borders match. Aligned vertical endpoints recover open top rows. Horizontal merged cells retain complete text. A transition to a different table ends curriculum ownership before references or policies. Topic rows, examinations, orientation and integrative/comparative activities are exposed rather than discarded. Original wording is retained, including inconsistent knowledge/outcome cells; Professors must check column associations against the PDF. No AI or OCR participates in extraction.

Original local fixtures, kept outside Git and verified by checksums in `ObtlMultiFormatTest`, reproduce these results:

| Document | Previous detection | v4 detection |
| --- | --- | --- |
| International Cuisine | 6 chapter proposals plus 1 ambiguous page | 18 sections: 13 chapter groups, orientation, 2 examinations, comparative study and integrative practical activity |
| Kitchen Essentials | 2 unisolated-page warnings, no isolated weeks | 18 sections: Weeks 1–10 and 12–19; Week 11 is absent in the original |
| Art Appreciation | No detected sections; whole-document proposal | 7 sections: two distinct rows in Weeks 1–4, Weeks 5–8, Week 9 examination, Weeks 10–13, Weeks 14–17 and Week 18 examination |

International Cuisine's integrative activity spans pages 4–5; Kitchen Week 15 spans pages 5–6; Art's instructional ranges span pages 2–6. Continuations retain review warnings. Unsupported borderless curriculum tables and incompatible grids remain blocked; this is not a general PDF table/OCR solution. Shared merged cells may repeat their original text in the preview. Detection is not academic sufficiency or correctness verification.

Existing published v3 snapshots remain readable and are not rewritten. New generation, reconfirmation or publication of old drafts requires explicit **Extract / Refresh Sources**, selection review and reconfirmation. Refresh does not update published revisions. No schema migration is needed. OBTL Expansion can confirm an outline with `instructional: false`; Source-Grounded sufficiency, academic review and publication checks remain unchanged.

For browser acceptance, use a disposable draft, refresh each original PDF, inspect the exact previews and page continuations, choose the mode and select only the intended section. Confirm and reload; verify the mode/choices persist. Do not check the substantive-instruction box for a curriculum outline. Verify generation/review/publication separately under the existing provider approval policy. If production confirmation still fails, capture only its HTTP status, fixed error code, selected mode and extraction version—never tokens or the complete private source payload. Local tests do not establish Render success.

Local verification on November 20, 2026: the focused backend run discovered 104 tests: 103 passed, zero failures/errors, one existing optional `halo.test.uploads` test skipped. All three original PDF fixtures executed. The broader `MentorExternalTransactionTest` separately has 11 initialization errors from a missing `LessonSourceScope` test bean; these were reproduced with the unchanged committed extractor and left outside this extraction fix. The complete frontend suite passed 482 tests with sequential execution (the concurrent run stalled with Vite port conflicts). Frontend lint/build passed. Backend packaging passed in a configuration-free disposable copy. The executable-JAR worker processed all three originals within the unchanged 30-second/96-MiB limits: section counts 18/18/7, matching original checksums, exit code 0 and empty stderr. No database-backed application startup, live Gemini, Linux/Render execution or production confirmation success is claimed.

## PATHFIT examination-coverage regression (version v5)

The original `ASSOCIATES OBTL PATHFIT 1 - SONEE JAMES C. LAMORENA.pdf` reproduced one incorrect section, `Weeks 1–8 – Demonstrate learned Midterm Written`, spanning pages 4–7. Its curriculum table uses a merged **Essential Learning** header instead of Content Standards. Without recognizing that header the table reader returned no rows, and the prose-heading fallback interpreted Week 9's examination coverage as a heading extending through the rest of the document.

`obtl-table-spans-v5` recognizes this header only immediately after an explicitly identified Week column, with the existing ruled geometry checks. A `Week(s)` reference alone in another cell cannot establish a topic column. Unsupported ruled coverage regions fail closed rather than producing that prose fallback. The original coverage remains in Week 9's permitted text.

After extraction, the file contains 18 independently identified sections: Weeks 1–18, including orientation at Week 1, Midterm Examination at Week 9, and Final Examination at Week 18. Week 3 continues from page 2 to page 3. Instruction is on pages 2–5; references, policies and administrative pages are excluded. Week 17's Final Performance Preparation remains an instructional topic, not an examination. No grouped instructional range is present in this particular PDF; legitimate grouped ranges in the Art/Cuisine fixtures remain covered by regression tests.

The existing snapshot policy also applies to v5: published v3/v4 lessons retain their saved scope; old drafts must be explicitly refreshed and reconfirmed for new generation/publication. No migration or stored-record rewrite is required. The real-file test verifies the PDF checksum and asserts that exam content cannot contaminate other weeks, and a synthetic fixture checks that coverage cannot replace topic labels even when its cell begins with `Weeks 1–8`.

Verification: 106 focused backend tests discovered, 105 passed, one existing optional upload-fixture test skipped, zero failures/errors. This includes the original four PDFs, source confirmation, both generation modes, immutable revisions and scoped Mentor tests. Backend packaging passed. The packaged Windows worker returned v5, seven pages and 18 sections for PATHFIT with matching checksum, exit code 0 and empty stderr. No frontend code changed in this follow-up; the prior 482-test frontend run was not repeated. Render/Linux and live browser confirmation remain unverified.

## Manual recovery of unresolved curriculum tables

Extraction v6 can offer individual rows from an unresolved table. On a draft, use **Extract / Refresh Sources**, select a recovery candidate alone for that PDF, and inspect the outline on the original page. Select each proposed region, enter a display label, check the assembled excerpt, and confirm. The label is Professor metadata. Generation and Mentor indexing use the verified PDF text and its original heading.

The worker requires enclosing table rules and a source chapter/topic anchor or a schedule column aligned with the preceding curriculum table. A week reference inside assessment text cannot establish ownership. A continuation requires an explicit continued topic on an adjacent page with matching ownership and geometry. Pages without sufficient evidence remain blocked. For multi-page review, select every required region in the PDF page selector.

Region confirmations use snapshot format 3 in the existing JSON column. They reference extracted row evidence by section ID, PDF checksum, extraction version and exact page offsets. Formats 1 and 2 remain readable. Existing published snapshots are retained. Refresh and reconfirm an older **draft** before generating or publishing with v6. Coordinate frontend and backend releases before accepting format 3 confirmations; earlier readers that support only formats 1 and 2 cannot open them.

## Implementation file inventory

Backend additions: `LessonSourceController`, `LessonSourceScope`, `ObtlSections`, `ObtlPdfText`, `ObtlExtractionProcess`, `ObtlWorker`, and the review-only SQL migration.

Backend integration: `AiLearningModuleEntity`, `MentorMessageEntity`, `AiLearningModuleResponse`, `AiGenerationService`, `ModuleGenerationState`, `ProfessorModuleService`, `LessonRevisionService`, `ModuleMaterialIndex`, `MentorService`, `AssessmentService`, `ProfessorErrorResponses`.

Frontend: `LessonSourceSelection.jsx`, its CSS module, `LessonEditor.jsx`, `LessonRevision.jsx`, Professor service methods and fixed error messages.

Tests: `ObtlSectionsTest`, `ObtlPdfTextTest`, `LessonSourceScopeTest`, `LessonSourceHttpTest`, added cases in `LessonRevisionTest`, `ModuleScopedMentorTest`, `ProfessorModuleOwnershipTest`; scope dependency fixtures in `ModuleFileTransactionTest`, `ModuleGenerationConcurrencyTest`, `ProfessorInputValidationTest`, `ProfessorModuleHttpTest`, `QuizRevisionTest`; `lesson-source-selection.test.mjs` and `lesson-editor-route.test.mjs`.
