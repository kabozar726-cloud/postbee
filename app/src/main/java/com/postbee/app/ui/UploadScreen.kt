package com.postbee.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postbee.app.PostBeeViewModel
import com.postbee.app.data.VideoChecker
import com.postbee.app.models.Platform
import com.postbee.app.models.UploadStatus
import com.postbee.app.models.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Upload screen — video chunein, title/description/tags likhein,
 * thumbnail lagayein, platforms tick karein, Upload dabayein.
 */
@Composable
fun UploadScreen(viewModel: PostBeeViewModel) {
    val context = LocalContext.current
    val videoInfo by viewModel.videoInfo.collectAsState()
    val warnings by viewModel.warnings.collectAsState()
    val progressMap by viewModel.uploadProgress.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf(setOf<Platform>()) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.resetUploadProgress()
            viewModel.setVideo(it)
        }
    }
    val thumbPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        thumbUri = uri
    }

    // Video ka pehla frame — preview ke liye (IO thread par)
    val frameBitmap by produceState<Bitmap?>(null, videoInfo) {
        value = withContext(Dispatchers.IO) {
            videoInfo?.let { VideoChecker.thumbnailFrame(context, Uri.parse(it.uriString)) }
        }
    }
    // User ki chuni hui thumbnail — preview ke liye (IO thread par)
    val pickedThumbBitmap by produceState<Bitmap?>(null, thumbUri) {
        value = withContext(Dispatchers.IO) {
            thumbUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val uploading = progressMap.values.any {
        it.status == UploadStatus.CHECKING ||
            it.status == UploadStatus.UPLOADING ||
            it.status == UploadStatus.PROCESSING
    }
    val canUpload = videoInfo != null &&
        selected.isNotEmpty() &&
        title.isNotBlank() &&
        !uploading

    val captionLength = title.length + description.length + tags.length

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Video Upload Karein")

        GoldButton(text = "Video Chunein", onClick = { videoPicker.launch("video/*") })

        // Video preview — BoxWithConstraints se 16:9 size (Dp * Float, kabhi Float * Dp nahi)
        if (videoInfo != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val previewHeight = maxWidth * (9f / 16f)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val bitmap = pickedThumbBitmap ?: frameBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Video preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewHeight)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lambai: ${videoInfo!!.durationMs / 1000} second • " +
                                "Size: ${"%.1f".format(videoInfo!!.sizeBytes / 1024f / 1024f)} MB • " +
                                "${videoInfo!!.width}x${videoInfo!!.height}",
                            fontSize = 13.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        }

        // Warnings — upload se pehle saaf dikhayein
        for (warn in warnings) {
            Text(
                text = "⚠ $warn",
                fontSize = 13.sp,
                color = Gold
            )
        }

        // Thumbnail picker
        GoldButton(text = "Thumbnail Chunein (optional)", onClick = { thumbPicker.launch("image/*") })
        if (thumbUri != null) {
            Text(
                text = "Thumbnail lag gayi.",
                fontSize = 13.sp,
                color = Color(0xFF4CAF50)
            )
        }

        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        TextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Tags / Hashtags (masalan #bee #history)") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Text(
            text = "Caption kul $captionLength characters — TikTok aur Instagram par 2,200 tak.",
            fontSize = 12.sp,
            color = Color(0xFFAAAAAA)
        )

        SectionTitle("Kahan post karna hai?")
        for (platform in Platform.values()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = selected.contains(platform),
                    onCheckedChange = { checked ->
                        selected = if (checked) {
                            selected + platform
                        } else {
                            selected - platform
                        }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = Gold)
                )
                Text(
                    text = platform.label(),
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        GoldButton(
            text = if (uploading) "Upload ho raha hai..." else "Upload Karein",
            enabled = canUpload,
            onClick = {
                viewModel.startUpload(title.trim(), description.trim(), tags.trim(), thumbUri, selected)
            }
        )

        // Per-platform progress
        for ((platform, result) in progressMap) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(result.status)
                        Text(
                            text = "  ${platform.label()} — ${result.status.label()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = result.progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = result.message, fontSize = 13.sp, color = Color(0xFFCCCCCC))
                    if (!result.liveUrl.isNullOrBlank()) {
                        Text(text = result.liveUrl, fontSize = 12.sp, color = Gold)
                    }
                }
            }
        }
    }
}
