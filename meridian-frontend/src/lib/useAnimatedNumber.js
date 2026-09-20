import { useEffect, useRef, useState } from "react";

// Animates FROM the previously displayed value TO the new real value over
// ~500ms, using requestAnimationFrame. The animation is purely cosmetic —
// the actual value driving it always comes from real API data.
export function useAnimatedNumber(target, duration = 500) {
  const [display, setDisplay] = useState(target);
  const fromRef = useRef(target);
  const rafRef = useRef(null);

  useEffect(() => {
    const from = fromRef.current;
    const to = target;
    // People who asked for less motion get the new value straight away.
    const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion || from === to || typeof to !== "number" || Number.isNaN(to)) {
      setDisplay(to);
      fromRef.current = to;
      return;
    }

    const start = performance.now();
    function tick(now) {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
      setDisplay(from + (to - from) * eased);
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = to;
      }
    }
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [target, duration]);

  return display;
}
