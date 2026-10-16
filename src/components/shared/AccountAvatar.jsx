import { useEffect, useRef } from 'react';
import { useProfilePhoto } from '../../context/login/useProfilePhoto';

export default function AccountAvatar({ initial }) {
  const shared = useProfilePhoto();
  const image = useRef(null);
  const photo = shared?.photo;
  useEffect(() => {
    if (!photo) return;
    const url = URL.createObjectURL(photo);
    image.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [photo]);
  return photo ? <img ref={image} alt="Your profile photo" onError={() => shared.updatePhoto(null)} /> : initial;
}
