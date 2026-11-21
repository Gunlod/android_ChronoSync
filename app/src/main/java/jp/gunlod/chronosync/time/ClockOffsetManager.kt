package jp.gunlod.chronosync.time

import jp.gunlod.chronosync.data.SettingsRepository

class ClockOffsetManager(private val repository: SettingsRepository) {
    fun set(value: Int) = repository.update { it.copy(offsetMs = value.coerceIn(-9999, 9999)) }
    fun adjust(direction: Int) = repository.update {
        it.copy(offsetMs = (it.offsetMs.toLong() + direction.toLong() * it.stepMs).coerceIn(-9999, 9999).toInt())
    }
    fun step(value: Int) = repository.update { it.copy(stepMs = value) }
    fun reset() = set(0)
}
