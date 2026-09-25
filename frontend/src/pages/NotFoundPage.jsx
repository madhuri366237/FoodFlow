import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <section className="hero">
      <h1>Page not found</h1>
      <p className="muted">The page you are looking for does not exist.</p>
      <Link to="/">Back to home</Link>
    </section>
  );
}
