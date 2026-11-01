import { useEffect, useId, useRef, useState } from 'react';
import { ImageUp } from 'lucide-react';
import Dialog from '../../../components/shared/Dialog';
import SubjectCover from '../../../components/shared/SubjectCover';
import styles from '../styles/SubjectCoverDialog.module.css';

export default function SubjectCoverDialog({ subject, api, onClose, onSaved }) {
  const titleId = useId();
  const inputId = useId(), hintId = useId(), errorId = useId();
  const [file, setFile] = useState(null), [preview, setPreview] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [hasCustom, setHasCustom] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const lock = useRef(false), alive = useRef(false), input = useRef(null), dragDepth = useRef(0);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url); }, [preview]);
  function choose(event) {
    const selected = event.target.files?.[0];
    event.target.value = '';
    selectFile(selected);
  }
  function selectFile(selected) {
    if (lock.current) return;
    setError('');
    if (!selected) return;
    if (!selected.size) { setError('This file is empty. Choose a JPEG, PNG or WebP image.'); return; }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(selected.type) || selected.size > 3 * 1024 * 1024) {
      setError('Choose a JPEG, PNG or WebP image up to 3 MB.'); return;
    }
    setFile(selected);
    setPreview({ file: selected, url: URL.createObjectURL(selected) });
  }
  function clearSelection() {
    if (lock.current) return;
    setFile(null); setPreview(null); setError('');
  }
  function drop(event) {
    event.preventDefault(); dragDepth.current = 0; setDragging(false);
    if (lock.current) return;
    const files = event.dataTransfer.files;
    if (files.length > 1) { setError('Choose one cover image at a time.'); return; }
    selectFile(files[0]);
  }
  async function save(remove = false) {
    if (lock.current || (!remove && !file)) return;
    lock.current = true; setBusy(true); setError('');
    try {
      if (remove) await api.removeSubjectCover(subject.id);
      else await api.saveSubjectCover(subject.id, file);
      if (alive.current) onSaved();
    } catch (failure) {
      if (alive.current) setError(failure.response?.status === 400 ? 'Choose a valid JPEG, PNG or WebP up to 3 MB and 16 megapixels.' : 'Could not confirm the cover update. Please retry or refresh to check the current cover.');
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  return <Dialog labelledBy={titleId} onClose={onClose} busy={busy} className={styles.dialog} initialFocus="button">
    <h2 id={titleId}>Subject cover</h2><p className={styles.subjectName}>{subject.subjectName}</p>
    <div className={`${styles.upload} ${dragging ? styles.dragging : ''}`} role="group" aria-label="Subject cover uploader" aria-disabled={busy || undefined}
      onDragEnter={event => { event.preventDefault(); if (!lock.current) { dragDepth.current++; setDragging(true); } }}
      onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = busy ? 'none' : 'copy'; }}
      onDragLeave={event => { event.preventDefault(); dragDepth.current = Math.max(0, dragDepth.current - 1); if (!dragDepth.current) setDragging(false); }}
      onDrop={drop}>
      <div className={styles.uploadHeading}><ImageUp size={22} aria-hidden="true" /><h3>Upload a subject cover</h3><span className={styles.optional}>Optional</span></div>
      <p className={styles.hint}>Drag and drop an image here, or choose a file</p>
      <div className={styles.preview}>
        <div hidden={!!file}><SubjectCover subjectId={subject.id} load={api.getSubjectCover} onCustomChange={setHasCustom} /></div>
        {file && <img src={preview?.url} alt="Selected subject cover preview" onError={() => { clearSelection(); setError('This image could not be opened. Choose a valid JPEG, PNG or WebP image.'); }} />}
      </div>
      <p className={styles.selection} role="status">{file ? <><strong>New image selected</strong><span className={styles.filename}>{file.name}</span><span>Save cover to apply this change.</span></> : hasCustom ? 'Saved cover' : 'Default HALO watermark'}</p>
      <label htmlFor={inputId} className={styles.visuallyHidden}>Subject cover image</label>
      <input ref={input} id={inputId} className={styles.visuallyHidden} tabIndex={-1} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={choose} aria-describedby={`${hintId}${error ? ` ${errorId}` : ''}`} aria-invalid={!!error} />
      <div className={styles.imageActions}>
        <button type="button" disabled={busy} onClick={() => input.current?.click()}>{file || hasCustom ? 'Change Image' : 'Choose Image'}</button>
        {(file || hasCustom) && <button type="button" disabled={busy} onClick={() => file ? clearSelection() : save(true)}>Remove Image</button>}
      </div>
      <p id={hintId} className={styles.hint}>JPG, PNG or WebP · Maximum 3 MB · Up to 16 megapixels</p>
    </div>
    {error && <p id={errorId} role="alert">{error}</p>}
    <div className={styles.actions}>
      <button type="button" disabled={busy} onClick={onClose}>Cancel</button>
      <button type="button" className={styles.primary} disabled={busy || !file} onClick={() => save()}>{busy ? 'Saving...' : 'Save cover'}</button>
    </div>
  </Dialog>;
}
