// Bound fan-out when the backend exposes per-student progress only.
export async function mapWithConcurrency(items, limit, mapper, signal) {
  const results = new Array(items.length);
  let nextIndex = 0;
  async function worker() {
    while (nextIndex < items.length) {
      signal?.throwIfAborted();
      const index = nextIndex++;
      results[index] = await mapper(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}
