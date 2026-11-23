package com.mathyusolutions.calarmi.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.mathyusolutions.calarmi.BuildConfig
import com.mathyusolutions.calarmi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que desbloquea Calarmi Plus, en el orden del paywall. */
enum class PlusFeature(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int, val symbol: String) {
    ARCADE_CHALLENGES(R.string.plus_feature_arcade, R.string.plus_feature_arcade_desc, "gamecontroller.fill"),
    CUSTOM_TONES(R.string.plus_feature_tones, R.string.plus_feature_tones_desc, "waveform"),
    UNLIMITED_COUNTDOWNS(R.string.plus_feature_countdowns, R.string.plus_feature_countdowns_desc, "hourglass"),
    APP_ICONS(R.string.plus_feature_icons, R.string.plus_feature_icons_desc, "paintpalette.fill"),
}

/** Precios para el paywall, ya formateados por Google Play. */
data class PlusOffers(
    val yearlyPrice: String?,
    /** Periodo de prueba en formato ISO 8601 (`P1W`), si la oferta lo trae. */
    val yearlyTrialPeriod: String?,
    val lifetimePrice: String?,
)

/**
 * Calarmi Plus con Google Play Billing: suscripción anual (con semana de
 * prueba) o pago único de por vida.
 *
 * Dos reglas, iguales que en iOS:
 * - Plus solo bloquea ELEGIR algo nuevo. Lo ya configurado sigue funcionando.
 * - Nada de esto corre al sonar una alarma.
 *
 * El último estado conocido se guarda para que Plus no «desaparezca» sin red.
 */
class PlusStore(
    private val app: Application,
    storage: Context,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    enum class LoadState { IDLE, LOADING, LOADED, FAILED }

    private val prefs = storage.getSharedPreferences("plus", Context.MODE_PRIVATE)
    private val _isPlus = MutableStateFlow(prefs.getBoolean(K_PLUS, false))
    val isPlus: StateFlow<Boolean> = _isPlus.asStateFlow()

    private val _offers = MutableStateFlow<PlusOffers?>(null)
    val offers: StateFlow<PlusOffers?> = _offers.asStateFlow()

    private val _loadState = MutableStateFlow(LoadState.IDLE)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    /** Mensaje de error de la última compra, para el paywall. */
    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private var yearly: ProductDetails? = null
    private var lifetime: ProductDetails? = null

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(app)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Para pruebas en Debug: simula tener Plus. */
    var debugForcePlus: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(K_DEBUG, false)
        set(value) {
            prefs.edit().putBoolean(K_DEBUG, value).apply()
            if (BuildConfig.DEBUG) setPlus(value || prefs.getBoolean(K_PLUS_REAL, false))
        }

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        _loadState.value = LoadState.LOADING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else {
                    Log.w(TAG, "Billing no disponible: ${result.debugMessage}")
                    _loadState.value = LoadState.FAILED
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Vuelve a leer productos y compras (también es «Restaurar compras»). */
    fun refresh() {
        scope.launch {
            if (!client.isReady) {
                connect()
                return@launch
            }
            _loadState.value = LoadState.LOADING
            loadProducts()
            loadPurchases()
        }
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_YEARLY).setProductType(BillingClient.ProductType.SUBS).build(),
            ),
        ).build()
        val subs = client.queryProductDetails(params)
        val inappParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_LIFETIME).setProductType(BillingClient.ProductType.INAPP).build(),
            ),
        ).build()
        val inapp = client.queryProductDetails(inappParams)
        yearly = subs.productDetailsList?.firstOrNull()
        lifetime = inapp.productDetailsList?.firstOrNull()
        val yearlyOffers = yearly?.subscriptionOfferDetails.orEmpty()
        val trialOffer = yearlyOffers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }
        val basePhase = yearlyOffers.flatMap { it.pricingPhases.pricingPhaseList }.firstOrNull { it.priceAmountMicros > 0 }
        _offers.value = PlusOffers(
            yearlyPrice = basePhase?.formattedPrice,
            yearlyTrialPeriod = trialOffer?.pricingPhases?.pricingPhaseList?.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod,
            lifetimePrice = lifetime?.oneTimePurchaseOfferDetails?.formattedPrice,
        )
        _loadState.value = if (yearly == null && lifetime == null) LoadState.FAILED else LoadState.LOADED
    }

    private suspend fun loadPurchases() {
        val subs = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val inapp = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        val all = subs.purchasesList.orEmpty() + inapp.purchasesList.orEmpty()
        if (subs.billingResult.responseCode != BillingClient.BillingResponseCode.OK &&
            inapp.billingResult.responseCode != BillingClient.BillingResponseCode.OK
        ) return // Sin respuesta: se queda el último estado conocido.
        handle(all)
    }

    private suspend fun handle(purchases: List<Purchase>) {
        val owned = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                it.products.any { id -> id == PRODUCT_YEARLY || id == PRODUCT_LIFETIME }
        }
        for (purchase in owned.filter { !it.isAcknowledged }) {
            runCatching {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                )
            }
        }
        prefs.edit().putBoolean(K_PLUS_REAL, owned.isNotEmpty()).apply()
        setPlus(owned.isNotEmpty() || debugForcePlus)
    }

    private fun setPlus(value: Boolean) {
        _isPlus.value = value
        prefs.edit().putBoolean(K_PLUS, value).apply()
    }

    enum class Product { YEARLY, LIFETIME }

    /** Abre la hoja de compra de Google Play. `false` si los productos no cargaron. */
    fun purchase(activity: Activity, product: Product): Boolean {
        _purchaseError.value = null
        val details = (if (product == Product.YEARLY) yearly else lifetime) ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        if (product == Product.YEARLY) {
            // La oferta con prueba gratis si existe; si no, el plan base.
            val offers = details.subscriptionOfferDetails.orEmpty()
            val offer = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() ?: return false
            params.setOfferToken(offer.offerToken)
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handle(purchases.orEmpty()); loadPurchases() }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _purchaseError.value = result.debugMessage.ifBlank { app.getString(R.string.plus_purchase_failed) }
        }
    }

    companion object {
        private const val TAG = "PlusStore"
        /** Productos de Play Console. Si se cambian aquí, también allá. */
        const val PRODUCT_YEARLY = "plus_yearly"
        const val PRODUCT_LIFETIME = "plus_lifetime"
        /** Cuentas regresivas PROPIAS sin Plus. */
        const val FREE_COUNTDOWN_LIMIT = 3
        private const val K_PLUS = "isPlus"
        private const val K_PLUS_REAL = "isPlusPurchased"
        private const val K_DEBUG = "debugForcePlus"
    }
}
