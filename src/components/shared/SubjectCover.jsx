import { useContext, useEffect, useRef, useState } from 'react';
import { AuthContext } from '../../context/login/useAuth';
import styles from './SubjectCover.module.css';

export const DEFAULT_SUBJECT_COVER = '/image/halo-subject-default.jpg';

// Share only outstanding requests. Settled data lives in mounted components and
// the browser's bounded private HTTP cache, which must revalidate before reuse.
const pending = new Set();
function acquire(load, subjectId, identity, revision) {
  let entry = [...pending].find(e => e.load === load && e.subjectId === subjectId &&
    e.identity === identity && e.revision === revision);
  if (!entry) {
    const controller = new AbortController();
    entry = { load, subjectId, identity, revision, controller, consumers: 0 };
    entry.promise = load(subjectId, { signal: controller.signal }).finally(() => pending.delete(entry));
    pending.add(entry);
  }
  entry.consumers++;
  return { promise: entry.promise, release() {
    if (--entry.consumers === 0) {
      pending.delete(entry);
      entry.controller.abort();
    }
  } };
}

export default function SubjectCover({ subjectId, load, revision = 0, previewUrl, className = '', onCustomChange }) {
  const frame = useRef(null);
  const identity = useContext(AuthContext)?.token;
  const [visible, setVisible] = useState(() => typeof window !== 'undefined' && !window.IntersectionObserver);
  const [generation, setGeneration] = useState(0);
  const [image, setImage] = useState(null);
  useEffect(() => {
    if (!window.IntersectionObserver) return;
    const observer = new window.IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        setVisible(true);
        observer.disconnect();
      }
    });
    observer.observe(frame.current);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    function changed(event) {
      if (subjectId === event.detail) setGeneration(value => value + 1);
    }
    window.addEventListener('halo:subject-cover-changed', changed);
    return () => window.removeEventListener('halo:subject-cover-changed', changed);
  }, [subjectId]);
  useEffect(() => {
    if (!visible || !load || !subjectId || previewUrl) return;
    let active = true, url;
    const context = { subjectId, load, revision, generation, identity };
    const request = acquire(load, subjectId, identity, revision + ':' + generation);
    request.promise.then(blob => {
      if (!active) return;
      if (blob != null) {
        if (!blob.size || !['image/png','image/jpeg'].includes(blob.type)) throw Error('Invalid cover');
        url = URL.createObjectURL(blob);
      }
      setImage({ ...context, url, status: url ? 'custom' : 'missing' });
      onCustomChange?.(Boolean(url));
    }).catch(() => {
      if (!active) return;
      setImage({ ...context, status: 'failed' });
      onCustomChange?.(false);
    });
    return () => { active = false; request.release(); if (url) URL.revokeObjectURL(url); };
  }, [visible, load, subjectId, revision, generation, identity, previewUrl, onCustomChange]);
  const current = image && image.subjectId === subjectId && image.load === load && image.revision === revision &&
    image.identity === identity && image.generation === generation ? image : null;
  const url = previewUrl || current?.url;
  const status = url ? 'custom' : current?.status || (load ? 'loading' : 'missing');
  return <div ref={frame} className={styles.frame + ' ' + className} data-subject-cover={url ? 'custom' : 'default'} data-status={status}>
    {status !== 'loading' && <img src={url || DEFAULT_SUBJECT_COVER} alt="" decoding="async"
      className={url ? styles.custom : styles.defaultCover}
      onError={() => { if (current?.url) { setImage({ ...current, url: null, status: 'failed' }); onCustomChange?.(false); } }} />}
  </div>;
}
