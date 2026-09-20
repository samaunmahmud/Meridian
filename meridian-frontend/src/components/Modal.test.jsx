import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Modal from "./Modal";

function Harness({ onClose = () => {} }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open it</button>
      {open && (
        <Modal
          title="Edit thing"
          onClose={() => {
            onClose();
            setOpen(false);
          }}
        >
          <input aria-label="Name" />
          <button type="button">Save</button>
        </Modal>
      )}
    </>
  );
}

// The real app renders into <div id="root">; Modal makes that element inert.
let root;
beforeEach(() => {
  root = document.createElement("div");
  root.id = "root";
  document.body.appendChild(root);
});
afterEach(() => root.remove());

const renderHarness = (props) => render(<Harness {...props} />, { container: root });

describe("Modal", () => {
  it("is a named modal dialog", async () => {
    renderHarness();
    await userEvent.click(screen.getByRole("button", { name: "Open it" }));
    const dialog = screen.getByRole("dialog", { name: "Edit thing" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("moves focus inside, makes the page behind inert, and undoes both on close", async () => {
    renderHarness();
    const opener = screen.getByRole("button", { name: "Open it" });
    await userEvent.click(opener);

    expect(screen.getByRole("dialog").contains(document.activeElement)).toBe(true);
    expect(root).toHaveAttribute("inert");

    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(root).not.toHaveAttribute("inert");
    expect(opener).toHaveFocus(); // focus goes back to what opened it
  });

  it("keeps Tab and Shift+Tab inside the dialog", async () => {
    renderHarness();
    await userEvent.click(screen.getByRole("button", { name: "Open it" }));
    const close = screen.getByRole("button", { name: "Close" });
    const name = screen.getByLabelText("Name");
    const save = screen.getByRole("button", { name: "Save" });

    save.focus();
    await userEvent.tab();
    expect(close).toHaveFocus(); // last -> first

    await userEvent.tab({ shift: true });
    expect(save).toHaveFocus(); // first -> last

    name.focus();
    await userEvent.tab();
    expect(save).toHaveFocus(); // normal movement is untouched
  });

  it("closes on Escape and on a click on the backdrop, but not on a click inside", async () => {
    const onClose = vi.fn();
    renderHarness({ onClose });

    await userEvent.click(screen.getByRole("button", { name: "Open it" }));
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Open it" }));
    await userEvent.click(screen.getByLabelText("Name"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("dialog").parentElement); // the dim backdrop
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
