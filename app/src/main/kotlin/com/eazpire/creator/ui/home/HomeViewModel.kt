package com.eazpire.creator.ui.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository,
    private val tokenStore: SecureTokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadedLocaleKey: String? = null
    private var bootstrapJob: Job? = null
    private var chipJob: Job? = null

    private fun creatorApi(): CreatorApi = CreatorApi(jwt = tokenStore.getJwt())

    fun ensureBootstrap(
        countryCode: String,
        region: String,
        reloadTrigger: Int,
        activity: ComponentActivity?,
    ) {
        val localeKey = "$countryCode|$region"
        val canSkipBootstrap = reloadTrigger == 0 &&
            loadedLocaleKey == localeKey &&
            _state.value.sectionPools.isNotEmpty() &&
            !_state.value.bootstrapInProgress
        if (canSkipBootstrap) {
            if (_state.value.promoProducts.isEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.refreshPromotionsIfEmpty(
                        creatorApi = creatorApi(),
                        countryCode = countryCode,
                    ) { transform ->
                        _state.update(transform)
                    }
                }
            }
            return
        }

        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            if (reloadTrigger > 0) {
                repository.clearMemoryCache()
                repository.clearDiskSnapshot()
                _state.value = HomeUiState(bootstrapInProgress = true)
            } else if (_state.value.sectionPools.isEmpty()) {
                repository.loadDiskSnapshot(localeKey)?.let { cached ->
                    _state.value = cached
                }
            }

            if (_state.value.sectionPools.isEmpty()) {
                _state.update {
                    it.copy(
                        bootstrapInProgress = true,
                        loadCreatorsSection = false,
                        heroImages = emptyList(),
                        promoProducts = emptyList(),
                        sectionPools = emptyMap(),
                        createScratchCatalog = emptyList(),
                        homeCreators = emptyList(),
                    )
                }
            }

            val api = creatorApi()
            repository.bootstrapInitial(
                creatorApi = api,
                countryCode = countryCode,
                region = region,
                force = reloadTrigger > 0,
                reloadTrigger = reloadTrigger,
                activity = activity,
            ) { transform ->
                _state.update(transform)
            }
            loadedLocaleKey = localeKey

            launch(Dispatchers.IO) {
                delay(2_500)
                repository.bootstrapBackground(
                    creatorApi = api,
                    countryCode = countryCode,
                    reloadTrigger = reloadTrigger,
                ) { transform ->
                    _state.update(transform)
                }
            }
        }
    }

    fun loadCategoryChip(countryCode: String, chip: String) {
        if (chip == "all") return
        chipJob?.cancel()
        chipJob = viewModelScope.launch {
            repository.loadCategoryChip(
                creatorApi = creatorApi(),
                chip = chip,
                countryCode = countryCode,
                currentPools = _state.value.sectionPools,
                onLoading = { loading ->
                    _state.update { it.copy(loadingCategories = loading) }
                },
                onUpdate = { pools ->
                    _state.update { it.copy(sectionPools = pools) }
                },
            )
        }
    }

    fun setCreatorsSort(sort: String) {
        _state.update { it.copy(homeCreatorsSort = sort) }
    }

    fun loadCreators(reloadTrigger: Int) {
        viewModelScope.launch {
            val state = _state.value
            if (!state.loadCreatorsSection) return@launch
            _state.update { it.copy(homeCreatorsLoading = it.homeCreators.isEmpty()) }
            val api = creatorApi()
            val initial = loadShopCreatorsForHome(api, state.homeCreatorsSort, HOME_INITIAL_CREATORS)
            _state.update { it.copy(homeCreators = initial, homeCreatorsLoading = false) }
            if (initial.size < 20) {
                val full = loadShopCreatorsForHome(api, state.homeCreatorsSort, 20)
                if (full.size > initial.size) {
                    _state.update { it.copy(homeCreators = full) }
                }
            }
        }
    }
}

class HomeViewModelFactory(
    private val tokenStore: SecureTokenStore,
    appContext: Context,
) : ViewModelProvider.Factory {
    private val repository = HomeRepository.get(appContext)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository, tokenStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
