// Turns an Axios error into something to show the user. The backend always answers with
// { status, error, message, fieldErrors? } (GlobalExceptionHandler), so this is the only
// place that needs to know that format.

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const data = error?.response?.data;
  if (data?.fieldErrors) {
    const first = Object.values(data.fieldErrors)[0];
    if (first) return first;
  }
  if (data?.message) return data.message;
  if (error?.code === 'ECONNABORTED') return 'The server took too long to respond. Please try again.';
  if (error && !error.response) return 'Cannot reach the server. Is the backend running?';
  return fallback;
}

// Stable machine-readable code, e.g. "CART_RESTAURANT_MISMATCH", "COUPON_EXPIRED".
export function getErrorCode(error) {
  return error?.response?.data?.error ?? null;
}

// { fieldName: "message" } for showing errors next to form inputs.
export function getFieldErrors(error) {
  return error?.response?.data?.fieldErrors ?? {};
}
