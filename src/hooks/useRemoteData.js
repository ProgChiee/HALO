import { useCallback, useEffect, useRef, useState } from 'react';

// Load a resource with cancellation and latest-request-wins semantics.
// Keep loader stable (a service function or a useCallback).
export function useRemoteData(loader, initialData = null) {
  const [data, setData] = useState(initialData);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const requestRef = useRef(null);

  const fetchData = useCallback((controller) => {
    return Promise.resolve().then(() => {
      controller.signal.throwIfAborted();
      return loader({ signal: controller.signal });
    }).then((result) => {
      if (controller.signal.aborted) return false;
      setData(result);
      setError(null);
      return true;
    }).catch((err) => {
      if (!controller.signal.aborted) setError(err);
      return false;
    }).finally(() => {
      if (!controller.signal.aborted) setIsLoading(false);
    });
  }, [loader]);

  useEffect(() => {
    const controller = new AbortController();
    requestRef.current = controller;
    fetchData(controller);
    return () => {
      controller.abort();
      requestRef.current?.abort();
    };
  }, [fetchData]);

  const reload = useCallback(() => {
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setIsLoading(true);
    setError(null);
    return fetchData(controller);
  }, [fetchData]);

  return { data, setData, isLoading, error, reload };
}
