package app.bp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bp.data.Reading
import app.bp.data.ReadingRepository
import app.bp.data.last7Days
import app.bp.domain.BpParser
import app.bp.domain.BpParser.ParseResult
import app.bp.domain.BpParser.ParseResult.Parsed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the two screens is visible. */
enum class Screen { Entry, Review }

/** Everything the entry screen renders, derived from the raw digits. */
data class EntryUi(
    val digits: String,
    /** True after "Edit" was tapped: confirm bar hidden until the digits change. */
    val editing: Boolean,
    val result: ParseResult,
)

class BpViewModel(private val repository: ReadingRepository) : ViewModel() {

    private val _screen = MutableStateFlow(Screen.Entry)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _digits = MutableStateFlow("")
    private val _editing = MutableStateFlow(false)

    val entryUi: StateFlow<EntryUi> =
        combine(_digits, _editing) { digits, editing ->
            EntryUi(digits, editing, BpParser.parse(digits))
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            EntryUi("", false, ParseResult.Waiting("")),
        )

    /** All readings, newest first (drives the list view). */
    val allReadings: StateFlow<List<Reading>> =
        repository.all.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /** Last 7 calendar days, oldest first (drives the chart). */
    val chartReadings: StateFlow<List<Reading>> =
        repository.all
            .map { it.last7Days() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

    /**
     * Cap well above the 9-digit maximum so the UI can show "too long"
     * instead of silently swallowing keys.
     */
    private val maxDigits = 12

    fun digit(d: Char) {
        if (_digits.value.length >= maxDigits) return
        _digits.update { it + d }
        _editing.value = false
    }

    fun backspace() {
        _digits.update { if (it.isNotEmpty()) it.dropLast(1) else it }
        _editing.value = false
    }

    /** Discard the in-progress entry. */
    fun cancelEntry() {
        _digits.value = ""
        _editing.value = false
    }

    /** Keep the digits, hide the confirm bar so the user can fix them. */
    fun startEditing() {
        _editing.value = true
    }

    /** Save the parsed reading, then move on to the review screen. */
    fun confirmEntry() {
        val parsed = entryUi.value.result as? Parsed ?: return
        if (entryUi.value.editing) return
        viewModelScope.launch {
            repository.save(parsed.sys, parsed.dia, parsed.hr)
            cancelEntry()
            _screen.value = Screen.Review
        }
    }

    fun newEntry() {
        cancelEntry()
        _screen.value = Screen.Entry
    }

    fun deleteReading(reading: Reading) {
        viewModelScope.launch { repository.delete(reading) }
    }

    fun restoreReading(reading: Reading) {
        viewModelScope.launch { repository.restore(reading) }
    }
}
