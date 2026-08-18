package com.potheride.app.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the search box shows at any moment. */
data class PlaceSearchState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false
)

/**
 * Debounces the passenger's typing before it reaches [NominatimClient].
 *
 * [NominatimClient] already rate-limits and caches, but neither stops a fast typist from
 * *queueing* five requests that then fire one per second for the next five seconds,
 * showing stale results the whole time. Debouncing means only the query the user has
 * actually paused on is ever sent.
 *
 * [debounceMillis] defaults to 400 ms — comfortably longer than the gap between
 * keystrokes in normal typing, short enough that the results still feel responsive.
 */
class PlaceSearchController(
    private val client: NominatimClient,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 400L
) {
    private val _state = MutableStateFlow(PlaceSearchState())
    val state: StateFlow<PlaceSearchState> = _state.asStateFlow()

    private var pendingSearch: Job? = null

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)
        pendingSearch?.cancel()

        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false)
            return
        }

        _state.value = _state.value.copy(searching = true)
        pendingSearch = scope.launch {
            delay(debounceMillis)
            val results = client.search(query)
            // The query may have changed again while this search was in flight; a result
            // for "Mirpu" arriving after the box now reads "Mirpur-10" must not overwrite
            // the newer (still pending) search's eventual results with a stale answer.
            if (_state.value.query == query) {
                _state.value = _state.value.copy(results = results, searching = false)
            }
        }
    }
}
