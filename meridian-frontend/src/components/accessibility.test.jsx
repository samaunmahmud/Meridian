import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { axeViolations } from "../test/axe";
import AddTickerModal from "./AddTickerModal";
import AlertsPanel from "./AlertsPanel";
import AccountsPanel from "./AccountsPanel";
import AuthPage from "./AuthPage";
import ConvertModal from "./ConvertModal";
import OrderHistory from "./OrderHistory";
import RecurringOrdersPanel from "./RecurringOrdersPanel";
import TradePanel from "./TradePanel";

vi.mock("../lib/api", () => ({
  login: vi.fn(),
  register: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
  getTickers: vi.fn(() => Promise.resolve([{ symbol: "NVDA", name: "NVIDIA Corporation" }])),
  getPrices: vi.fn(() => Promise.resolve([{ price: 120 }])),
  getWallets: vi.fn(() =>
    Promise.resolve([
      { currency: "USD", balance: 10000, reserved: 0, available: 10000 },
      { currency: "EUR", balance: 1000, reserved: 0, available: 1000 },
    ])
  ),
  getFxRates: vi.fn(() => Promise.resolve([])),
  getAlerts: vi.fn(() =>
    Promise.resolve([{ id: 1, symbol: "NVDA", direction: "ABOVE", targetPrice: 150, triggered: false }])
  ),
  createAlert: vi.fn(),
  deleteAlert: vi.fn(),
  getRecurringOrders: vi.fn(() =>
    Promise.resolve([{ id: 7, symbol: "NVDA", amount: 100, frequency: "WEEKLY", settlementCurrency: "USD" }])
  ),
  createRecurringOrder: vi.fn(),
  deleteRecurringOrder: vi.fn(),
  getOrders: vi.fn(() =>
    Promise.resolve([
      { id: 1, type: "BUY", kind: "LIMIT", status: "PENDING", symbol: "NVDA", quantity: 2, limitPrice: 100, createdAt: "2026-09-19T10:00:00Z" },
      { id: 2, type: "SELL", kind: "MARKET", status: "FILLED", symbol: "NVDA", quantity: 1, price: 120, feeAmount: 0.5, realizedPnL: 12, createdAt: "2026-09-19T11:00:00Z" },
    ])
  ),
  cancelOrder: vi.fn(),
  searchTickers: vi.fn(() => Promise.resolve([{ symbol: "AAPL", name: "Apple Inc.", region: "United States" }])),
  addTicker: vi.fn(),
  convertCurrency: vi.fn(),
  depositToWallet: vi.fn(),
  withdrawFromWallet: vi.fn(),
}));
vi.mock("../lib/toast", () => ({ showToast: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe("AuthPage accessibility", () => {
  it("labels every field so it can be found by its label, not only its placeholder", () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
  });

  it("has one main landmark and one level-1 heading", () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
  });

  it("passes axe on every mode", async () => {
    const { container } = render(<AuthPage onAuthenticated={vi.fn()} />);
    expect(await axeViolations(container, { page: true })).toEqual([]);

    await userEvent.click(screen.getByRole("tab", { name: "Sign up" }));
    expect(screen.getByLabelText("Password")).toHaveAttribute("autocomplete", "new-password");
    expect(await axeViolations(container, { page: true })).toEqual([]);

    await userEvent.click(screen.getByRole("tab", { name: "Log in" }));
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    expect(await axeViolations(container, { page: true })).toEqual([]);
  });

  it("is a real tablist: arrow keys move focus and selection, only the selected tab is in the tab order", async () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    const login = screen.getByRole("tab", { name: "Log in" });
    const signup = screen.getByRole("tab", { name: "Sign up" });
    expect(login).toHaveAttribute("tabindex", "0");
    expect(signup).toHaveAttribute("tabindex", "-1");

    login.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(signup).toHaveFocus();
    expect(signup).toHaveAttribute("aria-selected", "true");
    expect(signup).toHaveAttribute("tabindex", "0");
    expect(login).toHaveAttribute("aria-selected", "false");

    await userEvent.keyboard("{ArrowLeft}");
    expect(login).toHaveFocus();
    await userEvent.keyboard("{End}");
    expect(signup).toHaveFocus();
    await userEvent.keyboard("{Home}");
    expect(login).toHaveFocus();
  });

  it("names the tab panel after the selected tab", async () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    expect(screen.getByRole("tabpanel")).toHaveAccessibleName("Log in");
    await userEvent.click(screen.getByRole("tab", { name: "Sign up" }));
    expect(screen.getByRole("tabpanel")).toHaveAccessibleName("Sign up");
  });
});

describe("form panels", () => {
  it("TradePanel: fields have labels and the toggles say which is selected", async () => {
    const { container } = render(<TradePanel onOrderPlaced={vi.fn()} refreshKey={0} />);
    await screen.findByRole("option", { name: /EUR/ });

    expect(screen.getByLabelText("Symbol")).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toBeInTheDocument();
    expect(screen.getByLabelText("Pay with")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Sell" })).toHaveAttribute("aria-pressed", "false");

    await userEvent.click(screen.getByRole("button", { name: "Limit" }));
    expect(screen.getByRole("button", { name: "Limit" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Limit price")).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });

  it("AlertsPanel: labelled fields, a main landmark, and a Remove button that says which alert", async () => {
    const { container } = render(<AlertsPanel />);
    const remove = await screen.findByRole("button", { name: /Remove alert: NVDA above \$150\.00/ });
    expect(remove).toBeInTheDocument();
    expect(screen.getByLabelText("Symbol")).toBeInTheDocument();
    expect(screen.getByLabelText("Target price")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Above" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });

  it("RecurringOrdersPanel: every control has a name, and Cancel says which buy", async () => {
    const { container } = render(<RecurringOrdersPanel refreshKey={0} />);
    await screen.findByRole("button", { name: /Cancel recurring buy: NVDA/ });
    expect(screen.getByLabelText("Symbol")).toBeInTheDocument();
    expect(screen.getByLabelText("Amount in USD")).toBeInTheDocument();
    expect(screen.getByLabelText("Pay with")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Weekly" })).toHaveAttribute("aria-pressed", "true");
    expect(await axeViolations(container)).toEqual([]);
  });

  it("AccountsPanel: Deposit/Withdraw name their wallet, and focus returns to the opener after Cancel", async () => {
    const { container } = render(<AccountsPanel />);
    const depositEur = await screen.findByRole("button", { name: "Deposit EUR" });
    await userEvent.click(depositEur);

    const amount = screen.getByLabelText("Deposit amount in EUR");
    expect(amount).toHaveFocus();
    expect(await axeViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole("button", { name: "Cancel deposit" }));
    expect(screen.getByRole("button", { name: "Deposit EUR" })).toHaveFocus();
  });

  it("OrderHistory: a captioned-by-name, keyboard-scrollable table with a named Actions column", async () => {
    const { container } = render(<OrderHistory refreshKey={0} />);
    const region = await screen.findByRole("region", { name: "Order history table" });
    expect(region).toHaveAttribute("tabindex", "0");
    // Must be `relative`, or the absolutely-positioned sr-only header escapes the scroll box
    // and makes the whole page scroll sideways on a phone (found with a 320px browser check).
    expect(region).toHaveClass("relative", "overflow-x-auto");
    expect(within(region).getByRole("columnheader", { name: "Actions" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel pending buy order: NVDA" })).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });
});

describe("modals", () => {
  it("ConvertModal: a named dialog whose From, To and Amount fields are labelled", async () => {
    render(<ConvertModal wallets={[{ currency: "USD", balance: 100, available: 100 }]} onClose={vi.fn()} onConverted={vi.fn()} />);
    expect(screen.getByRole("dialog", { name: "Convert currency" })).toBeInTheDocument();
    expect(screen.getByLabelText("From")).toBeInTheDocument();
    expect(screen.getByLabelText("To")).toBeInTheDocument();
    expect(screen.getByLabelText(/Amount/)).toHaveFocus();
    expect(await axeViolations(document.body)).toEqual([]);
  });

  it("AddTickerModal: a named dialog, a labelled search box, results announced, Add says which stock", async () => {
    render(<AddTickerModal onClose={vi.fn()} onAdded={vi.fn()} />);
    expect(screen.getByRole("dialog", { name: "Add a stock" })).toBeInTheDocument();
    const search = screen.getByRole("textbox", { name: "Search by company name or symbol" });
    expect(search).toHaveFocus();

    await userEvent.type(search, "app");
    expect(await screen.findByRole("button", { name: "Add AAPL to the watchlist" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("1 match");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
