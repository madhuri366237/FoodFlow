const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

// 538.2 -> "₹538.20". Display only: every amount is calculated by the backend.
export function formatPrice(amount) {
  return rupees.format(Number(amount ?? 0));
}

export function formatDateTime(isoString) {
  if (!isoString) return '';
  return new Date(isoString).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

// "READY_FOR_PICKUP" -> "Ready for pickup"
export function formatEnum(value) {
  if (!value) return '';
  const text = value.toLowerCase().replace(/_/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}
