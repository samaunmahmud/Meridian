import { useSyncExternalStore } from "react";

const KEY = "meridian_theme";
const listeners = new Set();

// The source of truth is the data-theme attribute on <html>: the inline
// script in index.html sets it before first paint, and setTheme keeps it
// (and localStorage) in sync. Any component using useTheme() re-renders.
function read() {
  return document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
}

export function setTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    // private mode / storage blocked — the theme still applies for this session
  }
  listeners.forEach((l) => l());
}

function subscribe(callback) {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, read, () => "dark");
  return { theme, setTheme, toggle: () => setTheme(theme === "dark" ? "light" : "dark") };
}
