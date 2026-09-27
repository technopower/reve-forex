package com.reve.forex.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

object PremiumProducts {
    const val MONTHLY = "reve_forex_premium_monthly"
}

class PremiumBillingManager(
    context: Context,
    private val onProducts: (List<ProductDetails>) -> Unit = {},
    private val onPurchaseState: (Boolean) -> Unit = {},
    private val onMessage: (String) -> Unit = {}
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun connect() {
        if (client.isReady) {
            queryProducts()
            queryExistingPurchases()
            return
        }

        client.startConnection(object : BillingClientStateListener {

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    onMessage(
                        "Google Play Billing unavailable: ${result.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                onMessage("Billing service disconnected.")
            }
        })
    }

    private fun queryProducts() {
        val products = listOf(
            PremiumProducts.MONTHLY
        ).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
        ) { result, details ->

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onProducts(details.productDetailsList)
            } else {
                onMessage(
                    "Could not load premium plans: ${result.debugMessage}"
                )
            }
        }
    }

    private fun queryExistingPurchases() {
        if (!client.isReady) return

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun launch(
        activity: Activity,
        product: ProductDetails
    ) {
        val offer = product.subscriptionOfferDetails?.firstOrNull()

        if (offer == null) {
            onMessage(
                "No subscription offer is available for ${product.productId}."
            )
            return
        }

        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .setOfferToken(offer.offerToken)
                .build()

        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(productParams)
                )
                .build()
        )

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage(
                "Unable to open Google Play checkout: ${result.debugMessage}"
            )
        }
    }

    fun restore() {
        queryExistingPurchases()
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {

            handlePurchases(
                purchases.orEmpty()
            )

        } else if (
            result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED
        ) {
            onMessage(
                "Purchase failed: ${result.debugMessage}"
            )
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>
    ) {
        var premium = false

        purchases.forEach { purchase ->

            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {

                premium = true

                if (!purchase.isAcknowledged) {

                    val params =
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()

                    client.acknowledgePurchase(params) { result ->

                        if (
                            result.responseCode !=
                            BillingClient.BillingResponseCode.OK
                        ) {
                            onMessage(
                                "Purchase acknowledgement failed: ${result.debugMessage}"
                            )
                        }
                    }
                }

                // Production:
                // Verify purchaseToken on your backend
                // before granting premium entitlement.
            }
        }

        onPurchaseState(premium)
    }
}