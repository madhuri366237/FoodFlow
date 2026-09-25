import { getErrorMessage } from '../utils/errors.js';

// Shows a backend (or network) error; pass either an Axios error or a plain message.
export default function ErrorMessage({ error, message, onRetry }) {
  if (!error && !message) return null;
  return (
    <div className="alert alert-error" role="alert">
      <span>{message ?? getErrorMessage(error)}</span>
      {onRetry && (
        <button type="button" className="btn btn-sm btn-outline" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
