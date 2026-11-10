export function speechRecognitionConstructor(browser) {
  return browser?.SpeechRecognition || browser?.webkitSpeechRecognition;
}

const errors = {
  'not-allowed': 'Microphone permission was denied. Allow microphone access in your browser to try again.',
  'service-not-allowed': 'The speech recognition service is unavailable or blocked by your browser.',
  'audio-capture': 'No usable microphone was found. Check your microphone connection and settings.',
  'no-speech': 'No speech was detected. Please try again.',
  network: 'Voice input could not connect to the recognition service. Please try again or type your message.',
  'language-not-supported': 'Your browser does not support the selected speech language. Choose another language or type your message.',
};

// Owns one recognition session. It has no API client, audio storage, or send callback.
export function createVoiceInput({ Recognition, onState, onFinal, onInterim, onError }) {
  let current = null;
  let disposed = false;
  function release(session, abort = false) {
    if (current !== session) return;
    current = null;
    clearTimeout(session.timer);
    const recognition = session.recognition;
    for (const event of ['onstart', 'onresult', 'onerror', 'onend', 'onaudioend']) recognition[event] = null;
    if (abort) { try { recognition.abort(); } catch { /* Already ended. */ } }
  }
  function cancel() {
    if (!current) return;
    release(current, true);
    onInterim('');
    onState('idle');
  }
  function stop() {
    const session = current;
    if (!session || session.stopping) return;
    session.stopping = true;
    onState('processing');
    clearTimeout(session.timer);
    session.timer = setTimeout(() => {
      if (current !== session) return;
      cancel();
      onError('Voice input took too long to finish. Your completed text is still editable. Please try again.');
    }, 8000);
    try { session.recognition.stop(); } catch { cancel(); }
  }
  return {
    start(language) {
      if (disposed || current || !Recognition) return;
      onError('');
      onInterim('');
      let session;
      try {
        const recognition = new Recognition();
        session = { recognition, finalIndexes: new Set(), stopping: false, timer: null };
        current = session;
        recognition.lang = language;
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.maxAlternatives = 1;
        const active = () => !disposed && current === session;
        recognition.onstart = () => { if (active() && !session.stopping) onState('listening'); };
        recognition.onresult = event => {
          if (!active()) return;
          const finals = [], interim = [];
          for (let i = 0; i < event.results.length; i++) {
            const result = event.results[i];
            const text = result[0]?.transcript?.trim();
            if (!text) continue;
            if (result.isFinal) {
              if (!session.finalIndexes.has(i)) { session.finalIndexes.add(i); finals.push(text); }
            } else interim.push(text);
          }
          if (finals.length) onFinal(finals.join(' '));
          onInterim(interim.join(' '));
        };
        recognition.onaudioend = () => { if (active()) stop(); };
        recognition.onerror = event => {
          if (!active()) return;
          cancel();
          if (event.error !== 'aborted') onError(errors[event.error] || 'Voice input is unavailable. Please try again or type your message.');
        };
        recognition.onend = () => {
          if (!active()) return;
          release(session);
          onInterim('');
          onState('idle');
          if (!session.finalIndexes.size) onError(errors['no-speech']);
        };
        onState('starting');
        session.timer = setTimeout(stop, 60000);
        recognition.start();
      } catch (error) {
        if (session) release(session, true);
        onState('idle');
        onError(error.name === 'NotAllowedError' ? errors['not-allowed'] : 'Voice input could not start. Please try again or type your message.');
      }
    },
    stop,
    cancel,
    dispose() { disposed = true; if (current) release(current, true); },
  };
}

export function appendTranscript(draft, transcript) {
  return draft + (draft && !/\s$/.test(draft) ? ' ' : '') + transcript;
}
