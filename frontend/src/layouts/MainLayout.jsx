import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar.jsx';
import ErrorBoundary from '../components/ErrorBoundary.jsx';
import Spinner from '../components/Spinner.jsx';

// Shared page chrome; <Outlet /> renders whichever child route matched.
// The navbar stays usable even if a page crashes (the boundary wraps only the page), and
// <Suspense> shows a spinner while a lazily loaded page's code downloads.
export default function MainLayout() {
  const location = useLocation();
  return (
    <div className="app-shell">
      <Navbar />
      <main className="container">
        <ErrorBoundary resetKey={location.pathname}>
          <Suspense fallback={<Spinner />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>
      <footer className="footer">FoodFlow &middot; a portfolio project. No real orders or payments.</footer>
    </div>
  );
}
