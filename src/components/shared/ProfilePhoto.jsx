import { Camera } from 'lucide-react';
import { useProfilePhoto } from '../../context/login/useProfilePhoto';
import { useEffect, useRef, useState, useId } from 'react';
import { getOwnProfilePhoto, saveOwnProfilePhoto } from '../../services/profilePhotoService';
import { prepareProfilePhoto } from '../../utils/profilePhoto';
import styles from './ProfilePhoto.module.css';

export default function ProfilePhoto({ identity, ...props }) {
  return <Photo key={identity} {...props} />;
}
function Photo({ initial, readOnly = false, getProfilePhoto = getOwnProfilePhoto, identityContent }) {
  const sharedPhoto = useProfilePhoto();
  const ownPhoto = !readOnly ? sharedPhoto : null;
  const hasSharedPhoto = Boolean(ownPhoto);
  const fileInput = useRef(null);
  const inputId = useId();
  const helpId = useId();
  const [localSaved, setLocalSaved] = useState(null), [draft, setDraft] = useState(null);
  const saved = ownPhoto ? ownPhoto.photo : localSaved;
  const setSaved = ownPhoto ? ownPhoto.updatePhoto : setLocalSaved;
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const controller = useRef(null), locked = useRef(false), edited = useRef(false);
  useEffect(() => {
    const request = new AbortController(); controller.current = request;
    if (hasSharedPhoto) return () => request.abort();
    getProfilePhoto({ signal: request.signal }).then(data => {
      if (!request.signal.aborted && !edited.current && data instanceof Blob && data.size) setSaved(data);
    }).catch(() => { if (!request.signal.aborted) setError('Could not load your photo. Please try again later.'); });
    return () => request.abort();
  }, [getProfilePhoto, hasSharedPhoto, setSaved]);
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
  const image = draft || saved ? <img ref={imageRef} onError={() => { setDraft(null); setSaved(null); }} alt={draft ? 'New profile photo preview' : 'Profile photo'} /> : initial;
  const avatar = readOnly ? <div className={styles.avatar}>{image}</div> : (
    <button type="button" className={`${styles.avatar} ${styles.avatarUpload}`} aria-label="Change photo" aria-controls={inputId} aria-describedby={helpId} disabled={busy} onClick={() => fileInput.current?.click()}>
      {image}
      <span className={styles.hoverOverlay} aria-hidden="true">Change photo</span>
      <span className={styles.cameraBadge} aria-hidden="true"><Camera size={18} /></span>
    </button>
  );
  return <div className={identityContent ? `${styles.photoArea} ${styles.withIdentity}` : styles.photoArea}>
    {identityContent ? <div className={styles.identityRow}>{avatar}{identityContent}</div> : avatar}
    {!readOnly && <><label hidden htmlFor={inputId}>Change photo</label><input ref={fileInput} hidden id={inputId} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy} aria-describedby={helpId} />
      <p id={helpId} className={styles.photoHelp}>JPG, PNG or WEBP. Up to 5 MB.</p>
      {draft && <div className={styles.photoActions}><button disabled={busy} onClick={save}>Save photo</button><button disabled={busy} onClick={() => setDraft(null)}>Cancel</button></div>}
      {busy && <p role="status">Preparing / saving photo...</p>}{(error || ownPhoto?.error) && <p role="alert">{error || ownPhoto.error}</p>}</>}
  </div>;
}


