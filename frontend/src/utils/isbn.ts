export interface ParsedIsbnInput {
  counts: Map<string, number>
  invalidTokens: string[]
}

function normalizeToken(raw: string): string {
  return raw
    .replace(/^ISBN(?:-13)?:?/i, '')
    .replace(/[\s-]/g, '')
    .trim()
}

export function isValidIsbn13(isbn: string): boolean {
  if (!/^97[89]\d{10}$/.test(isbn)) {
    return false
  }

  const sum = isbn
    .slice(0, 12)
    .split('')
    .reduce((total, digit, index) => total + Number(digit) * (index % 2 === 0 ? 1 : 3), 0)
  const checkDigit = (10 - (sum % 10)) % 10
  return checkDigit === Number(isbn[12])
}

export function parseIsbnInput(raw: string): ParsedIsbnInput {
  const tokens = raw
    .split(/[\n,，;；]+/)
    .flatMap((part) => part.trim().split(/\s+/))
    .map(normalizeToken)
    .filter(Boolean)

  const counts = new Map<string, number>()
  const invalidTokens: string[] = []

  tokens.forEach((token) => {
    if (!isValidIsbn13(token)) {
      invalidTokens.push(token)
      return
    }
    counts.set(token, (counts.get(token) ?? 0) + 1)
  })

  return { counts, invalidTokens }
}
