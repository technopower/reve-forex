# V12 Setup

1. Copy `.env.example` to `.env`.
2. Add a real market-data API key for live mode.
3. Set symbols/timeframes.
4. Install requirements.
5. Run `run_windows.bat`.
6. Check:
   - GET /health
   - GET /scanner/status
   - POST /scanner/run-now
   - GET /signals

Demo mode:
- If no provider key exists and USE_DEMO_FALLBACK=true, synthetic candles are used.
- Demo signals are marked `source=DEMO`.
- Do not publish DEMO signals to customers.

Live mode:
- Set TWELVE_DATA_API_KEY.
- Verify symbol availability and provider terms/limits.
- Monitor API errors and data freshness.

Production:
- Replace SQLite with PostgreSQL/Firestore.
- Use a dedicated worker/queue for scanning.
- Add authentication to admin endpoints.
- Add structured logs, metrics and alerting.
- Add provider retry/backoff.
- Validate candle timestamps and stale data.
- Add a market-session/calendar layer where relevant.
