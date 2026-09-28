// Builds a CSV file from rows and hands it to the browser as a download.

function cell(value) {
  if (value === null || value === undefined) return "";
  let text = String(value);
  // A spreadsheet treats a cell starting with = + - @ as a formula. None of our own values
  // need that, so neutralise it (a ticker or description could otherwise run code in Excel).
  if (/^[=+\-@\t\r]/.test(text) && !/^-?\d/.test(text)) text = `'${text}`;
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** `columns` is a list of [header, row => value]. */
export function toCsv(rows, columns) {
  const lines = [columns.map(([header]) => cell(header)).join(",")];
  for (const row of rows) lines.push(columns.map(([, get]) => cell(get(row))).join(","));
  return lines.join("\r\n") + "\r\n";
}

export function downloadCsv(filename, text) {
  // The byte-order mark makes Excel read the file as UTF-8 (currency symbols survive).
  const url = URL.createObjectURL(new Blob(["﻿", text], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
