// Normalize supported browser-decoded photos before sending; the server validates again.
export async function prepareProfilePhoto(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file?.type)) throw new Error('Choose a JPG, PNG, or WEBP image.');
  if (file.size > 5 * 1024 * 1024) throw new Error('Choose an image smaller than 5 MB.');
  const bitmap = await createImageBitmap(file).catch(() => { throw new Error('This image could not be read. Please choose another photo.'); });
  try {
    const scale = Math.min(1, 512 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('This image could not be prepared.')), 'image/png'));
  } finally { bitmap.close(); }
}
