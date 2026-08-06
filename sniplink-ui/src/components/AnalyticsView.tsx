import { useEffect, useState } from 'react'
import { fetchAnalytics } from '../api'
import type { AnalyticsResponse } from '../types'

interface Props {
  code: string
  onBack: () => void
}

function formatTime(iso: string) {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

function truncate(value: string | null, max: number) {
  if (!value) return '—'
  return value.length <= max ? value : `${value.slice(0, max)}…`
}

export default function AnalyticsView({ code, onBack }: Props) {
  const [data, setData] = useState<AnalyticsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    fetchAnalytics(code)
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [code])

  return (
    <div>
      <button
        onClick={onBack}
        className="text-sm font-medium text-slate-500 hover:underline dark:text-slate-400"
      >
        ← Back
      </button>

      {loading && <p className="mt-6 text-slate-500 dark:text-slate-400">Loading analytics…</p>}

      {error && (
        <p className="mt-6 rounded-lg border border-red-300 bg-red-50 p-4 text-red-700
                      dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">
          {error}
        </p>
      )}

      {data && !loading && (
        <>
          <div className="mt-4">
            <h2 className="font-mono text-2xl text-slate-900 dark:text-slate-100">{data.shortCode}</h2>
            <a
              href={data.originalUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-1 block truncate text-sm text-slate-500 hover:underline dark:text-slate-400"
              title={data.originalUrl}
            >
              {data.originalUrl}
            </a>
          </div>

          <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-5
                          dark:border-slate-800 dark:bg-slate-900/60">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Total clicks
            </p>
            <p className="mt-1 text-5xl font-semibold text-slate-900 tabular-nums dark:text-slate-100">
              {data.totalClicks}
            </p>
            <p className="mt-2 text-xs text-slate-400 dark:text-slate-500">
              Created {formatTime(data.createdAt)}
            </p>
          </div>

          <h3 className="mt-8 text-sm font-medium text-slate-700 dark:text-slate-300">Recent clicks</h3>

          {data.recentClicks.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              No clicks recorded yet. Clicks are logged asynchronously, so a very recent one may take
              a moment to appear.
            </p>
          ) : (
            <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500
                                  dark:bg-slate-900 dark:text-slate-400">
                  <tr>
                    <th className="px-4 py-2 font-medium">IP</th>
                    <th className="px-4 py-2 font-medium">User agent</th>
                    <th className="px-4 py-2 font-medium">Referrer</th>
                    <th className="px-4 py-2 font-medium">When</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                  {data.recentClicks.map((click, index) => (
                    <tr key={index} className="text-slate-700 dark:text-slate-300">
                      <td className="whitespace-nowrap px-4 py-2 font-mono text-xs">{click.ipAddress}</td>
                      <td className="px-4 py-2" title={click.userAgent ?? ''}>
                        {truncate(click.userAgent, 40)}
                      </td>
                      <td className="px-4 py-2" title={click.referrer ?? ''}>
                        {truncate(click.referrer, 28)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2 text-xs">{formatTime(click.clickedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
