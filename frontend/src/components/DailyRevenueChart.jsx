import { useState } from 'react';
import { formatPrice } from '../utils/format.js';

const WIDTH = 560;
const HEIGHT = 200;
const PAD = { top: 16, right: 8, bottom: 28, left: 8 };
const BAR_GAP = 10;

const dayLabel = (isoDate) =>
  new Date(`${isoDate}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric' });

/**
 * Revenue per day for the last 7 days: [{ date, orders, revenue }] from the backend,
 * one bar per day. The days are already zero-filled by the server.
 * Single series: one hue, no legend (the title names it). Hovering or focusing a bar
 * shows its exact values; a table view is available for screen readers and precise reading.
 */
export default function DailyRevenueChart({ points, title = 'Revenue, last 7 days' }) {
  const [active, setActive] = useState(null);
  const max = Math.max(...points.map((p) => Number(p.revenue)), 1);
  const plotWidth = WIDTH - PAD.left - PAD.right;
  const plotHeight = HEIGHT - PAD.top - PAD.bottom;
  const slot = plotWidth / points.length;
  const barWidth = Math.min(slot - BAR_GAP, 48);
  const hovered = active === null ? null : points[active];

  return (
    <figure className="chart card">
      <figcaption className="row-between">
        <strong>{title}</strong>
        <span className="muted small" aria-live="polite">
          {hovered
            ? `${dayLabel(hovered.date)}: ${formatPrice(hovered.revenue)} · ${hovered.orders} order${hovered.orders === 1 ? '' : 's'}`
            : 'Hover a bar for details'}
        </span>
      </figcaption>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={title} className="chart-svg">
        <line
          x1={PAD.left}
          x2={WIDTH - PAD.right}
          y1={HEIGHT - PAD.bottom}
          y2={HEIGHT - PAD.bottom}
          className="chart-axis"
        />
        {points.map((point, index) => {
          const value = Number(point.revenue);
          const height = value === 0 ? 0 : Math.max((value / max) * plotHeight, 3);
          const x = PAD.left + index * slot + (slot - barWidth) / 2;
          const y = HEIGHT - PAD.bottom - height;
          return (
            <g
              key={point.date}
              tabIndex={0}
              onMouseEnter={() => setActive(index)}
              onMouseLeave={() => setActive(null)}
              onFocus={() => setActive(index)}
              onBlur={() => setActive(null)}
              aria-label={`${dayLabel(point.date)}: ${formatPrice(point.revenue)}, ${point.orders} orders`}
            >
              {/* Invisible full-height hit target: easier to hover than a short bar. */}
              <rect x={PAD.left + index * slot} y={PAD.top} width={slot} height={plotHeight} fill="transparent" />
              {height > 0 && (
                <path
                  className={`chart-bar ${active === index ? 'active' : ''}`}
                  d={roundedTopBar(x, y, barWidth, height, 4)}
                />
              )}
              <text x={x + barWidth / 2} y={HEIGHT - 8} textAnchor="middle" className="chart-label">
                {dayLabel(point.date)}
              </text>
            </g>
          );
        })}
      </svg>
      <details>
        <summary className="muted small">View as table</summary>
        <table className="table small">
          <thead>
            <tr>
              <th>Day</th>
              <th>Orders</th>
              <th>Revenue</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.date}>
                <td>{dayLabel(point.date)}</td>
                <td>{point.orders}</td>
                <td>{formatPrice(point.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}

// Bar with rounded top corners and a square base sitting on the axis.
function roundedTopBar(x, y, width, height, radius) {
  const r = Math.min(radius, width / 2, height);
  return `M${x},${y + height} V${y + r} Q${x},${y} ${x + r},${y} H${x + width - r} Q${x + width},${y} ${x + width},${y + r} V${y + height} Z`;
}
