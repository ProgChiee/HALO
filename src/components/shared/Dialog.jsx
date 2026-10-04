import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import styles from './Dialog.module.css';

const focusable = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])';

// Mounted only while open. Portal keeps the dialog outside the inert page.
export default function Dialog({ children, labelledBy, describedBy, onClose, busy = false, className = '', initialFocus = 'input:not(:disabled)' }) {
  const [portal] = useState(() => document.createElement('div'));
  const dialog = useRef(null);
  useEffect(() => {
    const opener = document.activeElement;
    document.body.append(portal);
    const background = [...document.body.children].filter(element => element !== portal);
    const priorInert = background.map(element => element.hasAttribute('inert'));
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    background.forEach(element => element.setAttribute('inert', ''));
    const node = dialog.current;
    (node.querySelector(initialFocus) || node).focus();
    const containFocus = event => {
      if (!node.contains(event.target)) (node.querySelector(focusable) || node).focus();
    };
    document.addEventListener('focusin', containFocus);
    return () => {
      document.removeEventListener('focusin', containFocus);
      background.forEach((element, i) => { if (!priorInert[i]) element.removeAttribute('inert'); });
      document.body.style.overflow = overflow;
      portal.remove();
      if (!opener?.isConnected) return;
      if (!opener.disabled) opener.focus();
      else {
        // Create closes before its authoritative GET unlocks the opener.
        const observer = new window.MutationObserver(() => {
          if (!opener.isConnected || !opener.disabled) {
            observer.disconnect();
            if (opener.isConnected && !document.querySelector('[aria-modal="true"]')) opener.focus();
          }
        });
        observer.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['disabled'] });
      }
    };
  }, [portal, initialFocus]);

  function keyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopPropagation();
      if (!busy) onClose();
    }
    if (event.key === 'Tab') {
      const items = [...dialog.current.querySelectorAll(focusable)]
        .filter(element => !element.hidden && element.getAttribute('aria-hidden') !== 'true')
        .sort((a, b) => a.compareDocumentPosition(b) & 4 ? -1 : 1);
      const index = items.indexOf(document.activeElement);
      event.preventDefault();
      if (!items.length) dialog.current.focus();
      else items[(index + (event.shiftKey ? -1 : 1) + items.length) % items.length].focus();
    }
  }
  return createPortal(<div className={styles.overlay} onClick={() => { if (!busy) onClose(); }}>
    <div ref={dialog} className={styles.dialog + ' ' + className} role="dialog" aria-modal="true"
      aria-labelledby={labelledBy} aria-describedby={describedBy} aria-busy={busy || undefined}
      tabIndex={-1} onKeyDown={keyDown} onClick={event => event.stopPropagation()}>
      {children}
    </div>
  </div>, portal);
}
