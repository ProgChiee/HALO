export function youtubeVideoId(value) {
 if(typeof value !== 'string' || !value.trim()) return null;
 const text=value.trim();
 if(text.length>255 || /[\s\\]/.test(text))return null;
 try {
  const url=new URL(text);
  if(!['https:','http:'].includes(url.protocol) || url.username || url.password || url.port)return null;
  let id=null;
  if(url.hostname==='youtu.be' && /^\/[A-Za-z0-9_-]{11}$/.test(url.pathname))id=url.pathname.slice(1);
  else if(['youtube.com','www.youtube.com','m.youtube.com'].includes(url.hostname)){
   if(/^\/shorts\/[A-Za-z0-9_-]{11}$/.test(url.pathname))id=url.pathname.slice(8);
   else if(url.pathname==='/watch' && url.searchParams.getAll('v').length===1)id=url.searchParams.get('v');
  }
  return id && /^[A-Za-z0-9_-]{11}$/.test(id)?id:null;
 }catch{return null;}
}
