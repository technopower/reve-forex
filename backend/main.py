import asyncio
import os
import sqlite3
from contextlib import asynccontextmanager
from datetime import datetime, timezone, timedelta
from typing import Optional

import httpx
import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException

load_dotenv()

DB_PATH = os.getenv("DATABASE_PATH", "reve_forex_v12.db")
TWELVE_KEY = os.getenv("TWELVE_DATA_API_KEY", "")
TWELVE_BASE = os.getenv("TWELVE_DATA_BASE_URL", "https://api.twelvedata.com")
INTERVAL = int(os.getenv("SCANNER_INTERVAL_SECONDS", "300"))
SYMBOLS = [x.strip() for x in os.getenv("SCANNER_SYMBOLS", "XAU/USD,EUR/USD,GBP/USD,USD/JPY").split(",") if x.strip()]
TIMEFRAMES = [x.strip() for x in os.getenv("SCANNER_TIMEFRAMES", "15min,1h").split(",") if x.strip()]
MIN_SCORE = int(os.getenv("MIN_SCORE_FOR_SIGNAL", "3"))
DEMO = os.getenv("USE_DEMO_FALLBACK", "true").lower() == "true"

scanner_task: Optional[asyncio.Task] = None
scanner_state = {
    "running": False,
    "last_run": None,
    "last_result": None,
    "provider": "LIVE" if TWELVE_KEY else "DEMO"
}


def db():
    return sqlite3.connect(DB_PATH)


def init_db():
    conn = db()
    conn.execute("""
    CREATE TABLE IF NOT EXISTS signals (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        symbol TEXT NOT NULL,
        timeframe TEXT NOT NULL,
        direction TEXT NOT NULL,
        score INTEGER NOT NULL,
        entry REAL,
        stop_loss REAL,
        take_profit REAL,
        reasons TEXT,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'ACTIVE',
        source TEXT NOT NULL
    )
    """)
    conn.commit()
    conn.close()


def ema(series, period):
    return series.ewm(span=period, adjust=False).mean()


def rsi(series, period=14):
    delta = series.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    ag = gain.ewm(alpha=1/period, adjust=False).mean()
    al = loss.ewm(alpha=1/period, adjust=False).mean()
    rs = ag / al.replace(0, float("nan"))
    return (100 - 100 / (1 + rs)).fillna(50)


def macd(series):
    line = ema(series, 12) - ema(series, 26)
    sig = ema(line, 9)
    return line, sig, line - sig


def bollinger(series, period=20):
    mid = series.rolling(period).mean()
    std = series.rolling(period).std()
    return mid, mid + 2*std, mid - 2*std


def atr(df, period=14):
    prev = df["close"].shift(1)
    tr = pd.concat([
        df["high"] - df["low"],
        (df["high"] - prev).abs(),
        (df["low"] - prev).abs()
    ], axis=1).max(axis=1)
    return tr.ewm(alpha=1/period, adjust=False).mean()


def demo_candles(n=100):
    # Clearly synthetic deterministic-ish data for development only.
    import math
    rows = []
    base = 100.0
    for i in range(n):
        drift = 0.03 * math.sin(i / 8)
        close = base + i * 0.02 + drift
        rows.append({
            "datetime": f"demo-{i}",
            "open": close - 0.10,
            "high": close + 0.20,
            "low": close - 0.20,
            "close": close,
            "volume": 0
        })
    return rows


async def fetch_candles(symbol, interval):
    if not TWELVE_KEY:
        if DEMO:
            return demo_candles()
        raise RuntimeError("TWELVE_DATA_API_KEY is not configured")

    url = f"{TWELVE_BASE}/time_series"
    params = {
        "symbol": symbol,
        "interval": interval,
        "outputsize": 100,
        "apikey": TWELVE_KEY,
        "format": "JSON"
    }

    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.get(url, params=params)
        response.raise_for_status()
        data = response.json()

    if "values" not in data:
        raise RuntimeError(str(data)[:500])

    return list(reversed(data["values"]))


def analyze(rows):
    df = pd.DataFrame(rows)
    for col in ["open", "high", "low", "close"]:
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna().reset_index(drop=True)

    if len(df) < 50:
        raise RuntimeError("Not enough candles")

    close = df["close"]
    df["ema20"] = ema(close, 20)
    df["ema50"] = ema(close, 50)
    df["rsi"] = rsi(close)
    df["macd"], df["macd_signal"], df["hist"] = macd(close)
    df["bbmid"], df["bbup"], df["bblow"] = bollinger(close)
    df["atr"] = atr(df)

    x = df.iloc[-1]
    score = 0
    reasons = []

    if x.ema20 > x.ema50:
        score += 1
        reasons.append("EMA20 above EMA50")
    elif x.ema20 < x.ema50:
        score -= 1
        reasons.append("EMA20 below EMA50")

    if 50 <= x.rsi <= 70:
        score += 1
        reasons.append("RSI bullish zone")
    elif 30 <= x.rsi < 50:
        score -= 1
        reasons.append("RSI bearish zone")

    if x["hist"] > 0:
        score += 1
        reasons.append("MACD histogram positive")
    elif x["hist"] < 0:
        score -= 1
        reasons.append("MACD histogram negative")

    if x.close > x.bbmid:
        score += 1
        reasons.append("Price above Bollinger midpoint")
    elif x.close < x.bbmid:
        score -= 1
        reasons.append("Price below Bollinger midpoint")

    direction = "BUY" if score >= MIN_SCORE else "SELL" if score <= -MIN_SCORE else "NEUTRAL"

    entry = float(x.close)
    atr_value = float(x.atr)
    sl = entry - atr_value if direction == "BUY" else entry + atr_value if direction == "SELL" else None
    tp = entry + 2*atr_value if direction == "BUY" else entry - 2*atr_value if direction == "SELL" else None

    return {
        "direction": direction,
        "score": int(score),
        "entry": entry,
        "stop_loss": sl,
        "take_profit": tp,
        "reasons": reasons
    }


def duplicate_exists(symbol, timeframe, direction, now):
    conn = db()
    row = conn.execute("""
        SELECT id FROM signals
        WHERE symbol=? AND timeframe=? AND direction=? AND status='ACTIVE'
          AND expires_at>?
        LIMIT 1
    """, (symbol, timeframe, direction, now)).fetchone()
    conn.close()
    return row is not None


def save_signal(symbol, timeframe, result, source):
    now = datetime.now(timezone.utc)
    expires = now + timedelta(minutes=30 if timeframe == "15min" else 120)
    conn = db()
    conn.execute("""
        INSERT INTO signals
        (symbol,timeframe,direction,score,entry,stop_loss,take_profit,reasons,created_at,expires_at,status,source)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """, (
        symbol, timeframe, result["direction"], result["score"],
        result["entry"], result["stop_loss"], result["take_profit"],
        " | ".join(result["reasons"]), now.isoformat(), expires.isoformat(),
        "ACTIVE", source
    ))
    conn.commit()
    conn.close()


def expire_signals():
    now = datetime.now(timezone.utc).isoformat()
    conn = db()
    conn.execute(
        "UPDATE signals SET status='EXPIRED' WHERE status='ACTIVE' AND expires_at<=?",
        (now,)
    )
    conn.commit()
    conn.close()


async def fetch_quote(symbol):
    if not TWELVE_KEY:
        if DEMO:
            demo_prices = {"XAU/USD": 2650.0, "EUR/USD": 1.1650, "GBP/USD": 1.3450, "USD/JPY": 148.50}
            return {"symbol": symbol, "price": demo_prices.get(symbol, 100.0), "change": 0.0,
                    "percent_change": 0.0, "datetime": datetime.now(timezone.utc).isoformat(), "source": "DEMO"}
        raise RuntimeError("TWELVE_DATA_API_KEY is not configured")

    url = f"{TWELVE_BASE}/quote"
    params = {"symbol": symbol, "apikey": TWELVE_KEY, "format": "JSON"}
    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.get(url, params=params)
        response.raise_for_status()
        data = response.json()
    if "close" not in data:
        raise RuntimeError(str(data)[:500])

    return {
        "symbol": symbol,
        "price": float(data["close"]),
        "change": float(data.get("change") or 0),
        "percent_change": float(data.get("percent_change") or 0),
        "datetime": data.get("datetime"),
        "source": "LIVE"}


async def scan_once():
    expire_signals()
    created = []
    errors = []

    for symbol in SYMBOLS:
        for timeframe in TIMEFRAMES:
            try:
                candles = await fetch_candles(symbol, timeframe)
                result = analyze(candles)

                if result["direction"] == "NEUTRAL":
                    continue

                now = datetime.now(timezone.utc).isoformat()
                if duplicate_exists(symbol, timeframe, result["direction"], now):
                    continue

                source = "LIVE" if TWELVE_KEY else "DEMO"
                save_signal(symbol, timeframe, result, source)
                created.append({
                    "symbol": symbol,
                    "timeframe": timeframe,
                    **result,
                    "source": source
                })

                # V10 FCM hook:
                # Call the authenticated notification service here after
                # applying your server-side FREE/PREMIUM entitlement rules.

            except Exception as exc:
                errors.append({
                    "symbol": symbol,
                    "timeframe": timeframe,
                    "error": str(exc)
                })

    scanner_state["last_run"] = datetime.now(timezone.utc).isoformat()
    scanner_state["last_result"] = {
        "created": created,
        "errors": errors
    }
    return scanner_state["last_result"]


async def scanner_loop():
    scanner_state["running"] = True
    try:
        while True:
            await scan_once()
            await asyncio.sleep(max(INTERVAL, 60))
    except asyncio.CancelledError:
        pass
    finally:
        scanner_state["running"] = False


@asynccontextmanager
async def lifespan(app: FastAPI):
    global scanner_task
    init_db()
    scanner_task = asyncio.create_task(scanner_loop())
    yield
    if scanner_task:
        scanner_task.cancel()
        try:
            await scanner_task
        except asyncio.CancelledError:
            pass


app = FastAPI(title="REVE FOREX V12", version="12.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": "12.0.0",
        "scanner_running": scanner_state["running"],
        "provider": scanner_state["provider"],
        "symbols": SYMBOLS,
        "timeframes": TIMEFRAMES
    }


@app.get("/markets")
async def markets():
    results, errors = [], []
    for symbol in SYMBOLS:
        try:
            results.append(await fetch_quote(symbol))
        except Exception as exc:
            errors.append({"symbol": symbol, "error": str(exc)})
    return {"success": True, "provider": "LIVE" if TWELVE_KEY else "DEMO",
            "markets": results, "errors": errors,
            "updated_at": datetime.now(timezone.utc).isoformat()}


@app.get("/scanner/status")
def scanner_status():
    return scanner_state


@app.post("/scanner/run-now")
async def run_now():
    return {"success": True, "result": await scan_once()}


@app.get("/signals")
def list_signals(limit: int = 100):
    conn = db()
    rows = conn.execute("""
        SELECT id,symbol,timeframe,direction,score,entry,stop_loss,take_profit,
               reasons,created_at,expires_at,status,source
        FROM signals ORDER BY id DESC LIMIT ?
    """, (min(max(limit, 1), 500),)).fetchall()
    conn.close()

    columns = [
        "id","symbol","timeframe","direction","score","entry","stop_loss",
        "take_profit","reasons","created_at","expires_at","status","source"
    ]
    return {"success": True, "signals": [dict(zip(columns, r)) for r in rows]}


@app.post("/signals/{signal_id}/close")
def close_signal(signal_id: int):
    conn = db()
    cur = conn.execute(
        "UPDATE signals SET status='CLOSED' WHERE id=?",
        (signal_id,)
    )
    conn.commit()
    conn.close()
    if cur.rowcount == 0:
        raise HTTPException(status_code=404, detail="Signal not found")
    return {"success": True, "signal_id": signal_id, "status": "CLOSED"}
