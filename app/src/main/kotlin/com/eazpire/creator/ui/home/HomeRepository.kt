package com.eazpire.creator.ui.home

import android.content.Context
import androidx.activity.ComponentActivity
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.perf.EazPerfTrace
import com.eazpire.creator.ui.HeroImage
import com.eazpire.creator.ui.fetchHeroImagesForHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class HomeRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val snapshotStore = HomeSnapshotStore(appContext)

    private data class CacheEntry(val products: List<ShopifyProductsApi.ProductItem>, val savedAtMs: Long) {
        fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - savedAtMs <= MEMORY_TTL_MS
    }

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    companion object {
        private const val MEMORY_TTL_MS = 10 * 60 * 1000L

        @Volatile
        private var instance: HomeRepository? = null

        fun get(context: Context): HomeRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: HomeRepository(app).also { instance = it }
            }
        }

        private fun cacheKey(sectionId: String, chipId: String, limit: Int, countryCode: String?): String =
            listOf(sectionId, chipId, limit.toString(), countryCode.orEmpty().uppercase()).joinToString("|")
    }

    suspend fun loadDiskSnapshot(localeKey: String): HomeUiState? = withContext(Dispatchers.IO) {
        val snap = snapshotStore.load() ?: return@withContext null
        if (snap.localeKey != localeKey) return@withContext null
        if (System.currentTimeMillis() - snap.savedAtMs > HomeSnapshotStore.MAX_AGE_MS) return@withContext null
        val bootstrap = runCatching { JSONObject(snap.bootstrapJson) }.getOrNull() ?: return@withContext null
        val (promos, sections) = parseHomeCarouselBootstrapResponse(bootstrap)
        if (promos.isEmpty() && sections.isEmpty()) return@withContext null
        val pools = sections.mapValues { (_, products) -> mapOf("all" to products) }
        HomeUiState(
            promoProducts = promos,
            sectionPools = pools,
            bootstrapInProgress = true,
        )
    }

    suspend fun bootstrapInitial(
        creatorApi: CreatorApi,
        countryCode: String,
        region: String,
        force: Boolean,
        reloadTrigger: Int,
        activity: ComponentActivity?,
        onUpdate: suspend ((HomeUiState) -> HomeUiState) -> Unit,
    ) = withContext(Dispatchers.Main) {
        if (reloadTrigger > 0) EazPerfTrace.resetHomeBootstrap()
        EazPerfTrace.mark(
            "home_bootstrap_start",
            mapOf("country" to countryCode, "region" to region, "reload" to reloadTrigger),
        )

        val reportedInteractive = AtomicBoolean(false)
        suspend fun maybeReportInteractive(reason: String) {
            if (!reportedInteractive.compareAndSet(false, true)) return
            EazPerfTrace.mark("home_interactive", mapOf("reason" to reason))
            EazPerfTrace.logHomeBootstrapSummary(
                if (reloadTrigger > 0) "reload_interactive" else "cold_interactive",
            )
            EazPerfTrace.logColdStartSummary()
            activity?.reportFullyDrawn()
        }

        EazPerfTrace.measureSectionSuspend("home.bootstrap.initial") {
            coroutineScope {
                val heroDeferred = async(Dispatchers.IO) {
                    EazPerfTrace.measureSectionSuspend("home.fetch.hero") {
                        EazPerfTrace.incrementCounter("home_api_calls")
                        fetchHeroImagesForHome(CreatorApi(), region)
                    }
                }
                val scratchDeferred = async(Dispatchers.IO) {
                    EazPerfTrace.measureSectionSuspend("home.fetch.scratch") {
                        EazPerfTrace.incrementCounter("home_api_calls")
                        loadCreateScratchCatalogFromWorker(creatorApi, region)
                    }
                }
                val poolsDeferred = async(Dispatchers.IO) {
                    fetchInitialPoolsBatch(creatorApi, countryCode, force)
                }

                val heroJob = launch {
                    val hero = heroDeferred.await()
                    onUpdate { it.copy(heroImages = hero) }
                    if (hero.isNotEmpty()) {
                        EazPerfTrace.mark("home_first_content", mapOf("hero_count" to hero.size))
                        maybeReportInteractive("hero")
                    }
                }
                val scratchJob = launch {
                    val scratch = scratchDeferred.await()
                    onUpdate { it.copy(createScratchCatalog = scratch) }
                }
                val poolsJob = launch {
                    val (promos, sectionMap, bootstrapJson) = poolsDeferred.await()
                    var pools = emptyMap<String, HomeCategoryPools>()
                    sectionMap.forEach { (sectionId, products) ->
                        rememberInCache(sectionId, "all", HOME_INITIAL_PRODUCTS, countryCode, products)
                        pools = pools + (sectionId to mapOf("all" to products))
                        onUpdate { state ->
                            state.copy(
                                promoProducts = if (promos.isNotEmpty()) promos else state.promoProducts,
                                sectionPools = pools,
                            )
                        }
                        if (products.isNotEmpty()) {
                            EazPerfTrace.mark(
                                "home_first_content",
                                mapOf("section" to sectionId, "count" to products.size),
                            )
                            maybeReportInteractive("section_$sectionId")
                        }
                    }
                    if (promos.isNotEmpty()) {
                        onUpdate { it.copy(promoProducts = promos) }
                        EazPerfTrace.mark("home_first_content", mapOf("promo_count" to promos.size))
                        maybeReportInteractive("promotions")
                    }
                    bootstrapJson?.let { json ->
                        snapshotStore.save("$countryCode|$region", json)
                    }
                }

                heroJob.join()
                scratchJob.join()
                poolsJob.join()
                onUpdate { it.copy(loadCreatorsSection = true, bootstrapInProgress = false) }
                EazPerfTrace.mark("home_bootstrap_end", mapOf("sections" to HOME_PRODUCT_SECTIONS.size))
            }
        }
    }

    suspend fun bootstrapBackground(
        creatorApi: CreatorApi,
        countryCode: String,
        reloadTrigger: Int,
        onUpdate: suspend ((HomeUiState) -> HomeUiState) -> Unit,
    ) = withContext(Dispatchers.IO) {
        EazPerfTrace.measureSectionSuspend("home.bootstrap.background") {
            coroutineScope {
                val sectionResults = HOME_PRODUCT_SECTIONS.map { def ->
                    async {
                        val products = carouselProducts(
                            creatorApi,
                            def.id,
                            chipId = "all",
                            limit = HOME_MAX_PRODUCTS,
                            countryCode = countryCode,
                        )
                        def.id to products
                    }
                }.awaitAll()
                val updated = sectionResults.associate { (id, products) ->
                    id to mapOf("all" to products)
                }
                onUpdate { it.copy(sectionPools = updated) }

                val promos = EazPerfTrace.measureSectionSuspend("home.fetch.promotions.full") {
                    EazPerfTrace.incrementCounter("home_api_calls")
                    loadHomePromotionsFromWorker(creatorApi, HOME_MAX_PRODUCTS, countryCode)
                }
                if (promos.isNotEmpty()) {
                    onUpdate { it.copy(promoProducts = promos) }
                }
            }
            EazPerfTrace.mark("home_background_fill_done")
        }
        EazPerfTrace.logHomeBootstrapSummary(if (reloadTrigger > 0) "reload_complete" else "cold_complete")
    }

    suspend fun loadCategoryChip(
        creatorApi: CreatorApi,
        chip: String,
        countryCode: String,
        currentPools: Map<String, HomeCategoryPools>,
        onLoading: suspend (Set<String>) -> Unit,
        onUpdate: suspend (Map<String, HomeCategoryPools>) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val defsToLoad = HOME_PRODUCT_SECTIONS.filter { def ->
            !currentPools[def.id].orEmpty().containsKey(chip)
        }
        if (defsToLoad.isEmpty()) return@withContext
        onLoading(setOf(chip))
        try {
            var pools = currentPools
            coroutineScope {
                val initial = defsToLoad.map { def ->
                    async(Dispatchers.IO) {
                        val products = carouselProducts(
                            creatorApi,
                            def.id,
                            chipId = chip,
                            limit = HOME_INITIAL_PRODUCTS,
                            countryCode = countryCode,
                        )
                        def.id to products
                    }
                }.awaitAll()
                initial.forEach { (id, products) ->
                    val chipMap = pools[id].orEmpty().toMutableMap()
                    chipMap[chip] = products
                    pools = pools + (id to chipMap)
                }
                onUpdate(pools)

                val full = defsToLoad.map { def ->
                    async(Dispatchers.IO) {
                        val products = carouselProducts(
                            creatorApi,
                            def.id,
                            chipId = chip,
                            limit = HOME_MAX_PRODUCTS,
                            countryCode = countryCode,
                        )
                        def.id to products
                    }
                }.awaitAll()
                full.forEach { (id, products) ->
                    val chipMap = pools[id].orEmpty().toMutableMap()
                    chipMap[chip] = products
                    pools = pools + (id to chipMap)
                }
                onUpdate(pools)
            }
        } finally {
            onLoading(emptySet())
        }
    }

    suspend fun carouselProducts(
        creatorApi: CreatorApi,
        sectionId: String,
        chipId: String,
        limit: Int,
        countryCode: String?,
        force: Boolean = false,
    ): List<ShopifyProductsApi.ProductItem> = withContext(Dispatchers.IO) {
        val key = cacheKey(sectionId, chipId, limit, countryCode)
        if (!force) {
            memoryCache[key]?.takeIf { it.isFresh() }?.products?.let { return@withContext it }
        }
        val products = loadHomeCarouselFromWorker(
            creatorApi,
            sectionId,
            chipId = chipId,
            limit = limit,
            countryCode = countryCode,
        )
        memoryCache[key] = CacheEntry(products, System.currentTimeMillis())
        products
    }

    private suspend fun fetchInitialPoolsBatch(
        creatorApi: CreatorApi,
        countryCode: String,
        force: Boolean,
    ): Triple<List<ShopifyProductsApi.ProductItem>, Map<String, List<ShopifyProductsApi.ProductItem>>, String?> {
        if (!force) {
            // memory warm path not batch-shaped — fall through to network
        }
        val batchResult = runCatching {
            EazPerfTrace.measureSectionSuspend("home.fetch.bootstrap_batch") {
                EazPerfTrace.incrementCounter("home_api_calls")
                creatorApi.listHomeCarouselBootstrap(
                    category = "all",
                    limit = HOME_INITIAL_PRODUCTS,
                    countryCode = countryCode,
                )
            }
        }.getOrNull()

        if (batchResult != null && batchResult.optBoolean("ok", false)) {
            val (promos, sections) = parseHomeCarouselBootstrapResponse(batchResult)
            return Triple(promos, sections, batchResult.toString())
        }

        return fetchInitialPoolsLegacy(creatorApi, countryCode)
    }

    private suspend fun fetchInitialPoolsLegacy(
        creatorApi: CreatorApi,
        countryCode: String,
    ): Triple<List<ShopifyProductsApi.ProductItem>, Map<String, List<ShopifyProductsApi.ProductItem>>, String?> =
        coroutineScope {
            val promoDeferred = async(Dispatchers.IO) {
                EazPerfTrace.measureSectionSuspend("home.fetch.promotions") {
                    EazPerfTrace.incrementCounter("home_api_calls")
                    loadHomePromotionsFromWorker(creatorApi, HOME_INITIAL_PRODUCTS, countryCode)
                }
            }
            val sectionDeferreds = HOME_PRODUCT_SECTIONS.map { def ->
                async(Dispatchers.IO) {
                    val products = EazPerfTrace.measureSectionSuspend("home.fetch.${def.id}") {
                        EazPerfTrace.incrementCounter("home_api_calls")
                        loadHomeCarouselFromWorker(
                            creatorApi,
                            def.id,
                            chipId = "all",
                            limit = HOME_INITIAL_PRODUCTS,
                            countryCode = countryCode,
                        )
                    }
                    def.id to products
                }
            }
            val promos = promoDeferred.await()
            val sections = sectionDeferreds.awaitAll().toMap()
            Triple(promos, sections, null)
        }

    private fun rememberInCache(
        sectionId: String,
        chipId: String,
        limit: Int,
        countryCode: String?,
        products: List<ShopifyProductsApi.ProductItem>,
    ) {
        memoryCache[cacheKey(sectionId, chipId, limit, countryCode)] =
            CacheEntry(products, System.currentTimeMillis())
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    suspend fun clearDiskSnapshot() {
        snapshotStore.clear()
    }
}
