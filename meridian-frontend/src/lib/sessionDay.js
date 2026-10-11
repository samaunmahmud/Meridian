export const sameDay = (a, b) => new Date(a).toDateString() === new Date(b).toDateString();

// "Friday" within the past week, "3 Oct" before that.
export function dayName(at, now) {
  const date = new Date(at);
  return now - date.getTime() < 6 * 24 * 60 * 60 * 1000
    ? date.toLocaleDateString([], { weekday: "long" })
    : date.toLocaleDateString([], { day: "numeric", month: "short" });
}
