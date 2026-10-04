import { useCallback, useEffect, useRef, useState } from 'react';

// Load a resource with cancellation and latest-request-wins semantics.
// Keep loader stable (a service function or a useCallback).
export function useRemoteData(loader, initialData = null) {
  const [data, setData] = useState(initialData);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const requestRef = useRef(null);
  const mountedRef = useRef(false);

  const fetchData = useCallback((controller) => {
    const isCurrent = () => mountedRef.current && requestRef.current === controller && !controller.signal.aborted;
    return Promise.resolve().then(() => {
      controller.signal.throwIfAborted();
      if (!isCurrent()) return;
      setIsLoading(true);
      setError(null);
      return loader({ signal: controller.signal });
    }).then((result) => {
      if (!isCurrent()) return false;
      setData(result);
      setError(null);
      return true;
    }).catch((err) => {
      if (isCurrent()) setError(err);
      return false;
    }).finally(() => {
      if (isCurrent()) setIsLoading(false);
    });
  }, [loader]);

  useEffect(() => {
    mountedRef.current = true;
    const controller = new AbortController();
    requestRef.current = controller;
    fetchData(controller);
    return () => {
      mountedRef.current = false;
      controller.abort();
      requestRef.current?.abort();
    };
  }, [fetchData]);

  const reload = useCallback(() => {
    if (!mountedRef.current) return Promise.resolve(false);
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setIsLoading(true);
    setError(null);
    return fetchData(controller);
  }, [fetchData]);

  return { data, setData, isLoading, error, reload };
}
