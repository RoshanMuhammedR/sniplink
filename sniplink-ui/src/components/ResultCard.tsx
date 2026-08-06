import { useState } from 'react'
import type { ShortenResponse } from '../types'

interface Props {
  result: ShortenResponse
  onViewAnalytics: (code: string) => void
  onReset: () => void
}

export default function ResultCard({ result, onViewAnalytics, onReset }: Props) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(result.shortUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      // Clipboard access needs a secure context; fall back to selection.
      window.prompt('Copy this short URL:', result.shortUrl)
    }
  }

  return (
    <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-slate-900/60">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Your short link
      </p>

      <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <a
          href={result.shortUrl}
          target="_blank"
          rel="noreferrer"
          className="font-mono text-lg break-all text-indigo-600 hover:underline dark:text-indigo-400"
        >
          {result.shortUrl}
        </a>
        <button
          onClick={copy}
          className="shrink-0 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium
                     text-slate-700 transition-colors hover:bg-slate-100
                     dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
        >
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      <p className="mt-3 truncate text-sm text-slate-500 dark:text-slate-400" title={result.originalUrl}>
        {result.originalUrl}
      </p>

      <div className="mt-4 flex gap-4 text-sm">
        <button
          onClick={() => onViewAnalytics(result.shortCode)}
          className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
        >
          View analytics
        </button>
        <button
          onClick={onReset}
          className="font-medium text-slate-500 hover:underline dark:text-slate-400"
        >
          Shorten another
        </button>
      </div>
    </div>
  )
}
