import { createContext, useContext } from 'react';
export const ProfilePhotoContext = createContext(null);
export const useProfilePhoto = () => useContext(ProfilePhotoContext);
