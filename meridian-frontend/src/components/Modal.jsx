import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

// An accessible modal dialog: it has a name, keeps Tab inside itself, closes on Escape,
// makes the page behind it inert (so a screen reader cannot wander off into it) and puts
// focus back on whatever opened it. It is rendered into <body>, outside #root, which is
// what lets #root be inert while the dialog and the toasts stay reachable.
export default function Modal({ title, onClose, children }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const openerRef = useRef(typeof document !== "undefined" ? document.activeElement : null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const opener = openerRef.current;
    const root = document.getElementById("root");
    root?.setAttribute("inert", "");

    // Focus the field marked data-autofocus, else the first control that is not "Close".
    // (React's autoFocus is not used: inside a portal it is timing-dependent.)
    const dialog = dialogRef.current;
    if (dialog) {
      const target =
        dialog.querySelector("[data-autofocus]") ??
        [...dialog.querySelectorAll(FOCUSABLE)].find((el) => el.getAttribute("aria-label") !== "Close") ??
        dialog;
      target.focus();
    }

    return () => {
      root?.removeAttribute("inert");
      if (opener && document.contains(opener)) opener.focus();
    };
  }, []);

  function handleKeyDown(e) {
    if (e.key === "Escape") {
      e.stopPropagation();
      onCloseRef.current();
      return;
    }
    if (e.key !== "Tab") return;
    const items = [...dialogRef.current.querySelectorAll(FOCUSABLE)];
    if (items.length === 0) {
      e.preventDefault();
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 bg-black/60 z-40 flex items-start justify-center pt-24 px-4"
      onMouseDown={(e) => {
        // A click on the dim backdrop (not on the dialog) dismisses it.
        if (e.target === e.currentTarget) onCloseRef.current();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        data-ring-parent
        onKeyDown={handleKeyDown}
        className="bg-panel border border-line rounded-[20px] w-full max-w-md p-5 fade-in outline-none text-bone"
      >
        <div className="flex items-center justify-between mb-4">
          <h2 id={titleId} className="text-sm font-medium">
            {title}
          </h2>
          <button
            type="button"
            onClick={() => onCloseRef.current()}
            aria-label="Close"
            className="w-8 h-8 -mr-2 rounded-lg flex items-center justify-center text-dim hover:text-bone transition-colors"
          >
            <span aria-hidden="true" className="text-lg leading-none">
              &times;
            </span>
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  );
}
