import { formatEnum } from '../utils/format.js';

// Colour classes per order/payment status are defined in index.css (.badge-<status>).
export default function StatusBadge({ status }) {
  return <span className={`badge badge-${status?.toLowerCase()}`}>{formatEnum(status)}</span>;
}
