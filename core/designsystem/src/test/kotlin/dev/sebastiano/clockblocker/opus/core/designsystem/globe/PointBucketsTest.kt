package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PointBucketsTest {
    @Test
    fun `sort groups points by bucket into one buffer, keeping insertion order within a bucket`() {
        val buckets = PointBuckets(3)
        buckets.reset(5)
        buckets.add(2, 1f, 10f)
        buckets.add(0, 2f, 20f)
        buckets.add(2, 3f, 30f)
        buckets.add(1, 4f, 40f)
        buckets.add(0, 5f, 50f)
        buckets.sort()

        buckets.counts.toList() shouldBe listOf(2, 1, 2)
        buckets.offsets.toList() shouldBe listOf(0, 2, 3)
        buckets.coords.copyOf(10).toList() shouldBe listOf(2f, 20f, 5f, 50f, 4f, 40f, 1f, 10f, 3f, 30f)
    }

    @Test
    fun `storage is sized to the point count, not to the point count per bucket`() {
        val buckets = PointBuckets(72)
        buckets.reset(1000)
        buckets.coords.size shouldBe 2000
        // A smaller frame reuses the same buffer and starts empty.
        buckets.reset(10)
        buckets.coords.size shouldBe 2000
        buckets.counts.sum() shouldBe 0
    }
}
