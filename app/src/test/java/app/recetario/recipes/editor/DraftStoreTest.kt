package app.recetario.recipes.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun corruptDraft_isRenamedToBad_notDeleted() {
        val dir = File(tmp.root, "drafts").also { it.mkdirs() }
        File(dir, "new.json").writeText("{esto no es json")
        val store = DraftStore(dir)
        assertThat(store.load("new")).isNull()
        assertThat(File(dir, "new.json").exists()).isFalse()
        assertThat(File(dir, "new.bad").readText()).isEqualTo("{esto no es json")

        File(dir, "new.json").writeText("{otro roto")
        assertThat(store.load("new")).isNull()
        assertThat(File(dir, "new.bad").readText()).isEqualTo("{otro roto")
    }

    @Test
    fun saveAndLoad_roundTrips() {
        val store = DraftStore(File(tmp.root, "drafts"))
        val state = EditorState(recipeId = "r1", title = "Sopa")
        store.save("r1", state)
        assertThat(store.load("r1")).isEqualTo(state)
    }
}
