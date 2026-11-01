import { useEffect, useId, useRef, useState } from 'react';
import Dialog from '../../../components/shared/Dialog';
import styles from '../styles/LessonAttachments.module.css';

const types = new Set(['application/pdf', 'image/png', 'image/jpeg']);
export default function LessonAttachments({ files = [], load }) {
  const titleId = useId();
  const [preview, setPreview] = useState(null), [pending, setPending] = useState(null), [error, setError] = useState('');
  const active = useRef(null), downloads = useRef(new Set());
  useEffect(() => () => {
    active.current?.abort();
    for (const url of downloads.current) URL.revokeObjectURL(url);
    downloads.current.clear();
  }, []);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url); }, [preview]);
  async function open(file, download) {
    if (active.current) return;
    const controller = new AbortController(); active.current = controller;
    setPending(file.id); setError('');
    try {
      const blob = await load(file.id, { signal: controller.signal, params: { download } });
      if (controller.signal.aborted) return;
      const type = blob?.type?.split(';')[0];
      if (!types.has(type) || !blob.size || blob.size > 3 * 1024 * 1024) throw new Error('Invalid material response');
      const url = URL.createObjectURL(blob);
      if (download) {
        downloads.current.add(url);
        const link = document.createElement('a'); link.href = url;
        link.download = Array.from(file.originalFileName || 'learning-material', char =>
          char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127 || char === '/' || char === '\\' ? '_' : char).join('');
        document.body.append(link); link.click(); link.remove();
        setTimeout(() => { URL.revokeObjectURL(url); downloads.current.delete(url); }, 1000);
      } else setPreview({ ...file, type, url });
    } catch (failure) {
      if (!controller.signal.aborted) setError([401, 403, 409].includes(failure.response?.status)
        ? 'This learning material is not currently available to your account.'
        : failure.response?.status === 404 ? 'This learning material could not be found.'
          : 'Could not open this learning material. Please try again.');
    } finally {
      if (active.current === controller) active.current = null;
      if (!controller.signal.aborted) setPending(null);
    }
  }
  if (!files.length) return null;
  return <section className={styles.materials} aria-label="Lesson attachments">
    <h3>Learning materials</h3>
    <ul>{files.map(file => <li key={file.id}>
      <span className={styles.name}>{file.originalFileName}</span>
      <div className={styles.actions}>
        <button type="button" disabled={pending !== null || !load} onClick={() => open(file, false)} aria-label={`Preview ${file.originalFileName}`}>Preview</button>
        <button type="button" disabled={pending !== null || !load} onClick={() => open(file, true)} aria-label={`Download ${file.originalFileName}`}>Download</button>
      </div>
    </li>)}</ul>
    {pending !== null && <p role="status">Loading learning material...</p>}
    {error && <p role="alert">{error}</p>}
    {preview && <Dialog labelledBy={titleId} onClose={() => setPreview(null)} closeOnEscape className={styles.dialog} initialFocus="button">
      <div className={styles.heading}><h2 id={titleId}>{preview.originalFileName}</h2><button type="button" onClick={() => setPreview(null)}>Close preview</button></div>
      {preview.type === 'application/pdf'
        ? <><p>If your browser cannot display this PDF, close the preview and choose Download.</p><iframe src={preview.url} title={`PDF preview: ${preview.originalFileName}`} className={styles.pdf} /></>
        : <img src={preview.url} alt={preview.originalFileName} className={styles.image} />}
    </Dialog>}
  </section>;
}
