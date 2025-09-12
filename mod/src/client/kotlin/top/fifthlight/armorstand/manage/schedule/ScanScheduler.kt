package top.fifthlight.armorstand.manage.schedule

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.time.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.measureTime

class ScanScheduler(
    private val onScan: suspend () -> Unit,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceMillis: Long = 100,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ScanScheduler::class.java)
    }

    // 0 表示未计划；否则为下一次扫描的时间戳
    private val nextDeadline = AtomicLong(0L)

    private val _lastScanTime = MutableStateFlow<Instant?>(null)
    val lastScanTime = _lastScanTime.asStateFlow()

    // 信号通道：新的计划到来时，只需唤醒等待协程重新计算等待时长
    // 使用 CONFLATED，避免重复堆积
    private val signal = Channel<Unit>(capacity = Channel.CONFLATED)

    private val runner: Job
    private var stopped = false

    init {
        runner = scope.launch {
            loop()
        }
    }

    fun stop() = synchronized(this) {
        if (stopped) {
            return@synchronized
        }
        stopped = true
        runner.cancel()
        signal.close()
    }

    /**
     * 请求一次扫描：
     * - 如果是立即扫描，将下个扫描时间设置为 now
     * - 如果不是立即扫描，将下一次扫描时间推进到 now + debounceMillis（如果已有更晚的计划，则保留更晚者）
     * - 发送信号唤醒等待协程以重新计算等待时长
     */
    fun scheduleScan(immediately: Boolean = false) {
        if (immediately) {
            nextDeadline.set(System.nanoTime())
        } else {
            val target = System.nanoTime() + debounceMillis * 1_000_000
            nextDeadline.updateAndGet { oldDeadline ->
                maxOf(oldDeadline, target)
            }
        }
        signal.trySend(Unit)
    }

    private suspend fun CoroutineScope.loop() {
        while (isActive) {
            val deadline = nextDeadline.get()
            if (deadline == 0L) {
                // 没有计划：挂起直到有新计划
                try {
                    signal.receive()
                } catch (ex: ClosedReceiveChannelException) {
                    // 通道已被关闭，退出循环
                    break
                }
                continue
            }

            val now = System.nanoTime()
            val waitDuration = Duration.ofNanos(deadline - now)

            // 如果没有请求要马上执行，则等待超时或新的请求
            if (waitDuration.isPositive) {
                withTimeoutOrNull(waitDuration) {
                    signal.receive()
                }
                continue
            }

            // 到时间：执行一次扫描
            val startTime = System.nanoTime()
            try {
                val time = measureTime {
                    onScan()
                }
                logger.info("Finish scanning models, took $time")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Throwable) {
                logger.warn("Failed to scan models", ex)
            }

            // 更新 lastScanTime
            _lastScanTime.value = Instant.now()

            // 扫描期间如果没有新的计划，把 deadline 清零，回到“等待新计划”状态
            // 如果扫描期间有新的请求，把时间保留（可能已在过去，则下轮立刻执行）
            nextDeadline.updateAndGet { cur ->
                if (cur <= startTime) {
                    0L
                } else {
                    cur
                }
            }
        }
    }
}
