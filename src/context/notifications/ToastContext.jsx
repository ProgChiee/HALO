import { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';
import Toast from '../../components/shared/Toast';

const ToastContext = createContext(null);

/**
 * Wrap the app with <ToastProvider> (already done in App.jsx).
 * Usage anywhere in the app:
 *
 *   const { showToast } = useToast();
 *   showToast('Saved successfully!', 'success');
 *   showToast('Something went wrong.', 'error');
 *   showToast('Heads up.', 'info');
 */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timeoutsRef = useRef(new Map());

  // Clear any still-pending auto-dismiss timers when the provider unmounts,
  // so they don't fire setState after the app tree is gone.
  useEffect(() => {
    return () => {
      timeoutsRef.current.forEach((timeoutId) => clearTimeout(timeoutId));
      timeoutsRef.current.clear();
    };
  }, []);

  const showToast = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type }]);
    const timeoutId = setTimeout(() => {
      timeoutsRef.current.delete(id);
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, duration);
    timeoutsRef.current.set(id, timeoutId);
  }, []);

  function dismissToast(id) {
    const timeoutId = timeoutsRef.current.get(id);
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutsRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}

      <div
        style={{
          position: 'fixed',
          top: '16px',
          right: '16px',
          zIndex: 1000,
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
        }}
      >
        {toasts.map((t) => (
          <Toast
            key={t.id}
            message={t.message}
            type={t.type}
            onClose={() => dismissToast(t.id)}
          />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within a ToastProvider');
  return ctx;
}