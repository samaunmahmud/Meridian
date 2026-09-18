// A tiny global toast system. Any component calls showToast(...) and the
// single <ToastContainer/> mounted in App.jsx picks it up — no context
// provider boilerplate needed, similar pattern to the session-expired event.
export function showToast(kind, message) {
  window.dispatchEvent(new CustomEvent("meridian:toast", { detail: { kind, message } }));
}
