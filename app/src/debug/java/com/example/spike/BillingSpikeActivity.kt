// Spike 005: play-billing-model — hands-on half.
// Proves BillingClient can be built, connects to Play on a Play-Store-image
// emulator, and queries product details cleanly with nothing configured yet.
// Launch:  adb shell am start -n com.aistudio.gordian.ovthnk/com.example.spike.BillingSpikeActivity
// Observe: adb logcat -s BillingSpike

package com.example.spike

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams

private const val TAG = "BillingSpike"

class BillingSpikeActivity : Activity() {
    private lateinit var billing: BillingClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SPIKE-START building BillingClient")
        billing = BillingClient.newBuilder(this)
            .setListener { result, _ ->
                Log.i(TAG, "PURCHASES-UPDATED code=${result.responseCode}")
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                Log.i(TAG, "SETUP-FINISHED code=${result.responseCode} msg='${result.debugMessage}'")
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                } else {
                    Log.i(TAG, "SPIKE-DONE verdict=connect-only (code ${result.responseCode})")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.i(TAG, "SERVICE-DISCONNECTED")
            }
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("lifetime")
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            Log.i(
                TAG,
                "PRODUCT-QUERY code=${result.responseCode} " +
                    "products=${details.productDetailsList.size} " +
                    "unfetched=${details.unfetchedProductList.size}"
            )
            Log.i(TAG, "SPIKE-DONE verdict=full-path")
        }
    }
}
