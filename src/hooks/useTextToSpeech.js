import { useState, useCallback, useEffect, useRef } from 'react';

// Text-to-speech using the browser's built-in Web Speech API
// (window.speechSynthesis) — no npm package or backend needed.
//
// Only one message can "speak" at a time: calling speak() while another
// message is already speaking cancels it first. Calling speak() again
// with the SAME id stops it (acts as a toggle for the Play/Stop button).
//
// Browser support: works in Chrome, Edge, Safari, and most modern
// mobile browsers. `isSupported` lets the UI hide the button entirely
// on browsers that don't have it, instead of showing a button that does
// nothing.
export function useTextToSpeech() {
  const [speakingId, setSpeakingId] = useState(null);
  const isSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;

  // Tracks which id is the CURRENT/latest speak() call. Needed because
  // cancelling an utterance fires its onend/onerror asynchronously — if
  // the user rapidly switches from message A to message B, A's "stopped"
  // event can arrive AFTER B has already started, and would otherwise
  // incorrectly reset speakingId to null while B is still playing.
  const currentIdRef = useRef(null);

  // Stop any speech in progress when the component using this hook
  // unmounts (e.g. student navigates to a different lesson/page).
  useEffect(() => {
    return () => {
      if (isSupported) window.speechSynthesis.cancel();
    };
  }, [isSupported]);

  const speak = useCallback(
    (id, text) => {
      if (!isSupported || !text) return;

      // Clicking the button for the message that's currently speaking
      // stops it (toggle off).
      if (speakingId === id) {
        currentIdRef.current = null;
        window.speechSynthesis.cancel();
        setSpeakingId(null);
        return;
      }

      // Switching to a different message — stop whatever was playing
      // before starting the new one, so two messages never overlap.
      window.speechSynthesis.cancel();

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.rate = 1;
      utterance.pitch = 1;
      currentIdRef.current = id;
      utterance.onend = () => {
        // Ignore this event if a newer speak() call has already taken
        // over (guards against the stale-callback race described above).
        if (currentIdRef.current === id) setSpeakingId(null);
      };
      utterance.onerror = () => {
        if (currentIdRef.current === id) setSpeakingId(null);
      };

      setSpeakingId(id);
      window.speechSynthesis.speak(utterance);
    },
    [isSupported, speakingId]
  );

  const stop = useCallback(() => {
    currentIdRef.current = null;
    if (isSupported) window.speechSynthesis.cancel();
    setSpeakingId(null);
  }, [isSupported]);

  return { speak, stop, speakingId, isSupported };
}