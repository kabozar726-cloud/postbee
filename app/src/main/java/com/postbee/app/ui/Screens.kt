package com.postbee.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postbee.app.PostBeeViewModel
import com.postbee.app.models.Platform
import com.postbee.app.models.UploadStatus
import com.postbee.app.models.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** PostBee rang: gehra background + sunehri (gold) accent — 3D feel. */
val Gold = Color(0xFFF5B301)
val DarkBg = Color(0xFF121212)
val CardBg = Color(0xFF1E1E1E)

fun formatDate(time: Long): String {
    return try {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time))
    } catch (e: Exception) {
        ""
    }
}

// ---------- Shared composables ----------

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.Black,
            disabledContainerColor = Color(0xFF555555),
            disabledContentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusDot(status: UploadStatus) {
    val color = when (status) {
        UploadStatus.DONE -> Color(0xFF4CAF50)
        UploadStatus.FAILED -> Color(0xFFF44336)
        UploadStatus.UPLOADING, UploadStatus.PROCESSING -> Gold
        UploadStatus.CHECKING -> Color(0xFF90A4AE)
        UploadStatus.IDLE -> Color(0xFF777777)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun PlatformChip(platform: Platform, status: UploadStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        StatusDot(status)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${platform.label()} • ${status.label()}",
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

// ---------- HOME ----------

@Composable
fun HomeScreen(viewModel: PostBeeViewModel, onOpenUpload: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val connectedCount = accounts.count { it.connected }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Hero card — sunehri gradient + 3D feel (elevated card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF5B301), Color(0xFFB8860B))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "PostBee",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = "Aik video — teen jagah",
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "TikTok • Instagram • Facebook",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$connectedCount / 3 accounts connected",
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            GoldButton(text = "Video Upload Karein", onClick = onOpenUpload)
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Haal ki uploads")
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = "Abhi koi upload nahi — pehli video upload karein.",
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        } else {
            items(history.take(5)) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = record.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = formatDate(record.createdAt),
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (res in record.results) {
                                PlatformChip(res.platform, res.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- ACCOUNTS ----------

@Composable
fun AccountsScreen(viewModel: PostBeeViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Accounts Connect Karein")
            Text(
                text = "Login official screen par hoga — password app mein save nahi hota.",
                fontSize = 14.sp,
                color = Color(0xFFAAAAAA)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(accounts) { account ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 3D feel: platform ka rang wala gol daagh
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    when (account.platform) {
                                        Platform.TIKTOK -> Color(0xFF25F4EE)
                                        Platform.INSTAGRAM -> Color(0xFFE1306C)
                                        Platform.FACEBOOK -> Color(0xFF1877F2)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = account.platform.label().take(1),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.platform.label(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (account.connected) account.displayName else "Connect nahi",
                                fontSize = 13.sp,
                                color = if (account.connected) Color(0xFF4CAF50) else Color(0xFFAAAAAA)
                            )
                        }
                        StatusDot(
                            if (account.connected) UploadStatus.DONE else UploadStatus.IDLE
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (account.platform) {
                            Platform.TIKTOK -> "Direct Post ke liye TikTok developer approval chahiye."
                            Platform.INSTAGRAM -> "Business ya Creator account chahiye, Facebook Page se linked."
                            Platform.FACEBOOK -> "Sirf Facebook Page par post hogi — personal profile par nahi."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GoldButton(
                        text = if (account.connected) "Dobara Connect Karein" else "Connect Karein",
                        onClick = {
                            val url = when (account.platform) {
                                Platform.TIKTOK -> viewModel.buildTikTokAuthUrl()
                                Platform.INSTAGRAM, Platform.FACEBOOK -> viewModel.buildMetaAuthUrl()
                            }
                            if (url == null) {
                                note = "Developer key abhi set nahi — ApiConfig mein key dalein, phir dobara koshish karein."
                            } else {
                                note = ""
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        }
                    )
                }
            }
        }
        if (note.isNotBlank()) {
            item {
                Text(
                    text = note,
                    fontSize = 14.sp,
                    color = Gold
                )
            }
        }
        item {
            // BoxWithConstraints: chhoti screen par note chhota dikhaye
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val small = maxWidth < 360.dp
                Text(
                    text = "Instagram Meta login se hi connect hota hai — Facebook login " +
                        "ke baad Instagram Business account khud mil jata hai.",
                    fontSize = if (small) 12.sp else 13.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
    }
}

// ---------- HISTORY ----------

@Composable
fun HistoryScreen(viewModel: PostBeeViewModel) {
    val history by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Upload History")
                if (history.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text(text = "Saaf Karein", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = "Abhi koi history nahi.",
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        } else {
            items(history) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = record.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = formatDate(record.createdAt),
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        for (res in record.results) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                PlatformChip(res.platform, res.status)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.message,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                            if (!res.liveUrl.isNullOrBlank()) {
                                Text(
                                    text = res.liveUrl,
                                    fontSize = 12.sp,
                                    color = Gold,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
