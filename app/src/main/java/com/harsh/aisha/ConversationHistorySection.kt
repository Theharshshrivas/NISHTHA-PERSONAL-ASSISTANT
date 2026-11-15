package com.harsh.aisha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harsh.aisha.database.ConversationMessage
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationHistorySection(
    history: List<ConversationMessage>,
    status: String,
    onClear: () -> Unit
) {
    var showConfirmation by remember {
        mutableStateOf(false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "CHAT HISTORY (${history.size})",
                color = Color(0xFF55E4D0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = { showConfirmation = true },
                enabled = history.isNotEmpty()
            ) {
                Text("Clear history")
            }
        }

        Text(
            status,
            color = Color.LightGray,
            fontSize = 11.sp
        )

        if (history.isEmpty()) {
            InfoCard(
                "No conversations yet",
                "Your future conversations will be saved securely on this device."
            )
        } else {
            history.take(10).asReversed().forEach { item ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF201A35)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "YOU",
                            color = Color(0xFF55E4D0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            item.userText,
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        Text(
                            "NISHTHA",
                            color = Color(0xFF9B7BFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            item.assistantText,
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        Text(
                            DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                            ).format(Date(item.createdAtMillis)),
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showConfirmation = false
            },
            title = {
                Text("Clear chat history?")
            },
            text = {
                Text(
                    "All locally saved conversations will be permanently deleted. Your separate saved memories and reminders will remain."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onClear()
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
