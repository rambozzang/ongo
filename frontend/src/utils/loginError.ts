/**
 * Convert transport-level login failures into a localized UI message.
 * Domain errors returned by the API are kept so actionable server guidance is
 * not lost, while browser/Axios implementation details stay out of the UI.
 */
export function loginErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback
  const message = error.message.trim()
  if (!message || message === 'Network Error' || /^Request failed with status code \d+$/.test(message)) {
    return fallback
  }
  return message
}
