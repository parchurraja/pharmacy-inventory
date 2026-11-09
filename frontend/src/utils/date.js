/**
 * Formats ISO date or string into clean human-readable date.
 * e.g., "15 Nov 2026"
 */
export function formatDate(dateString) {
  if (!dateString) return '—';
  try {
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return dateString;
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    }).format(date);
  } catch {
    return dateString;
  }
}

/**
 * Formats ISO datetime string into date + time.
 * e.g., "15 Nov 2026, 04:30 PM"
 */
export function formatDateTime(dateTimeString) {
  if (!dateTimeString) return '—';
  try {
    const date = new Date(dateTimeString);
    if (isNaN(date.getTime())) return dateTimeString;
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(date);
  } catch {
    return dateTimeString;
  }
}

/**
 * Calculates days remaining until a given expiry date.
 * Negative number indicates days since it expired.
 */
export function getDaysUntil(expiryDateString) {
  if (!expiryDateString) return 0;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(expiryDateString);
  target.setHours(0, 0, 0, 0);

  const diffTime = target.getTime() - today.getTime();
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
}
