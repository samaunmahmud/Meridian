import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAnimatedNumber } from "./useAnimatedNumber";

function mockReducedMotion(reduce) {
  window.matchMedia = vi.fn((query) => ({ matches: reduce && query.includes("prefers-reduced-motion"), addEventListener() {}, removeEventListener() {} }));
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  vi.useRealTimers();
  delete window.matchMedia;
});

describe("useAnimatedNumber", () => {
  it("counts up to a new value over time by default", () => {
    mockReducedMotion(false);
    const { result, rerender } = renderHook(({ v }) => useAnimatedNumber(v), { initialProps: { v: 100 } });
    rerender({ v: 200 });
    act(() => vi.advanceTimersByTime(100));
    expect(result.current).toBeGreaterThan(100);
    expect(result.current).toBeLessThan(200);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current).toBe(200);
  });

  it("jumps straight to the new value when the user asked for reduced motion", () => {
    mockReducedMotion(true);
    const { result, rerender } = renderHook(({ v }) => useAnimatedNumber(v), { initialProps: { v: 100 } });
    rerender({ v: 200 });
    expect(result.current).toBe(200);
  });
});
