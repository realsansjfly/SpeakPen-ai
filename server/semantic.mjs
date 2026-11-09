export function retrievalInput(input) {
  if (!input || typeof input.query !== 'string' || !input.query.trim() || input.query.length > 3000) throw new Error('query');
  if (![4, 8, 12].includes(input.count) || !Array.isArray(input.books) || input.books.length > 3 ||
      input.books.some(book => !['anki-cet4', 'anki-cet6', 'anki-ielts'].includes(book)) ||
      !['SIMPLE', 'DAILY', 'DEEP'].includes(input.level)) throw new Error('options');
  return { query: input.query.trim(), count: input.count, books: input.books, level: input.level };
}
export async function retrieve(input, token, fetchImpl = fetch, timeout = 2500) {
  const response = await fetchImpl('http://127.0.0.1:8791/search', { method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(retrievalInput(input)), signal: AbortSignal.timeout(timeout), redirect: 'error' });
  if (!response.ok) throw new Error('unavailable');
  const result = await response.json();
  if (typeof result.version !== 'string' || !Array.isArray(result.candidates) || result.candidates.length > input.count ||
    result.candidates.some(c => typeof c.id !== 'string' || typeof c.expression !== 'string' || typeof c.meaning !== 'string' ||
      c.expression.length > 200 || c.meaning.length > 500 || !input.books.includes(c.book))) throw new Error('invalid_result');
  return result;
}
