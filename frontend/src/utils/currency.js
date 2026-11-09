/**
 * Formats a monetary amount into INR (₹) with commas and 2 decimal places.
 * @param {number|string} amount
 * @returns {string} e.g. "₹1,250.00"
 */
export function formatCurrency(amount) {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '₹0.00';
  }
  const numeric = typeof amount === 'number' ? amount : parseFloat(amount);
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numeric);
}

/**
 * Returns raw numeric value rounded to 2 decimals.
 */
export function roundMoney(amount) {
  if (!amount || isNaN(amount)) return 0;
  return Math.round((parseFloat(amount) + Number.EPSILON) * 100) / 100;
}
