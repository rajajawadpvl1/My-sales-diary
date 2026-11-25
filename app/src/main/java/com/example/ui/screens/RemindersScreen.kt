package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.ui.components.LocalDatePickerModal
import com.example.ui.components.LocalTimePickerModal
import com.example.ui.components.StatusBadge
import com.example.ui.components.callStatusStyle
import com.example.ui.theme.CallActionGreen
import com.example.ui.theme.DueTodayAccent
import com.example.ui.theme.DueTodaySurface
import com.example.ui.theme.OverdueAccent
import com.example.ui.theme.OverdueSurface
import com.example.ui.viewmodel.ReminderBuckets
import com.example.util.LocalDateHelper

private enum class ReminderUrgency {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
}

private data class ReschedulePromptState(
    val lead: LeadEntity,
    val targetStatus: CallStatus,
)

@Composable
fun RemindersScreen(
    reminders: ReminderBuckets,
    coursePricesByName: Map<String, String>,
    todayIso: String,
    onCallClick: (String) -> Unit,
    onQuickOutcome: (LeadEntity, CallStatus) -> Unit,
    onRescheduleOutcome: (LeadEntity, CallStatus, String, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reschedulePrompt by remember { mutableStateOf<ReschedulePromptState?>(null) }

    if (reminders.isEmpty) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            EmptyRemindersState()
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("reminders_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section 1: Overdue (highest priority — remains visible until action is taken)
            if (reminders.overdue.isNotEmpty()) {
                item(key = "header_overdue") {
                    ReminderSectionHeader(
                        title = stringResource(R.string.reminders_overdue),
                        count = reminders.overdue.size,
                        urgency = ReminderUrgency.OVERDUE,
                    )
                }
                items(items = reminders.overdue, key = { "overdue_${it.id}" }) { lead ->
                    ReminderCard(
                        lead = lead,
                        coursePrice = coursePricesByName[lead.course.lowercase()].orEmpty(),
                        todayIso = todayIso,
                        urgency = ReminderUrgency.OVERDUE,
                        onCallClick = { onCallClick(lead.phoneNumber) },
                        onInterestedClick = { onQuickOutcome(lead, CallStatus.INTERESTED) },
                        onCallBackClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.CALL_BACK)
                        },
                        onNotConnectedClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.NOT_CONNECTED)
                        },
                        onNotInterestedClick = { onQuickOutcome(lead, CallStatus.NOT_INTERESTED) },
                        onEnrolledClick = { onQuickOutcome(lead, CallStatus.ENROLLED) },
                    )
                }
            }

            // Section 2: Due Today
            if (reminders.dueToday.isNotEmpty()) {
                item(key = "header_due_today") {
                    ReminderSectionHeader(
                        title = stringResource(R.string.reminders_due_today),
                        count = reminders.dueToday.size,
                        urgency = ReminderUrgency.DUE_TODAY,
                    )
                }
                items(items = reminders.dueToday, key = { "today_${it.id}" }) { lead ->
                    ReminderCard(
                        lead = lead,
                        coursePrice = coursePricesByName[lead.course.lowercase()].orEmpty(),
                        todayIso = todayIso,
                        urgency = ReminderUrgency.DUE_TODAY,
                        onCallClick = { onCallClick(lead.phoneNumber) },
                        onInterestedClick = { onQuickOutcome(lead, CallStatus.INTERESTED) },
                        onCallBackClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.CALL_BACK)
                        },
                        onNotConnectedClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.NOT_CONNECTED)
                        },
                        onNotInterestedClick = { onQuickOutcome(lead, CallStatus.NOT_INTERESTED) },
                        onEnrolledClick = { onQuickOutcome(lead, CallStatus.ENROLLED) },
                    )
                }
            }

            // Section 3: Upcoming
            if (reminders.upcoming.isNotEmpty()) {
                item(key = "header_upcoming") {
                    ReminderSectionHeader(
                        title = stringResource(R.string.reminders_upcoming),
                        count = reminders.upcoming.size,
                        urgency = ReminderUrgency.UPCOMING,
                    )
                }
                items(items = reminders.upcoming, key = { "upcoming_${it.id}" }) { lead ->
                    ReminderCard(
                        lead = lead,
                        coursePrice = coursePricesByName[lead.course.lowercase()].orEmpty(),
                        todayIso = todayIso,
                        urgency = ReminderUrgency.UPCOMING,
                        onCallClick = { onCallClick(lead.phoneNumber) },
                        onInterestedClick = { onQuickOutcome(lead, CallStatus.INTERESTED) },
                        onCallBackClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.CALL_BACK)
                        },
                        onNotConnectedClick = {
                            reschedulePrompt = ReschedulePromptState(lead, CallStatus.NOT_CONNECTED)
                        },
                        onNotInterestedClick = { onQuickOutcome(lead, CallStatus.NOT_INTERESTED) },
                        onEnrolledClick = { onQuickOutcome(lead, CallStatus.ENROLLED) },
                    )
                }
            }
        }
    }

    reschedulePrompt?.let { prompt ->
        RescheduleFollowUpDialog(
            lead = prompt.lead,
            targetStatus = prompt.targetStatus,
            todayIso = todayIso,
            onConfirm = { pickedDate, pickedTime, note ->
                onRescheduleOutcome(prompt.lead, prompt.targetStatus, pickedDate, pickedTime, note)
                reschedulePrompt = null
            },
            onDismiss = { reschedulePrompt = null },
        )
    }
}

@Composable
private fun ReminderSectionHeader(
    title: String,
    count: Int,
    urgency: ReminderUrgency,
) {
    val isDark = isSystemInDarkTheme()
    val (accent, badgeBg, icon) = when (urgency) {
        ReminderUrgency.OVERDUE -> Triple(
            if (isDark) Color(0xFFFF9B9B) else OverdueAccent,
            if (isDark) Color(0xFF421A1A) else OverdueSurface,
            Icons.Outlined.WarningAmber,
        )
        ReminderUrgency.DUE_TODAY -> Triple(
            if (isDark) Color(0xFFFFB574) else DueTodayAccent,
            if (isDark) Color(0xFF45270B) else DueTodaySurface,
            Icons.Outlined.EventAvailable,
        )
        ReminderUrgency.UPCOMING -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Outlined.CalendarToday,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("section_header_${urgency.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = badgeBg,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderCard(
    lead: LeadEntity,
    coursePrice: String,
    todayIso: String,
    urgency: ReminderUrgency,
    onCallClick: () -> Unit,
    onInterestedClick: () -> Unit,
    onCallBackClick: () -> Unit,
    onNotConnectedClick: () -> Unit,
    onNotInterestedClick: () -> Unit,
    onEnrolledClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = when (urgency) {
        ReminderUrgency.OVERDUE -> (if (isDark) Color(0xFFFF8A8A) else OverdueAccent).copy(alpha = 0.45f)
        ReminderUrgency.DUE_TODAY -> (if (isDark) Color(0xFFFFB574) else DueTodayAccent).copy(alpha = 0.45f)
        ReminderUrgency.UPCOMING -> MaterialTheme.colorScheme.outline
    }
    val dateAccent = when (urgency) {
        ReminderUrgency.OVERDUE -> if (isDark) Color(0xFFFF9B9B) else OverdueAccent
        ReminderUrgency.DUE_TODAY -> if (isDark) Color(0xFFFFB574) else DueTodayAccent
        ReminderUrgency.UPCOMING -> MaterialTheme.colorScheme.primary
    }
    val dateSurface = when (urgency) {
        ReminderUrgency.OVERDUE -> if (isDark) Color(0xFF3E1919) else OverdueSurface
        ReminderUrgency.DUE_TODAY -> if (isDark) Color(0xFF40250D) else DueTodaySurface
        ReminderUrgency.UPCOMING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }

    val followUpIso = lead.followUpDate.orEmpty()
    val relativeLabel = LocalDateHelper.formatRelativeDate(followUpIso, todayIso)
    val timeLabel = LocalDateHelper.formatTimeDisplay(lead.followUpTime)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${lead.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lead.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lead.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Prominent Direct Dialer Button (opens phone dialer with tel: URI)
                Button(
                    onClick = onCallClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CallActionGreen,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("reminder_call_button_${lead.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.action_call_student),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Call",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (lead.course.isNotBlank()) {
                    val courseLabel = if (coursePrice.isNotBlank()) {
                        "${lead.course} • $coursePrice"
                    } else {
                        lead.course
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = courseLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                StatusBadge(status = lead.callStatus)
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = dateSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = dateAccent,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val timePart = if (timeLabel != null) " • $timeLabel" else ""
                    Text(
                        text = "${LocalDateHelper.formatDisplayDate(followUpIso)}$timePart — $relativeLabel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = dateAccent,
                    )
                }
            }

            if (lead.notes.isNotBlank()) {
                Text(
                    text = lead.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "After Call Action",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AfterCallActionChip(
                        label = "Interested (+2d)",
                        status = CallStatus.INTERESTED,
                        icon = Icons.Rounded.ThumbUp,
                        onClick = onInterestedClick,
                        modifier = Modifier.testTag("after_call_interested_${lead.id}"),
                    )
                    AfterCallActionChip(
                        label = "Call Back",
                        status = CallStatus.CALL_BACK,
                        icon = Icons.Rounded.PhoneCallback,
                        onClick = onCallBackClick,
                        modifier = Modifier.testTag("after_call_callback_${lead.id}"),
                    )
                    AfterCallActionChip(
                        label = "Not Connected",
                        status = CallStatus.NOT_CONNECTED,
                        icon = Icons.Rounded.PhoneMissed,
                        onClick = onNotConnectedClick,
                        modifier = Modifier.testTag("after_call_not_connected_${lead.id}"),
                    )
                    AfterCallActionChip(
                        label = "Not Interested",
                        status = CallStatus.NOT_INTERESTED,
                        icon = Icons.Rounded.Block,
                        onClick = onNotInterestedClick,
                        modifier = Modifier.testTag("after_call_not_interested_${lead.id}"),
                    )
                    AfterCallActionChip(
                        label = "Enrolled",
                        status = CallStatus.ENROLLED,
                        icon = Icons.Rounded.School,
                        onClick = onEnrolledClick,
                        modifier = Modifier.testTag("after_call_enrolled_${lead.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AfterCallActionChip(
    label: String,
    status: CallStatus,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = callStatusStyle(status)
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = RoundedCornerShape(10.dp),
        color = style.containerColor,
        border = BorderStroke(1.dp, style.contentColor.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = style.contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = style.contentColor,
            )
        }
    }
}

@Composable
private fun RescheduleFollowUpDialog(
    lead: LeadEntity,
    targetStatus: CallStatus,
    todayIso: String,
    onConfirm: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedDate by remember { mutableStateOf(LocalDateHelper.addDays(todayIso, 1)) }
    var pickedTime by remember { mutableStateOf<String?>("10:00") }
    var noteText by remember { mutableStateOf(lead.notes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (targetStatus == CallStatus.CALL_BACK) {
                    "Schedule Call Back"
                } else {
                    "Retry Attempt (${targetStatus.label})"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${lead.displayName} • ${lead.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickedDate = todayIso },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Today")
                    }
                    OutlinedButton(
                        onClick = { pickedDate = LocalDateHelper.addDays(todayIso, 1) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Tomorrow")
                    }
                    OutlinedButton(
                        onClick = { pickedDate = LocalDateHelper.addDays(todayIso, 2) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("+2 Days")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LocalDateHelper.formatShortDate(pickedDate),
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LocalDateHelper.formatTimeDisplay(pickedTime) ?: "Any time",
                            maxLines = 1,
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Update Notes (Optional)") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pickedDate, pickedTime, noteText) },
                modifier = Modifier.testTag("confirm_reschedule_button"),
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showDatePicker) {
        LocalDatePickerModal(
            initialIsoDate = pickedDate,
            title = "Select Next Call Date",
            onDateSelected = { pickedDate = it },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        LocalTimePickerModal(
            initialTime = pickedTime,
            onTimeSelected = { pickedTime = it },
            onClearTime = { pickedTime = null },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun EmptyRemindersState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_reminders_state"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.empty_reminders_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.empty_reminders_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
