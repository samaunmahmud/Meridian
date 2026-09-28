import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrderNoteModal from "./OrderNoteModal";
import { setOrderNote } from "../lib/api";
import { axeViolations } from "../test/axe";

vi.mock("../lib/api", () => ({ setOrderNote: vi.fn() }));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

const ORDER = { id: 3, symbol: "AAPL", type: "BUY", quantity: 2, note: "Old idea" };

beforeEach(() => vi.clearAllMocks());

describe("OrderNoteModal", () => {
  it("edits the existing note and hands back the updated order", async () => {
    setOrderNote.mockResolvedValue({ ...ORDER, note: "Earnings beat" });
    const onSaved = vi.fn();
    render(<OrderNoteModal order={ORDER} onClose={() => {}} onSaved={onSaved} />);

    const box = screen.getByRole("textbox");
    expect(box).toHaveValue("Old idea");
    expect(box).toHaveFocus();
    expect(await axeViolations(screen.getByRole("dialog"))).toEqual([]);

    await userEvent.clear(box);
    await userEvent.type(box, "Earnings beat");
    expect(screen.getByText("13/500")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    expect(setOrderNote).toHaveBeenCalledWith(3, "Earnings beat");
    expect(onSaved).toHaveBeenCalledWith({ ...ORDER, note: "Earnings beat" });
  });
});
