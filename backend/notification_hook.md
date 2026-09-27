# V10 Notification Hook

When `scan_once()` creates a signal, production code should call the V10 notification service.

Recommended flow:

1. Save signal.
2. Determine signal visibility (FREE or PREMIUM) using server-side business rules.
3. Send an authenticated request to the notification service.
4. Notification payload:
   - type = FOREX_SIGNAL
   - signal_id
   - symbol
   - direction
   - timeframe
5. Record notification delivery results.

Do not send notifications from Android itself based on locally generated signals.
