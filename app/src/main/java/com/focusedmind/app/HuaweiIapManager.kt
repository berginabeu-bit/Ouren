package com.focusedmind.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfo
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Huawei IAP adapter for permanent non-consumable features.
 *
 * The app never unlocks Premium from a button click alone. It first checks the
 * AppGallery product, validates the signed purchase payload, checks product
 * identity/type/package/state and then persists the entitlement locally.
 */
class HuaweiIapManager(context: Context) {
    private val appContext = context.applicationContext
    private val client = Iap.getIapClient(appContext)
    private val pendingProducts = mutableMapOf<String, ProductInfo>()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val publicKey: String
        get() = BuildConfig.HUAWEI_IAP_PUBLIC_KEY.trim()

    fun startPurchase(
        activity: Activity,
        productId: String,
        requestCode: Int,
        onError: (Exception) -> Unit = {}
    ) {
        if (!PremiumProducts.isKnown(productId)) {
            onError(IllegalArgumentException("Unknown Premium product."))
            return
        }
        if (publicKey.isBlank()) {
            onError(IllegalStateException("Huawei IAP public key is not configured."))
            return
        }

        val infoRequest = ProductInfoReq().apply {
            priceType = IapClient.PriceType.IN_APP_NONCONSUMABLE
            productIds = arrayListOf(productId)
        }

        client.obtainProductInfo(infoRequest)
            .addOnSuccessListener { infoResult ->
                val productInfo = infoResult?.productInfoList.orEmpty()
                    .firstOrNull { it.productId == productId && it.priceType == IapClient.PriceType.IN_APP_NONCONSUMABLE }
                if (productInfo == null) {
                    onError(IllegalStateException("Premium product is not configured in AppGallery Connect."))
                    return@addOnSuccessListener
                }

                synchronized(pendingProducts) { pendingProducts[productId] = productInfo }
                prefs.edit().putString(KEY_PENDING_PRODUCT, productId).apply()

                val request = PurchaseIntentReq().apply {
                    this.productId = productId
                    priceType = IapClient.PriceType.IN_APP_NONCONSUMABLE
                }
                client.createPurchaseIntent(request)
                    .addOnSuccessListener { result ->
                        runCatching {
                            activity.startActivityForResult(result.status.resolutionForResult, requestCode)
                        }.onFailure {
                            clearPendingProduct(productId)
                            onError(it as? Exception ?: Exception(it))
                        }
                    }
                    .addOnFailureListener {
                        clearPendingProduct(productId)
                        onError(it as? Exception ?: Exception(it))
                    }
            }
            .addOnFailureListener { onError(it as? Exception ?: Exception(it)) }
    }

    /**
     * Handles the activity result. When the process was recreated by Android,
     * the expected product is recovered from persistent state.
     */
    fun handlePurchaseResult(
        resultCode: Int,
        data: Intent?,
        expectedProduct: String? = pendingProduct()
    ): Boolean {
        val expected = expectedProduct?.takeIf(PremiumProducts::isKnown)
        if (resultCode != Activity.RESULT_OK || data == null || publicKey.isBlank() || expected == null) {
            if (resultCode != Activity.RESULT_OK) clearPendingProduct(expected)
            return false
        }

        val verified = runCatching {
            val result = client.parsePurchaseResultInfoFromIntent(data)
            if (result.returnCode != OrderStatusCode.ORDER_STATE_SUCCESS) return@runCatching false

            val purchaseData = result.inAppPurchaseData ?: return@runCatching false
            val signature = result.inAppDataSignature ?: return@runCatching false
            if (!verify(purchaseData, signature)) return@runCatching false

            val parsed = InAppPurchaseData(purchaseData)
            if (!isValidNonConsumableReceipt(parsed, expected)) return@runCatching false

            val configured = synchronized(pendingProducts) { pendingProducts[expected] }
            if (configured != null && !productDetailsMatch(configured, parsed)) return@runCatching false

            PremiumAccessManager.setValidated(appContext, expected)
            true
        }.getOrDefault(false)

        // A completed Activity result is terminal, including a rejected/invalid
        // receipt. Never leave a stale product intent pointing at an old purchase.
        clearPendingProduct(expected)
        return verified
    }

    fun queryProductPrice(
        productId: String,
        onPrice: (String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        if (!PremiumProducts.isKnown(productId)) {
            onError(IllegalArgumentException("Unknown Premium product."))
            return
        }
        val request = ProductInfoReq().apply {
            priceType = IapClient.PriceType.IN_APP_NONCONSUMABLE
            productIds = arrayListOf(productId)
        }
        client.obtainProductInfo(request)
            .addOnSuccessListener { result ->
                val info = result?.productInfoList.orEmpty()
                    .firstOrNull { it.productId == productId && it.priceType == IapClient.PriceType.IN_APP_NONCONSUMABLE }
                val price = info?.price?.trim().orEmpty()
                if (price.isBlank()) onError(IllegalStateException("Huawei did not return a product price."))
                else onPrice(price)
            }
            .addOnFailureListener { onError(it as? Exception ?: Exception(it)) }
    }

    /** Restores all known permanent Premium products, including paginated results. */
    fun restore(
        onDone: (Set<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!PremiumProducts.isConfigured()) {
            onError(IllegalStateException("Huawei Premium products are not configured."))
            return
        }
        if (publicKey.isBlank()) {
            onError(IllegalStateException("Huawei IAP public key is not configured."))
            return
        }

        queryProductsForRestore(onError) { products ->
            fetchAllOwnedPurchases(products, onError) { validProducts ->
                // Only reconcile entitlements after Huawei returned a successful
                // complete owned-purchases result. If the query fails, cached
                // offline entitlements remain untouched.
                PremiumProducts.all.forEach { product ->
                    PremiumAccessManager.setValidated(appContext, product in validProducts)
                }
                onDone(validProducts)
            }
        }
    }

    private fun queryProductsForRestore(
        onError: (Exception) -> Unit,
        onProducts: (Map<String, ProductInfo>) -> Unit
    ) {
        val request = ProductInfoReq().apply {
            priceType = IapClient.PriceType.IN_APP_NONCONSUMABLE
            productIds = ArrayList(PremiumProducts.all)
        }
        client.obtainProductInfo(request)
            .addOnSuccessListener { result ->
                val products = result?.productInfoList.orEmpty()
                    .filter { it.priceType == IapClient.PriceType.IN_APP_NONCONSUMABLE }
                    .associateBy { it.productId }
                onProducts(products)
            }
            .addOnFailureListener { onError(it as? Exception ?: Exception(it)) }
    }

    private fun fetchAllOwnedPurchases(
        products: Map<String, ProductInfo>,
        onError: (Exception) -> Unit,
        onComplete: (Set<String>) -> Unit
    ) {
        val valid = mutableSetOf<String>()

        fun requestPage(token: String?) {
            val request = OwnedPurchasesReq().apply {
                priceType = IapClient.PriceType.IN_APP_NONCONSUMABLE
                if (!token.isNullOrBlank()) continuationToken = token
            }
            client.obtainOwnedPurchases(request)
                .addOnSuccessListener { result ->
                    val dataList = result.inAppPurchaseDataList.orEmpty()
                    val signatureList = result.inAppSignature.orEmpty()
                    dataList.forEachIndexed { index, data ->
                        val signature = signatureList.getOrNull(index) ?: return@forEachIndexed
                        if (!verify(data, signature)) return@forEachIndexed
                        val parsed = runCatching { InAppPurchaseData(data) }.getOrNull() ?: return@forEachIndexed
                        val product = parsed.productId
                        if (!PremiumProducts.isKnown(product)) return@forEachIndexed
                        if (!isValidNonConsumableReceipt(parsed, product)) return@forEachIndexed
                        val configured = products[product] ?: return@forEachIndexed
                        if (productDetailsMatch(configured, parsed)) valid += product
                    }

                    val next = result.continuationToken?.trim().orEmpty()
                    if (next.isNotBlank()) requestPage(next) else onComplete(valid)
                }
                .addOnFailureListener { onError(it as? Exception ?: Exception(it)) }
        }

        requestPage(null)
    }

    private fun isValidNonConsumableReceipt(data: InAppPurchaseData, expectedProduct: String): Boolean =
        data.productId == expectedProduct &&
            data.packageName == appContext.packageName &&
            data.purchaseState == 0 &&
            data.kind == 1 &&
            data.purchaseToken.isNotBlank()

    /** Huawei ProductInfo.microsPrice is price * 1,000,000; InAppPurchaseData.price is price * 100. */
    private fun productDetailsMatch(configured: ProductInfo, receipt: InAppPurchaseData): Boolean {
        val currencyMatches = configured.currency.isBlank() || receipt.currency.isBlank() || configured.currency == receipt.currency
        val priceMatches = configured.microsPrice <= 0L || receipt.price <= 0L || configured.microsPrice == receipt.price * 10_000L
        return currencyMatches && priceMatches
    }

    private fun verify(data: String, signatureBase64: String): Boolean = runCatching {
        val keyBytes = Base64.decode(
            publicKey
                .removePrefix("-----BEGIN PUBLIC KEY-----")
                .removeSuffix("-----END PUBLIC KEY-----")
                .replace("\\s".toRegex(), ""),
            Base64.DEFAULT
        )
        val publicKeySpec = X509EncodedKeySpec(keyBytes)
        val key = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec)
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(key)
            update(data.toByteArray(Charsets.UTF_8))
        }
        verifier.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
    }.getOrDefault(false)

    fun pendingProductId(): String? = pendingProduct()

    private fun pendingProduct(): String? = prefs.getString(KEY_PENDING_PRODUCT, null)

    private fun clearPendingProduct(productId: String?) {
        if (productId.isNullOrBlank()) return
        synchronized(pendingProducts) { pendingProducts.remove(productId) }
        if (prefs.getString(KEY_PENDING_PRODUCT, null) == productId) {
            prefs.edit().remove(KEY_PENDING_PRODUCT).apply()
        }
    }

    companion object {
        private const val PREFS = "focused_mind_iap"
        private const val KEY_PENDING_PRODUCT = "pending_product"
    }
}

object PremiumProducts {
    const val IMPORTANT_EVENT = "focused_mind_important_event"
    const val ACADEMIC_FOCUS = "focused_mind_academic_focus"

    const val IMPORTANT_EVENT_PRICE_EUR = 7
    const val ACADEMIC_FOCUS_PRICE_EUR = 10

    val all: List<String> = listOf(IMPORTANT_EVENT, ACADEMIC_FOCUS)

    fun isKnown(product: String): Boolean = product in all
    fun isConfigured(): Boolean = all.size == 2

    fun productFor(categoryId: Int): String? = when (categoryId) {
        4 -> IMPORTANT_EVENT
        5 -> ACADEMIC_FOCUS
        else -> null
    }
}
