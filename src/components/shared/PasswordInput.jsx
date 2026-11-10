import { useEffect, useId, useRef, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import styles from './PasswordInput.module.css';

/** Presentation only; each field owns visibility and forwards native input attributes. */
export default function PasswordInput({ id, className = '', value, disabled, ...props }) {
  const generatedId = useId();
  const inputId = id || generatedId;
  const input = useRef(null);
  const [visible, setVisible] = useState(false);
  const [previousValue, setPreviousValue] = useState(value);
  // Reset visibility when a controlled form clears this field, without altering its value.
  if (previousValue !== value) {
    setPreviousValue(value);
    if (value === '') setVisible(false);
  }
  useEffect(() => {
    const form = input.current?.form;
    const reset = () => setVisible(false);
    form?.addEventListener('reset', reset);
    return () => form?.removeEventListener('reset', reset);
  }, []);
  return <div className={styles.wrapper}>
    <input {...props} ref={input} id={inputId} value={value} disabled={disabled}
      type={visible ? 'text' : 'password'} className={`${className} ${styles.input}`} />
    <button type="button" className={styles.toggle} disabled={disabled}
      aria-label={visible ? 'Hide password' : 'Show password'} aria-controls={inputId}
      aria-pressed={visible} onClick={() => setVisible(current => !current)}>
      {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
    </button>
  </div>;
}
