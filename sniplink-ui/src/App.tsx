import { useEffect, useState } from 'react'
import ShortenForm from './components/ShortenForm'
import ResultCard from './components/ResultCard'
import AnalyticsView from './components/AnalyticsView'
import { shortenUrl } from './api'
import type { ShortenResponse } from './types'

type View = { name: 'form' } | { name: 'analytics'; code: string }

export default function App() {
  const [view, setView] = useState<View>({ name: 'form' })
  const [result, setResult] = useState<ShortenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // The error toast clears itself so it never lingers over a later success.
  useEffect(() => {
    if (!error) return
    const timer = setTimeout(() => setError(null), 5000)
    return () => clearTimeout(timer)
  }, [error])

  async function handleShorten(url: string) {
    setBusy(true)
    setError(null)
    try {
      setResult(await shortenUrl(url))
    } catch (err) {
      setError((err as Error).message)
      setResult(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-white px-4 py-16 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <main className="mx-auto max-w-2xl">
        <header className="mb-8 text-center">
          <h1 className="text-4xl font-semibold tracking-tight">Sniplink</h1>
          <p className="mt-2 text-slate-500 dark:text-slate-400">
            Shorten a link, then watch the clicks roll in.
          </p>
        </header>

        <section
          className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm
                     dark:border-slate-800 dark:bg-slate-900"
        >
          {view.name === 'form' ? (
            <>
              <ShortenForm onSubmit={handleShorten} busy={busy} />
              {result && (
                <ResultCard
                  result={result}
                  onViewAnalytics={(code) => setView({ name: 'analytics', code })}
                  onReset={() => {
                    setResult(null)
                    setError(null)
                  }}
                />
              )}
            </>
          ) : (
            <AnalyticsView code={view.code} onBack={() => setView({ name: 'form' })} />
          )}
        </section>

        {error && (
          <div
            role="alert"
            className="mt-4 rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700
                       dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
          >
            {error}
          </div>
        )}

        <footer className="mt-8 text-center text-xs text-slate-400 dark:text-slate-600">
          <a
            href="http://localhost:8080/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
            className="hover:underline"
          >
            API docs
          </a>
        </footer>
      </main>
    </div>
  )
}
