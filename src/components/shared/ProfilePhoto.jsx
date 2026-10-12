import { useEffect, useRef, useState, useId } from 'react';
import { getOwnProfilePhoto, saveOwnProfilePhoto } from '../../services/profilePhotoService';
import { prepareProfilePhoto } from '../../utils/profilePhoto';
import styles from './ProfilePhoto.module.css';

export default function ProfilePhoto({ identity, ...props }) {
  return <Photo key={identity} {...props} />;
}
function Photo({ initial, readOnly = false, getProfilePhoto = getOwnProfilePhoto }) {
  const inputId = useId();
  const helpId = useId();
  const [saved, setSaved] = useState(null), [draft, setDraft] = useState(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const controller = useRef(null), locked = useRef(false), edited = useRef(false);
  useEffect(() => {
    const request = new AbortController(); controller.current = request;
    getProfilePhoto({ signal: request.signal }).then(data => {
      if (!request.signal.aborted && !edited.current && data instanceof Blob && data.size) setSaved(data);
    }).catch(() => { if (!request.signal.aborted) setError('Could not load your photo. Please try again later.'); });
    return () => request.abort();
  }, [getProfilePhoto]);
  const imageRef = useRef(null);
  useEffect(() => {
    const blob = draft ?? saved;
    if (!blob) return;
    const next = URL.createObjectURL(blob); imageRef.current.src = next;
    return () => URL.revokeObjectURL(next);
  }, [draft, saved]);
  async function choose(event) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (readOnly || !file || locked.current) return;
    edited.current = true; locked.current = true; setBusy(true); setError('');
    try { const photo = await prepareProfilePhoto(file); if (!controller.current.signal.aborted) setDraft(photo); }
    catch (failure) { if (!controller.current.signal.aborted) setError(failure.message); }
    finally { locked.current = false; if (!controller.current.signal.aborted) setBusy(false); }
  }
  async function save() {
    if (readOnly || !draft || locked.current) return;
    locked.current = true; setBusy(true); setError('');
    const data = new FormData(); data.append('photo', draft, 'profile.png');
    try {
      await saveOwnProfilePhoto(data, { signal: controller.current.signal });
      if (!controller.current.signal.aborted) { setSaved(draft); setDraft(null); }
    } catch { if (!controller.current.signal.aborted) setError('Could not save your photo. Please try again.'); }
    finally { locked.current = false; if (!controller.current.signal.aborted) setBusy(false); }
  }
  return <div className={styles.photoArea}>
    <div className={styles.avatar}>{draft || saved ? <img ref={imageRef} alt={draft ? 'New profile photo preview' : 'Profile photo'} /> : initial}</div>
    {!readOnly && <><label className={styles.photoLabel} htmlFor={inputId}>Change photo</label><input id={inputId} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy} aria-describedby={helpId} />
      <p id={helpId} className={styles.photoHelp}>JPG, PNG or WEBP. Up to 5 MB.</p>
      {draft && <div className={styles.photoActions}><button disabled={busy} onClick={save}>Save photo</button><button disabled={busy} onClick={() => setDraft(null)}>Cancel</button></div>}
      {busy && <p role="status">Preparing / saving photo...</p>}{error && <p role="alert">{error}</p>}</>}
  </div>;
}
