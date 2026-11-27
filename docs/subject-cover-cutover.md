# Subject cover cutover

## Preserve the running service before any deployment

1. In Render, identify every running backend instance, its working directory, the configured Spring property `halo.subject-cover-directory`, and any persistent disk mount. The image working directory is `/app`. The default cover directory resolves to `/app/uploads/subject-covers`. Check service-specific configuration without exporting the environment. Compare the resolved directory to the disk mount. Adding a disk or saving environment changes can restart the service.
2. Coordinate a pause on cover uploads, removals, and permanent subject deletions. Keep the existing processes running. For each instance, run the following read-only inventory in the **verified cover directory**. Save the output privately outside Render, labeled with service, instance, directory and collection time.

   ```sh
   pwd -P
   find . -maxdepth 1 -type f -regextype posix-extended -regex './[1-9][0-9]*\.png' -exec sha256sum {} + | sort
   ```

3. Copy those files over authenticated SSH/SCP to a separate restricted, encrypted operator workstation directory per instance. In each copy, run `sha256sum -c manifest.sha256` using that instance's manifest. Count files, validate PNG signatures and decode each image locally. Resolve conflicting hashes for the same subject ID with its owner. Match IDs against the current production subject inventory. Never substitute development files merely because their IDs match. Stop on an unexplained empty directory, missing instance, invalid file, or checksum mismatch. A missing ephemeral file needs an independent backup or the owner's original.
4. Retain the verified originals and manifests. Re-inventory the running source immediately before switching. If writes cannot be paused, reconcile replacements and removals before any restart. Keep preservation copies outside Git.

## Stage and verify after approval

1. In the existing Supabase project create a dedicated **private** bucket named `halo-subject-covers`. Allow image/png and image/jpeg. Configure the object size limit to accommodate the largest verified legacy object. The application's read ceiling is 12 MiB. Investigate any larger file before cutover. Check available storage and egress quota. Keep public access off, with no anonymous or authenticated-client read/write policies for this bucket.
2. Under the operator's private credentials, stage each approved PNG byte-for-byte as a root object named with its decimal subject ID, without an extension. Set Content-Type to image/png. Use create-only uploads with upsert disabled. If a key exists, download and compare; stop on any difference. Keep lesson-material objects and bucket unchanged.
3. Authenticated-download every staged object to the protected workstation, compare SHA-256 and byte count to the instance inventory, and verify its MIME type and raster decoding. Reconcile missing, extra and conflicting IDs. Verify anonymous access is denied. A checksum on the upload source alone is insufficient.
4. Verify in staging that the approved build can authorize reads, render a migrated PNG, replace it with a JPEG, return a fresh ETag, and remove it. Exercise logout/login, an enrolled archived subject, an unauthorized subject, and acting/preview sessions. Verify storage survives a staging instance replacement. Stop on any discrepancy.

## Deploy and rollback after separate approval

1. Review the exact change set. Exclude unrelated working-tree changes. Coordinate manual deployments and disable unintended auto-deploy triggers during the change window.
2. Deploy the reviewed Vercel frontend first with its existing build settings. It accepts both legacy PNGs and new JPEGs. No frontend environment change is required.
3. On Render retain the existing server-only `SUPABASE_URL` and `SUPABASE_SECRET_KEY`. Set `HALO_SUBJECT_COVER_PROVIDER=supabase` for the reviewed backend release. Coordinate saving configuration with that release. Keep lesson storage configuration unchanged. Use the existing Docker build and start configuration.
4. With cover writes still paused, smoke-test representative migrated covers using authorized Professor and Student sessions. Check private response headers, conditional GET, missing-image handling, and denied reads. Confirm that other academic screens load. Release the write pause only after successful verification.
5. For rollback before new writes, restore the verified files to a durable local provider directory and switch the reviewed backend to `HALO_SUBJECT_COVER_PROVIDER=local` with `halo.subject-cover-directory` pointing there. Verify the directory and mount before restarting. An ephemeral disk is unsuitable as a durable fallback.
6. After new writes, first pause writes again and export the complete current private bucket into a **new** protected directory. Reconcile its authoritative inventory, including removals. For the reviewed local provider, store the exact exported bytes under the legacy lookup name `<id>.png`; it serves the actual encoded MIME type. Verify checksums before switching. If rolling back to older PNG-only software, transcode JPEGs to PNG in a separate directory and verify decoded dimensions and pixels. Keep both originals and new exports. Avoid overlaying the initial backup onto the current inventory since that would resurrect removals. If a verified fallback is unavailable, keep writes paused and repair forward.
