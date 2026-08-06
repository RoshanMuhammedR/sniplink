import { useState, type FormEvent } from 'react'

interface Props {
  onSubmit: (url: string) => void
  busy: boolean
}

export default function ShortenForm({ onSubmit, busy }: Props) {
  const [value, setValue] = useState('')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = value.trim()
    if (trimmed && !busy) onSubmit(trimmed)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row">
      <input
        type="text"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="Paste a long URL..."
        aria-label="URL to shorten"
        autoComplete="off"
        spellCheck={false}
        className="flex-1 rounded-lg border border-slate-300 bg-white px-4 py-3 text-slate-900
                   placeholder:text-slate-400 transition-colors outline-none
                   focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/30
                   dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100
                   dark:placeholder:text-slate-500"
      />
      <button
        type="submit"
        disabled={busy || value.trim() === ''}
        className="rounded-lg bg-indigo-600 px-6 py-3 font-medium text-white transition-colors
                   hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50
                   disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? 'Shortening…' : 'Shorten'}
      </button>
    </form>
  )
}
