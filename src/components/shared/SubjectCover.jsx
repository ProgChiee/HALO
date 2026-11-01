import { useEffect, useState } from 'react';
import styles from './SubjectCover.module.css';

export const DEFAULT_SUBJECT_COVER = '/image/halo-subject-default.png';

// Never load arbitrary URLs from API data: reads go through the current authorized workflow.
export default function SubjectCover({ subjectId, load, revision = 0, previewUrl, className = '', onCustomChange }) {
  const [image, setImage] = useState(null);
  useEffect(() => {
    if (!load || !subjectId) return;
    const controller = new AbortController();
    let url;
    load(subjectId, { signal: controller.signal }).then(blob => {
      if (controller.signal.aborted) return;
      if (blob?.size > 0 && blob.type === 'image/png') {
        url = URL.createObjectURL(blob);
        setImage({ url, subjectId, load, revision });
        onCustomChange?.(true);
      } else onCustomChange?.(false);
    }).catch(() => { if (!controller.signal.aborted) onCustomChange?.(false); });
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url); };
  }, [subjectId, load, revision, onCustomChange]);
  const current = image?.subjectId === subjectId && image?.load === load && image?.revision === revision ? image.url : null;
  const custom = previewUrl || current;
  return <div className={`${styles.frame} ${className}`} data-subject-cover={custom ? 'custom' : 'default'}>
    <img src={custom || DEFAULT_SUBJECT_COVER} alt="" loading="lazy" className={custom ? styles.custom : styles.defaultCover}
      onError={() => { if (current) { setImage(null); onCustomChange?.(false); } }} />
  </div>;
}
