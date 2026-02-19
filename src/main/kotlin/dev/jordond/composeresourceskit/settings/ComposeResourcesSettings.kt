package dev.jordond.composeresourceskit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(
    name = "ComposeResourcesKitSettings",
    storages = [Storage("ComposeResourcesKit.xml")]
)
class ComposeResourcesSettings : PersistentStateComponent<ComposeResourcesSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var debounceMs: Int = 2000,
        var showNotifications: Boolean = true,
        var additionalResourcePaths: MutableList<String> = mutableListOf(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    val enabled: Boolean get() = myState.enabled
    val debounceMs: Int get() = myState.debounceMs
    val showNotifications: Boolean get() = myState.showNotifications
    val additionalResourcePaths: List<String> get() = myState.additionalResourcePaths

    companion object {
        fun getInstance(project: Project): ComposeResourcesSettings {
            return project.getService(ComposeResourcesSettings::class.java)
        }
    }
}
