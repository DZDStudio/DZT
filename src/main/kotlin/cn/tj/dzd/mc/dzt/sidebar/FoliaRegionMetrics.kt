package cn.tj.dzd.mc.dzt.sidebar

import java.lang.reflect.Method

/**
 * 读取 Folia 当前区域的运行指标。
 *
 * Folia 尚未在 Bukkit API 中公开区域占用率，因此这里将版本相关的反射集中隔离。
 */
internal object FoliaRegionMetrics {
    private val bridge: Bridge? by lazy {
        runCatching { Bridge() }.getOrNull()
    }

    /**
     * 读取当前区域 1 分钟窗口的占用率。
     *
     * 该方法必须在 Folia 区域或实体线程中调用。
     *
     * @return 以 `0.0..1.0` 为正常范围的占用率；超载时可大于 `1.0`，不可用时返回 null。
     */
    fun currentOneMinuteUtilisation(): Double? {
        return bridge?.readCurrentOneMinuteUtilisation()
    }

    private class Bridge {
        private val tickRegionSchedulerClass = Class.forName(
            "io.papermc.paper.threadedregions.TickRegionScheduler"
        )
        private val scheduleHandleClass = Class.forName(
            "io.papermc.paper.threadedregions.TickRegionScheduler\$RegionScheduleHandle"
        )
        private val getCurrentTickingTask: Method = tickRegionSchedulerClass.getMethod("getCurrentTickingTask")
        private val getCurrentRegion: Method = tickRegionSchedulerClass.getMethod("getCurrentRegion")
        private val getRegionData: Method = Class.forName(
            "io.papermc.paper.threadedregions.ThreadedRegionizer\$ThreadedRegion"
        ).getMethod("getData")
        private val getRegionSchedulingHandle: Method = Class.forName(
            "io.papermc.paper.threadedregions.TickRegions\$TickRegionData"
        ).getMethod("getRegionSchedulingHandle")
        private val getTickReportOneMinute: Method = scheduleHandleClass.getMethod(
            "getTickReport1m",
            Long::class.javaPrimitiveType,
        )
        private val utilisation: Method = Class.forName(
            "ca.spottedleaf.moonrise.common.time.TickData\$TickReportData"
        ).getMethod("utilisation")

        fun readCurrentOneMinuteUtilisation(): Double? {
            return runCatching {
                val scheduleHandle = currentScheduleHandle() ?: return null

                val report = getTickReportOneMinute.invoke(scheduleHandle, System.nanoTime()) ?: return null
                (utilisation.invoke(report) as Number).toDouble()
                    .takeIf { it.isFinite() && it >= 0.0 }
            }.getOrNull()
        }

        private fun currentScheduleHandle(): Any? {
            val tickingTask = getCurrentTickingTask.invoke(null)
            if (scheduleHandleClass.isInstance(tickingTask)) {
                return tickingTask
            }

            val region = getCurrentRegion.invoke(null) ?: return null
            val regionData = getRegionData.invoke(region)
            return getRegionSchedulingHandle.invoke(regionData)
                .takeIf(scheduleHandleClass::isInstance)
        }
    }
}
