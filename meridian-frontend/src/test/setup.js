import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Unmount whatever a test rendered, so tests never see each other's DOM.
afterEach(() => cleanup());
