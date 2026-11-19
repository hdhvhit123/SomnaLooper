package com.somnalooper.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "SomnaLooper Pro" unlock through Google Play Billing.
 *
 * Create an in-app product with id [PRODUCT_ID] in Play Console. Outside of Play (sideloaded
 * APK) billing is simply unavailable and the app keeps working in the free tier.
 */
class BillingManager(context: Context, private val onProChanged: (Boolean) -> Unit) {
    companion object {
        const val PRODUCT_ID = "somnalooper_pro"
        private const val TAG = "Billing"
    }

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                handlePurchases(purchases)
            } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refreshPurchases()
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _available.value = true
                        queryProduct()
                        refreshPurchases()
                    } else {
                        Log.i(TAG, "Billing unavailable: ${result.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _available.value = false
                }
            })
        }.onFailure { Log.w(TAG, "startConnection failed", it) }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { _, list ->
            details = list.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun refreshPurchases() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, authoritative = true)
        }
    }

    /** @return false if the store is not reachable (e.g. sideloaded build). */
    fun launchPurchase(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun handlePurchases(purchases: List<Purchase>, authoritative: Boolean = false) {
        var owned = false
        for (p in purchases) {
            if (PRODUCT_ID !in p.products) continue
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                owned = true
                if (!p.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                    ) { }
                }
            }
        }
        if (owned || authoritative) onProChanged(owned)
    }
}
