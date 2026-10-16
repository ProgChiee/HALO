import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from './useAuth';
import { ProfilePhotoContext } from './useProfilePhoto';
import { getOwnProfilePhoto } from '../../services/profilePhotoService';

export default function ProfilePhotoProvider({ children }) {
  const { user, token } = useAuth();
  return <AccountPhoto key={`${user?.email ?? user?.id ?? ''}:${token ?? ''}:${Boolean(user?.mustChangePassword)}`} enabled={Boolean(user && token && !user.mustChangePassword)}>{children}</AccountPhoto>;
}
function AccountPhoto({ children, enabled }) {
  const [photo, setPhoto] = useState(null);
  const [error, setError] = useState('');
  const updated = useRef(false);
  const updatePhoto = useCallback(blob => { updated.current = true; setPhoto(blob); setError(''); }, []);
  useEffect(() => {
    if (!enabled) return;
    const request = new AbortController();
    getOwnProfilePhoto({ signal: request.signal }).then(blob => {
      if (!request.signal.aborted && !updated.current && blob instanceof Blob && blob.size) setPhoto(blob);
    }).catch(() => {
      if (!request.signal.aborted && !updated.current) setError('Could not load your photo. Please try again later.');
    });
    return () => request.abort();
  }, [enabled]);
  const value = useMemo(() => ({ photo, updatePhoto, error }), [photo, updatePhoto, error]);
  return <ProfilePhotoContext.Provider value={value}>{children}</ProfilePhotoContext.Provider>;
}
