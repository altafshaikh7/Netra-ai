package com.example.netraai.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netraai.ui.theme.DarkBackground
import com.example.netraai.ui.theme.DarkCard
import com.example.netraai.ui.theme.ElectricCyan
import com.example.netraai.ui.theme.TextPrimary
import com.example.netraai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun BenchmarkScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Tap 'Start Benchmark' to begin.") }
    var results by remember { mutableStateOf<Map<String, Map<String, ConfusionMatrix>>?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DEBUG BENCHMARK",
                color = ElectricCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
            ) {
                Text("Close", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = statusText, color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            enabled = !isRunning,
            onClick = {
                isRunning = true
                scope.launch {
                    val runner = BenchmarkRunner(context)
                    val res = runner.runBenchmark { progress ->
                        statusText = progress
                    }
                    results = res
                    isRunning = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Benchmark", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        results?.forEach { (profileName, labelMap) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Profile: $profileName", color = ElectricCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    labelMap.forEach { (label, cm) ->
                        Text(
                            text = "$label -> TP:${cm.truePositives} FP:${cm.falsePositives} FN:${cm.falseNegatives} | P:${String.format(Locale.US, "%.2f", cm.precision)} R:${String.format(Locale.US, "%.2f", cm.recall)}",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
