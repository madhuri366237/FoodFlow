import { Component } from 'react';

/**
 * Catches errors thrown while RENDERING (a bug, unexpected data) so one broken page shows a
 * message instead of blanking the whole app to a white screen. API errors don't come through
 * here; pages handle those themselves with <ErrorMessage>.
 * React only supports this as a class component (componentDidCatch has no hook equivalent).
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // In production this would go to an error-tracking service (e.g. Sentry).
    console.error('Render error', error, info.componentStack);
  }

  componentDidUpdate(previousProps) {
    // Navigating to another page gives the app a fresh chance.
    if (this.state.hasError && previousProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <section className="page narrow empty-state" role="alert">
          <h1>Something went wrong</h1>
          <p className="muted">This page hit an unexpected error. Your data is safe.</p>
          <button type="button" className="btn btn-primary" onClick={() => window.location.assign('/')}>
            Back to home
          </button>
        </section>
      );
    }
    return this.props.children;
  }
}
