package com.reve.forex
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.reve.forex.api.ApiClient
import com.reve.forex.api.MarketQuote
import com.reve.forex.api.Signal
import com.reve.forex.billing.PremiumBillingManager
import kotlinx.coroutines.launch
import java.util.Locale

private fun formatNumber(value: Double): String {
    return String.format(Locale.US, "%.5f", value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForexApp() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("REVE FOREX") }) },
        bottomBar = {
            NavigationBar {
                listOf("Markets", "Signals", "Premium", "Settings").forEachIndexed { index, name ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(name.take(1)) },
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> Markets()
                1 -> Signals()
                2 -> Premium()
                else -> Settings()
            }
        }
    }
}

@Composable
fun Markets() {
    val scope = rememberCoroutineScope()
    var markets by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try { markets = ApiClient.api.markets().markets }
            catch (_: Exception) { error = "Backend unavailable. Start the V12 API on port 8000." }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Live Markets", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { refresh() }) { Text("Refresh") }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn { items(markets) { MarketCard(it) } }
    }
}

@Composable
private fun MarketCard(market: MarketQuote) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(market.symbol, style = MaterialTheme.typography.titleMedium)
                Text(market.source, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatNumber(market.price))
                Text(String.format(Locale.US, "%+.2f%%", market.percent_change))
            }
        }
    }
}

@Composable
fun Signals() {
    var signals by remember { mutableStateOf<List<Signal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { signals = ApiClient.api.signals().signals }
        catch (_: Exception) { error = "Unable to load signals. Check the V12 backend." }
        finally { loading = false }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Signal Center", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Automatic scanner signals appear here.")
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn {
            items(signals) { signal ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${signal.symbol} • ${signal.timeframe}")
                        Text("${signal.direction}  |  Score ${signal.score}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Entry: ${signal.entry?.let(::formatNumber) ?: "-"}")
                        Text("SL: ${signal.stop_loss?.let(::formatNumber) ?: "-"}")
                        Text("TP: ${signal.take_profit?.let(::formatNumber) ?: "-"}")
                        Text("Source: ${signal.source}")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Signals are informational and do not guarantee results.")
    }
}

@Composable
fun Premium() {
    val context = LocalContext.current
    val activity = context as? Activity
    var products by remember { mutableStateOf<List<ProductDetails>>(emptyList()) }
    var premium by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val billing = remember {
        PremiumBillingManager(
            context = context,
            onProducts = { products = it },
            onPurchaseState = { premium = it },
            onMessage = { message = it }
        )
    }

    LaunchedEffect(billing) { billing.connect() }

    Column(
        modifier = Modifier.padding(all = 16.dp)
    ) {
        Text(
            "Premium Features",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Access additional market-analysis features with a subscription."
        )

        Spacer(Modifier.height(8.dp))

        if (premium) {
            Text("Premium subscription active.")
        } else if (products.isEmpty()) {
            Text("Loading subscription plans…")
        }

        products.forEach { product ->
            val offer = product.subscriptionOfferDetails?.firstOrNull()
            val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            Button(
                onClick = { activity?.let { billing.launch(it, product) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (price != null) "${product.name} • $price" else product.name)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = { billing.restore() }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore Purchases")
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun Settings() {
    val scope = rememberCoroutineScope()

    var backendStatus by remember {
        mutableStateOf("Checking...")
    }

    var provider by remember {
        mutableStateOf("LIVE")
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    fun checkBackend() {
        scope.launch {
            backendStatus = "Checking..."
            error = null

            try {
                ApiClient.api.health()
                backendStatus = "Connected"
                provider = "LIVE"
            } catch (_: Exception) {
                backendStatus = "Disconnected"
                error = "Backend unavailable. Make sure the V12 API is running."
            }
        }
    }

    LaunchedEffect(Unit) {
        checkBackend()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "API / Provider",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Provider")

                Text(
                    provider,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(12.dp))

                Text("Backend Status")

                Text(
                    backendStatus,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(12.dp))

                Text("API")
                Text("V12 Backend • Port 8000")

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = {
                        checkBackend()
                    }
                ) {
                    Text("Check Connection")
                }
                Spacer(Modifier.height(24.dp))

                Text(
                    "Risk Disclaimer",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "REVE FOREX provides market information and analytical signals "
                                + "for informational and educational purposes only. The information "
                                + "provided by the app does not constitute financial, investment, "
                                + "trading, or other professional advice. Trading foreign exchange "
                                + "and other financial instruments involves substantial risk, and "
                                + "you may lose some or all of your invested capital. Past performance "
                                + "or signal accuracy does not guarantee future results. Users are "
                                + "responsible for their own trading decisions.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Data",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Markets")
                Text("LIVE")

                Spacer(Modifier.height(10.dp))

                Text("Signals")
                Text("LIVE")
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))

            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}