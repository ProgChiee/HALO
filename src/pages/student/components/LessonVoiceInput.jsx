import { useEffect, useRef, useState } from 'react';
import { Info, Mic, Square } from 'lucide-react';
import { createVoiceInput, speechRecognitionConstructor } from '../../../utils/voiceInput';
import styles from '../styles/LessonChat.module.css';

export default function LessonVoiceInput({ disabled, onFinal, onBusyChange, onStart }) {
  const [language, setLanguage] = useState('en-US');
  const [state, setState] = useState('idle');
  const [interim, setInterim] = useState('');
  const [error, setError] = useState('');
  const controller = useRef(null);
  const Recognition = speechRecognitionConstructor(window);
  const busy = state !== 'idle';

  useEffect(() => {
    const voice = createVoiceInput({ Recognition, onFinal, onInterim: setInterim, onError: setError,
      onState: next => { setState(next); onBusyChange(next !== 'idle'); },
    });
    controller.current = voice;
    const leave = () => voice.cancel();
    const visibility = () => { if (document.hidden) leave(); };
    window.addEventListener('pagehide', leave);
    document.addEventListener('visibilitychange', visibility);
    return () => {
      voice.dispose();
      window.removeEventListener('pagehide', leave);
      document.removeEventListener('visibilitychange', visibility);
    };
  }, [Recognition, onFinal, onBusyChange]);

  useEffect(() => { if (disabled) controller.current?.cancel(); }, [disabled]);

  return <>
    <button type="button" className={`${styles.voiceButton} ${busy ? styles.voiceActive : ''}`}
      aria-label={busy ? 'Stop voice input' : 'Use voice input'} title={busy ? 'Stop voice input' : 'Use voice input'}
      aria-pressed={busy} aria-describedby="voice-input-help"
      disabled={disabled || !Recognition || state === 'processing'}
      onClick={() => { if (busy) controller.current?.stop(); else { onStart(); controller.current?.start(language); } }}>
      {busy ? <Square size={18} aria-hidden="true" /> : <Mic size={18} aria-hidden="true" />}
    </button>
    <div className={styles.voiceDetails}>
      {Recognition && <label className={styles.voiceLanguage}>Voice language
        <select aria-label="Voice language" value={language} disabled={disabled || busy} onChange={event => setLanguage(event.target.value)}>
          <option value="en-US">English</option><option value="fil-PH">Filipino</option>
        </select>
      </label>}
      <span id="voice-input-help" className={styles.voiceHelp}>{Recognition ? 'Review your transcription before sending.' : 'Voice input is not supported in this browser. You can still type your message.'}</span>
      {Recognition && <span className={styles.voicePrivacy}>
        <button type="button" aria-label="Voice input privacy information" aria-describedby="voice-privacy-help"><Info size={14} aria-hidden="true" /></button>
        <span id="voice-privacy-help" role="tooltip">Voice input may use your browser’s speech service. HALO does not store or upload microphone audio.</span>
      </span>}
      <span role="status" className={styles.voiceStatus}>{state === 'starting' ? 'Starting microphone...' : state === 'listening' ? 'Listening...' : state === 'processing' ? 'Transcribing...' : ''}</span>
      {interim && <span className={styles.voiceInterim}>Heard so far: {interim}</span>}
      {error && <span role="alert" className={styles.voiceError}>{error}</span>}
    </div>
  </>;
}
