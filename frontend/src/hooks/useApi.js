import { useCallback, useEffect, useState } from 'react';

// Loads data when the component mounts or a dependency changes:
//   const { data, loading, error, reload, setData } = useApi(() => orderService.get(id), [id]);
// Ignores responses that arrive after the component moved on (unmounted, or deps changed),
// so a slow old request can never overwrite newer data.
export default function useApi(request, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState((previous) => ({ ...previous, loading: true, error: null }));
    request()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: null });
      })
      .catch((error) => {
        if (!cancelled) setState({ data: null, loading: false, error });
      });
    return () => {
      cancelled = true;
    };
    // The caller's deps decide when to reload; "request" is a new function every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const setData = useCallback(
    (update) =>
      setState((previous) => ({
        ...previous,
        data: typeof update === 'function' ? update(previous.data) : update,
      })),
    [],
  );

  return { ...state, reload, setData };
}
