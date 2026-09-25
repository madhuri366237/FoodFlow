// "-  2  +" control. Limits mirror the backend (1-20); the backend still validates.
export default function QuantityStepper({ value, onChange, min = 1, max = 20, disabled = false }) {
  return (
    <div className="stepper" aria-label="Quantity">
      <button type="button" aria-label="Decrease" disabled={disabled || value <= min} onClick={() => onChange(value - 1)}>
        −
      </button>
      <span aria-live="polite">{value}</span>
      <button type="button" aria-label="Increase" disabled={disabled || value >= max} onClick={() => onChange(value + 1)}>
        +
      </button>
    </div>
  );
}
