import { afterEach, describe, expect, it, vi } from "vitest";

// config.js reads the environment when it is first imported, so each test sets
// the variable and then imports a fresh copy.
async function loadConfig(apiBase) {
  vi.resetModules();
  if (apiBase === undefined) vi.unstubAllEnvs();
  else vi.stubEnv("VITE_API_BASE", apiBase);
  return import("./config");
}

afterEach(() => vi.unstubAllEnvs());

describe("config", () => {
  it("talks to the local backend by default, so development works unchanged", async () => {
    const { API_BASE, socketUrl } = await loadConfig("");
    expect(API_BASE).toBe("http://localhost:8080/api");
    expect(socketUrl()).toBe("ws://localhost:8080/ws/prices");
  });

  it("uses a relative /api and the page's own host in the Docker build", async () => {
    const { API_BASE, socketUrl } = await loadConfig("/api");
    expect(API_BASE).toBe("/api");
    expect(socketUrl()).toBe(`ws://${window.location.host}/ws/prices`);
  });

  it("uses wss:// when the API is served over https", async () => {
    const { socketUrl } = await loadConfig("https://app.example.com/api");
    expect(socketUrl()).toBe("wss://app.example.com/ws/prices");
  });
});
