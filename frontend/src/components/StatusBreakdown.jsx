import StatusBadge from './StatusBadge.jsx';

// { PLACED: 3, CONFIRMED: 1, ... } as a labelled list: exact counts, no colour-only encoding.
export default function StatusBreakdown({ counts }) {
  return (
    <div className="card">
      <strong>Orders by status</strong>
      <ul className="status-breakdown">
        {Object.entries(counts).map(([status, count]) => (
          <li key={status} className="row-between">
            <StatusBadge status={status} />
            <span className="price">{count}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
