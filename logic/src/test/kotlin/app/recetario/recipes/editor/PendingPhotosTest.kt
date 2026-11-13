package app.recetario.recipes.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PendingPhotosTest {
    private val camera = PendingPhoto(source = "/cache/camera/a.jpg", tempFile = true, target = "step:s1")
    private val gallery = PendingPhoto(source = "content://media/picker/0/1?x=1&y=2", tempFile = false, target = "cover")

    @Test
    fun resultWhileEditorNotReady_isHeld_andDeliveredOnceWhenReady() {
        val pending = PendingPhotos() + camera
        val (now, rest) = pending.release(editorReady = false, draftPending = false)
        assertThat(now).isEmpty()
        assertThat(rest).isEqualTo(pending)

        val (later, left) = rest.release(editorReady = true, draftPending = false)
        assertThat(later).containsExactly(camera)
        assertThat(left.items).isEmpty()
        assertThat(left.release(editorReady = true, draftPending = false).first).isEmpty()
    }

    @Test
    fun resultWhileDraftUndecided_isHeldUntilTheDraftIsResolved() {
        val pending = PendingPhotos() + camera + gallery
        assertThat(pending.release(editorReady = true, draftPending = true).first).isEmpty()
        assertThat(pending.release(editorReady = true, draftPending = false).first).containsExactly(camera, gallery).inOrder()
    }

    @Test
    fun tempFiles_areOnlyTheCameraCaptures() {
        assertThat((PendingPhotos() + camera + gallery).tempFiles).containsExactly("/cache/camera/a.jpg")
    }

    @Test
    fun encodeDecode_roundTrip() {
        val pending = PendingPhotos() + camera + gallery
        val encoded = pending.encode()
        assertThat(encoded).isInstanceOf(ArrayList::class.java)
        assertThat(PendingPhotos.decode(encoded)).isEqualTo(pending)
    }

    @Test
    fun decode_skipsMalformedEntries() {
        val encoded = listOf("basura", "X\ncover\n/a.jpg") + (PendingPhotos() + camera).encode()
        assertThat(PendingPhotos.decode(encoded).items).containsExactly(camera)
    }
}
