package dev.jordond.composeresourceskit.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class PluginLogger : Disposable {
  data class Entry(
    val time: String,
    val level: Level,
    val message: String,
  ) {
    enum class Level { INFO, WARN, ERROR }
  }

  private val entries = CopyOnWriteArrayList<Entry>()
  private val listeners = CopyOnWriteArrayList<() -> Unit>()
  private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  fun info(message: String) = add(Entry.Level.INFO, message)

  fun warn(message: String) = add(Entry.Level.WARN, message)

  fun error(message: String) = add(Entry.Level.ERROR, message)

  fun getEntries(): List<Entry> = entries.toList()

  fun clear() {
    entries.clear()
    notifyListeners()
  }

  fun addListener(listener: () -> Unit) {
    listeners.add(listener)
  }

  fun removeListener(listener: () -> Unit) {
    listeners.remove(listener)
  }

  private fun add(
    level: Entry.Level,
    message: String,
  ) {
    val entry = Entry(LocalTime.now().format(formatter), level, message)
    entries.add(entry)
    if (entries.size > MAX_ENTRIES) {
      entries.subList(0, entries.size - MAX_ENTRIES).clear()
    }
    notifyListeners()
  }

  private fun notifyListeners() {
    listeners.forEach { it() }
  }

  override fun dispose() {
    entries.clear()
    listeners.clear()
  }

  companion object {
    private const val MAX_ENTRIES = 200

    fun getInstance(project: Project): PluginLogger = project.getService(PluginLogger::class.java)
  }
}
