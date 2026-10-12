# Shared profile photos
No database migration. Private PNG files live in `halo.profile-photo-directory`
(default `uploads/student-photos`). Configure a durable writable directory outside
any static/web root; share it across backend replicas and include it in backups.
Files use authenticated numeric account IDs, never user filenames or client IDs.
Replacement uses an atomic filesystem move; the old photo survives validation or
write failure. Do not expose this directory through a static server.
JPG/PNG/WEBP input up to 5 MB is browser-decoded/resized to <=512px and sent as PNG.
The server independently enforces a 2 MB payload/1024px bound, decodes PNG/JPEG,
and writes fresh PNG data (no original metadata). Raw WEBP transport is unnecessary.
GET /api/profile/photo and PUT /api/profile/photo use only the authenticated
active account ID for all four roles. Existing Student endpoints remain compatible.
Admin Professor and Student Preview/Support GET validates its acting session.
No acting photo-write endpoints exist; own-photo PUT also rejects X-Acting-Session.
The historical uploads/student-photos default and all existing files are retained.
No relocation, DB migration or configuration change is needed.
