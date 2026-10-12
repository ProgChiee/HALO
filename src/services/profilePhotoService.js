import apiClient from './apiClient';
export async function getOwnProfilePhoto(config = {}) {
  return (await apiClient.get('/profile/photo', { ...config, responseType: 'blob' })).data;
}
export async function saveOwnProfilePhoto(data, config = {}) {
  return apiClient.put('/profile/photo', data, config);
}
