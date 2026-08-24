package com.mipastudio.memostamp.feature.profile.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import kotlinx.coroutines.launch

@Composable
fun SupabaseConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val supabaseClient = remember(context) { SupabaseClient.getInstance(context) }

    var url by remember { mutableStateOf(SupabaseConfig.getSupabaseUrl(context)) }
    var anonKey by remember { mutableStateOf(SupabaseConfig.getAnonKey(context)) }
    
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cấu hình kết nối Cloud", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryText)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Đồng bộ tài khoản, kết bạn, nhắn tin trò chuyện và chia sẻ con tem kỷ niệm qua hệ thống đám mây.",
                    fontSize = 12.sp,
                    color = SecondaryText
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Đường dẫn Máy chủ (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = anonKey,
                    onValueChange = { anonKey = it },
                    label = { Text("Khóa truy cập (Anon Key)") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Test Connection Button
                Button(
                    onClick = {
                        isTesting = true
                        testResult = null
                        coroutineScope.launch {
                            val res = supabaseClient.testConnection(url, anonKey)
                            testResult = res
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = WarmPaperBg, contentColor = PrimaryText),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, UIBorder, RoundedCornerShape(10.dp))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang kiểm tra kết nối...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm tra trạng thái máy chủ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Test Result Status Box
                testResult?.let { (success, message) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (success) Color(0xFF81C784) else Color(0xFFE57373),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                if (success) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                message,
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SupabaseConfig.saveConfig(context, url, anonKey)
                    Toast.makeText(context, "Đã cập nhật cấu hình đám mây thành công! ✨", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Lưu cấu hình")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        containerColor = SurfaceWhite
    )
}

