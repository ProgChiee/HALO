import { useId, useState } from 'react';
import Dialog from './Dialog';
import Button from './Button';
import { youtubeVideoId } from '../../utils/youtubeVideo';
import styles from './VideoLearning.module.css';

export function VideoPreview({ url }) {
 const id=youtubeVideoId(url);
 if(!id)return null;
 return <figure className={styles.preview}><figcaption>Video Preview</figcaption><img src={`https://i.ytimg.com/vi/${id}/hqdefault.jpg`} alt="YouTube video preview" loading="lazy" /></figure>;
}

export default function VideoLearning({ url }) {
 const id=youtubeVideoId(url);
 // Changing the current video unmounts any previously open player.
 return id ? <VideoControl key={id} videoId={id} /> : null;
}
function VideoControl({ videoId }) {
 const [open,setOpen]=useState(false);
 const titleId=useId();
 return <>
  <Button onClick={()=>setOpen(true)}>Video Learning</Button>
  {open && <Dialog labelledBy={titleId} onClose={()=>setOpen(false)} className={styles.modal} initialFocus="button">
   <div className={styles.header}><h2 id={titleId}>Video Learning</h2><Button onClick={()=>setOpen(false)}>Close</Button></div>
   <iframe className={styles.player} src={`https://www.youtube.com/embed/${videoId}`} title="Current lesson YouTube video" allow="encrypted-media; picture-in-picture; fullscreen" allowFullScreen referrerPolicy="strict-origin-when-cross-origin" tabIndex={0} />
   <p className={styles.help}>Video unavailable? Please contact your Professor or open the source link on YouTube. Private, deleted, or restricted videos may not play here.</p>
   <a href={`https://www.youtube.com/watch?v=${videoId}`} target="_blank" rel="noopener noreferrer">Open on YouTube</a>
  </Dialog>}
 </>;
}
