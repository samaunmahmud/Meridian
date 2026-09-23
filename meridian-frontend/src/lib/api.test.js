import { afterEach, describe, expect, it, vi } from "vitest";
import { getMe, getTickers, isServerUnavailable } from "./api";

afterEach(() => vi.unstubAllGlobals());

function respond(status, body = {}) {
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status }))));
}

describe("API errors say whether the server is unavailable", () => {
  it("a 503 is unavailable", async () => {
    respond(503, { message: "Service unavailable" });
    const err = await getMe().catch((e) => e);
    expect(err.status).toBe(503);
    expect(isServerUnavailable(err)).toBe(true);
  });

  it("a network failure is unavailable, with a readable message", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    const err = await getTickers().catch((e) => e);
    expect(err.status).toBe(0);
    expect(err.message).toMatch(/Cannot reach Meridian/);
    expect(isServerUnavailable(err)).toBe(true);
  });

  it("not signed in (401) and a refused request (400) are not", async () => {
    respond(401);
    expect(isServerUnavailable(await getMe().catch((e) => e))).toBe(false);
    respond(400, { message: "bad" });
    const err = await getTickers().catch((e) => e);
    expect(err.message).toBe("bad");
    expect(isServerUnavailable(err)).toBe(false);
  });
});
