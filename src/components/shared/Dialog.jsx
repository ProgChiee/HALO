import { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import styles from './Dialog.module.css';

const focusable = 'button:not(:disabled), a[href], input:not(:disabled):not([type="hidden"]), select:not(:disabled), textarea:not(:disabled), iframe, [tabindex]:not([tabindex="-1"])';
const stack = [];
const originalInert = new Map();
let originalOverflow, backgroundObserver;
const top = () => stack.at(-1);
function available(element) {
  if (element.disabled || element.tabIndex < 0 || element.closest('[hidden], [inert], [aria-hidden="true"]')) return false;
  for (let node = element; node; node = node.parentElement) {
    const style = window.getComputedStyle(node);
    if (style.display === 'none' || style.visibility === 'hidden') return false;
  }
  return true;
}
function controls(node) {
  return [...node.querySelectorAll(focusable)].filter(available)
    .sort((a, b) => a.compareDocumentPosition(b) & 4 ? -1 : 1);
}
function focusInside(entry) {
  const preferred = entry.node.querySelector(entry.initialFocus);
  (preferred && available(preferred) ? preferred : controls(entry.node)[0] || entry.node).focus();
}
function isolate() {
  const active = top();
  for (const element of document.body.children) {
    if (!originalInert.has(element)) originalInert.set(element, element.hasAttribute('inert'));
    if (element === active?.portal && !originalInert.get(element)) element.removeAttribute('inert');
    else element.setAttribute('inert', '');
  }
  stack.forEach((entry, index) => { entry.portal.style.zIndex = String(1000 + index); });
}
function containFocus(event) {
  const entry = top();
  if (entry && !entry.node.contains(event.target)) focusInside(entry);
}
function blockBackground(event) {
  const entry = top();
  if (entry && !entry.node.contains(event.target)) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }
}

// All modal input belongs to the topmost portal. Escape is opt-in for safe reads.
export default function Dialog({ children, labelledBy, describedBy, onClose, busy = false, closeOnEscape = false, className = '', initialFocus = 'input:not(:disabled)' }) {
  const [portal] = useState(() => Object.assign(document.createElement('div'), { className: styles.portal }));
  const dialog = useRef(null);
  useLayoutEffect(() => {
    const opener = document.activeElement;
    document.body.append(portal);
    const entry = { portal, node: dialog.current, initialFocus };
    stack.push(entry);
    if (stack.length === 1) {
      originalOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      document.addEventListener('focusin', containFocus, true);
      for (const type of ['pointerdown', 'mousedown', 'click', 'touchstart', 'keydown']) document.addEventListener(type, blockBackground, { capture: true, passive: false });
      backgroundObserver = new window.MutationObserver(isolate);
      backgroundObserver.observe(document.body, { childList: true });
    }
    isolate();
    focusInside(entry);
    return () => {
      const wasTop = top() === entry;
      stack.splice(stack.indexOf(entry), 1);
      portal.remove();
      if (!stack.length) {
        backgroundObserver.disconnect();
        document.removeEventListener('focusin', containFocus, true);
        for (const type of ['pointerdown', 'mousedown', 'click', 'touchstart', 'keydown']) document.removeEventListener(type, blockBackground, true);
        for (const [element, inert] of originalInert) {
          if (!inert) element.removeAttribute('inert');
        }
        originalInert.clear();
        document.body.style.overflow = originalOverflow;
      } else isolate();
      if (!wasTop) return;
      const canRestore = () => opener?.isConnected && !opener.disabled && !opener.closest('[inert]') && (!top() || top().node.contains(opener));
      if (canRestore()) opener.focus();
      else {
        if (top()) focusInside(top());
        if (opener?.isConnected && opener.disabled) {
          const observer = new window.MutationObserver(() => {
            if (!opener.isConnected || !opener.disabled) {
              observer.disconnect();
              if (canRestore()) opener.focus();
            }
          });
          observer.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['disabled'] });
        }
      }
    };
  }, [portal, initialFocus]);

  function keyDown(event) {
    if (top()?.node !== dialog.current) return;
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopPropagation();
      if (closeOnEscape && !busy) onClose?.();
    }
    if (event.key === 'Tab') {
      event.preventDefault(); event.stopPropagation();
      const items = controls(dialog.current);
      const index = items.indexOf(document.activeElement);
      if (!items.length) dialog.current.focus();
      else if (index < 0) items[event.shiftKey ? items.length - 1 : 0].focus();
      else items[(index + (event.shiftKey ? -1 : 1) + items.length) % items.length].focus();
    }
  }
  return createPortal(<div className={styles.overlay}>
    <div ref={dialog} className={styles.dialog + ' ' + className} role="dialog" aria-modal="true"
      aria-labelledby={labelledBy} aria-describedby={describedBy} aria-busy={busy || undefined}
      tabIndex={-1} onKeyDown={keyDown}>
      {children}
    </div>
  </div>, portal);
}
