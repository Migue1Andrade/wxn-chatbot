const rank = {
  PENDING: 0,
  UNKNOWN: 0,
  ACCEPTED: 1,
  SENT: 2,
  DELIVERED: 3,
  READ: 4,
  FAILED: 5,
  SIMULATED: 5,
  RECEIVED: 5,
};
export function mergeMessages(current, incoming) {
  const map = new Map(current.map((message) => [message.id, message]));
  for (const row of incoming) {
    if (!row.id) continue;
    const previous = map.get(row.id);
    if (!previous) {
      map.set(row.id, row);
      continue;
    }
    const before = Date.parse(previous.updatedAt || previous.createdAt) || 0;
    const after = Date.parse(row.updatedAt || row.createdAt) || 0;
    if (after > before || (after === before && (rank[row.status] || 0) >= (rank[previous.status] || 0)))
      map.set(row.id, { ...previous, ...row });
  }
  return [...map.values()].sort(
    (a, b) => (Date.parse(a.createdAt) || 0) - (Date.parse(b.createdAt) || 0) || a.id.localeCompare(b.id),
  );
}
