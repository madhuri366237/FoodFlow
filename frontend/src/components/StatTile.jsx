// A headline number: faster to read than any chart when there's one value to show.
export default function StatTile({ label, value, hint }) {
  return (
    <div className="stat-tile card">
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint && <span className="muted small">{hint}</span>}
    </div>
  );
}
