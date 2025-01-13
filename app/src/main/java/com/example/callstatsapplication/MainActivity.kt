package com.example.callstatsapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Calendar
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CallStatsScreen()
            }
        }
    }

    @Composable
    fun CallStatsScreen() {
        var hasPermission by remember { mutableStateOf(false) }
        var callStatsMap by remember { mutableStateOf<Map<String, CallStats>?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.READ_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                hasPermission = true
                if (callStatsMap == null && !isLoading) {
                    isLoading = true
                    loadCallStats { statsMap ->
                        callStatsMap = statsMap
                        isLoading = false
                    }
                }
            }
        }

        when {
            isLoading -> LoadingIndicator()
            hasPermission -> {
                callStatsMap?.let {
                    StatsDisplay(it)
                } ?: RequestPermissionButton()
            }
            else -> RequestPermissionButton()
        }
    }

    @Composable
    fun RequestPermissionButton() {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Button(onClick = {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.READ_CALL_LOG),
                    1
                )
            }) {
                Text("请求通话记录权限")
            }
        }
    }

    @Composable
    fun StatsDisplay(statsMap: Map<String, CallStats>) {
        val currentDate = remember {
            java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // 启用垂直滚动
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 显示当前日期
            Text("今日日期：$currentDate", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

            // 统计结果标题
            Text("统计结果", fontSize = 28.sp, modifier = Modifier.padding(bottom = 24.dp))

            statsMap.forEach { (period, stats) ->
                val title = when (period) {
                    "Today" -> "今日统计"
                    "ThisWeek" -> "本周统计"
                    "ThisMonth" -> "本月统计"
                    else -> "统计数据"
                }

                Text(title, fontSize = 24.sp, modifier = Modifier.padding(vertical = 16.dp))
                Text("通话次数：${stats.totalCalls}次", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("去电次数：${stats.outgoingCalls}次", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("来电次数：${stats.incomingCalls}次", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("未接次数：${stats.missedCalls}次", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("去电时长：${stats.outgoingDuration / 60}分${stats.outgoingDuration % 60}秒", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("来电时长：${stats.incomingDuration / 60}分${stats.incomingDuration % 60}秒", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("总时长：${stats.totalDuration / 60}分${stats.totalDuration % 60}秒", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))

                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }




    @Composable
    fun LoadingIndicator() {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator()
        }
    }

    private fun loadCallStats(onResult: (Map<String, CallStats>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val todayRange = getStartAndEndOfDay()
            val weekRange = getStartAndEndOfWeek()
            val monthRange = getStartAndEndOfMonth()

            val todayLogs = getCallLogs(todayRange.first, todayRange.second)
            val weekLogs = getCallLogs(weekRange.first, weekRange.second)
            val monthLogs = getCallLogs(monthRange.first, monthRange.second)

            val todayStats = calculateCallStats(todayLogs)
            val weekStats = calculateCallStats(weekLogs)
            val monthStats = calculateCallStats(monthLogs)

            val statsMap = mapOf(
                "Today" to todayStats,
                "ThisWeek" to weekStats,
                "ThisMonth" to monthStats
            )

            withContext(Dispatchers.Main) {
                onResult(statsMap)
            }
        }
    }

    private fun getCallLogs(startTime: Long, endTime: Long): List<CallLogInfo> {
        val callLogList = mutableListOf<CallLogInfo>()
        val cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
            arrayOf(startTime.toString(), endTime.toString()),
            CallLog.Calls.DEFAULT_SORT_ORDER
        )

        cursor?.use {
            while (it.moveToNext()) {
                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))

                when (type) {
                    CallLog.Calls.OUTGOING_TYPE -> callLogList.add(CallLogInfo("OUTGOING", duration))
                    CallLog.Calls.INCOMING_TYPE -> callLogList.add(CallLogInfo("INCOMING", duration))
                    CallLog.Calls.MISSED_TYPE -> callLogList.add(CallLogInfo("MISSED", 0))
                }
            }
        }

        return callLogList
    }

    private fun calculateCallStats(callLogs: List<CallLogInfo>): CallStats {
        val totalCalls = callLogs.size
        val outgoingCalls = callLogs.count { it.type == "OUTGOING" }
        val incomingCalls = callLogs.count { it.type == "INCOMING" }
        val missedCalls = callLogs.count { it.type == "MISSED" }
        val outgoingDuration = callLogs.filter { it.type == "OUTGOING" }.sumOf { it.duration }
        val incomingDuration = callLogs.filter { it.type == "INCOMING" }.sumOf { it.duration }
        val totalDuration = outgoingDuration + incomingDuration

        return CallStats(
            totalCalls,
            outgoingCalls,
            incomingCalls,
            missedCalls,
            outgoingDuration,
            incomingDuration,
            totalDuration
        )
    }

    private fun getStartAndEndOfDay(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return Pair(startOfDay, endOfDay)
    }

    private fun getStartAndEndOfWeek(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        return Pair(startOfWeek, endOfWeek)
    }

    private fun getStartAndEndOfMonth(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 0)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis

        return Pair(startOfMonth, endOfMonth)
    }

    data class CallLogInfo(val type: String, val duration: Long)
    data class CallStats(
        val totalCalls: Int,
        val outgoingCalls: Int,
        val incomingCalls: Int,
        val missedCalls: Int,
        val outgoingDuration: Long,
        val incomingDuration: Long,
        val totalDuration: Long
    )
}
