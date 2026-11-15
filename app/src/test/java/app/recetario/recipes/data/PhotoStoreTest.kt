package app.recetario.recipes.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoStoreTest {
    @Test
    fun targetSize_scalesDownLandscape() {
        assertThat(PhotoStore.targetSize(4000, 3000)).isEqualTo(1600 to 1200)
    }

    @Test
    fun targetSize_scalesDownPortrait() {
        assertThat(PhotoStore.targetSize(3000, 4000)).isEqualTo(1200 to 1600)
    }

    @Test
    fun targetSize_keepsSmallImages() {
        assertThat(PhotoStore.targetSize(800, 600)).isEqualTo(800 to 600)
    }

    @Test
    fun targetSize_neverReturnsZero() {
        assertThat(PhotoStore.targetSize(10000, 1)).isEqualTo(1600 to 1)
    }
}
