package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CourseEntity
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.ui.components.CallStatusSelector
import com.example.ui.components.LocalDatePickerModal
import com.example.ui.components.LocalTimePickerModal
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CallActionGreen
import com.example.ui.viewmodel.LeadFormState
import com.example.util.LocalDateHelper

@Composable
fun NotesScreen(
    formState: LeadFormState,
    leads: List<LeadEntity>,
    totalLeadCount: Int,
    courses: List<CourseEntity>,
    searchQuery: String,
    todayIso: String,
    listState: LazyListState,
    onToggleFormExpanded: () -> Unit,
    onStudentNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCourseSelected: (String) -> Unit,
    onCallDateSelected: (String) -> Unit,
    onStatusSelected: (CallStatus) -> Unit,
    onCustomFollowUpDateSelected: (String) -> Unit,
    onCustomFollowUpTimeSelected: (String?) -> Unit,
    onScheduleRetryToggle: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmitLead: () -> Unit,
    onCancelEdit: () -> Unit,
    onManageCoursesClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEditLead: (LeadEntity) -> Unit,
    onDeleteLead: (LeadEntity) -> Unit,
    onCallLead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var leadPendingDelete by remember { mutableStateOf<LeadEntity?>(null) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "lead_entry_form") {
            LeadEntryFormCard(
                form = formState,
                courses = courses,
                todayIso = todayIso,
                onToggleExpand = onToggleFormExpanded,
                onStudentNameChange = onStudentNameChange,
                onPhoneChange = onPhoneChange,
                onCourseSelected = onCourseSelected,
                onCallDateSelected = onCallDateSelected,
                onStatusSelected = onStatusSelected,
                onCustomFollowUpDateSelected = onCustomFollowUpDateSelected,
                onCustomFollowUpTimeSelected = onCustomFollowUpTimeSelected,
                onScheduleRetryToggle = onScheduleRetryToggle,
                onNotesChange = onNotesChange,
                onSubmit = onSubmitLead,
                onCancelEdit = onCancelEdit,
                onManageCoursesClick = onManageCoursesClick,
            )
        }

        item(key = "lead_search_header") {
            LeadHistoryHeaderAndSearch(
                searchQuery = searchQuery,
                visibleCount = leads.size,
                totalCount = totalLeadCount,
                onSearchQueryChange = onSearchQueryChange,
            )
        }

        if (leads.isEmpty()) {
            item(key = "empty_leads_state") {
                EmptyLeadsState(isSearching = searchQuery.isNotBlank())
            }
        } else {
            val coursePriceByKey = courses.associate { it.name.lowercase() to it.price }
            items(items = leads, key = { it.id }) { lead ->
                LeadHistoryCard(
                    lead = lead,
                    coursePrice = coursePriceByKey[lead.course.lowercase()].orEmpty(),
                    todayIso = todayIso,
                    onCallClick = { onCallLead(lead.phoneNumber) },
                    onEditClick = { onEditLead(lead) },
                    onDeleteClick = { leadPendingDelete = lead },
                )
            }
        }
    }

    leadPendingDelete?.let { lead ->
        AlertDialog(
            onDismissRequest = { leadPendingDelete = null },
            title = { Text("Delete Lead?") },
            text = {
                Text(
                    "Remove ${lead.displayName} (${lead.phoneNumber}) and any scheduled follow-up from your diary?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLead(lead)
                        leadPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_lead_button"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { leadPendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadEntryFormCard(
    form: LeadFormState,
    courses: List<CourseEntity>,
    todayIso: String,
    onToggleExpand: () -> Unit,
    onStudentNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCourseSelected: (String) -> Unit,
    onCallDateSelected: (String) -> Unit,
    onStatusSelected: (CallStatus) -> Unit,
    onCustomFollowUpDateSelected: (String) -> Unit,
    onCustomFollowUpTimeSelected: (String?) -> Unit,
    onScheduleRetryToggle: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
    onManageCoursesClick: () -> Unit,
) {
    var courseDropdownExpanded by remember { mutableStateOf(false) }
    var showCallDatePicker by remember { mutableStateOf(false) }
    var showFollowUpDatePicker by remember { mutableStateOf(false) }
    var showFollowUpTimePicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lead_entry_form_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (form.isEditing) 1.5.dp else 1.dp,
            color = if (form.isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (form.isEditing) {
                            stringResource(R.string.form_edit_lead_title)
                        } else {
                            stringResource(R.string.form_new_lead_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Call Date: ${LocalDateHelper.formatDisplayDate(form.callDateIso)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    onClick = { showCallDatePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("call_date_picker_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = "Change call date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (form.callDateIso == todayIso) "Today" else LocalDateHelper.formatShortDate(form.callDateIso),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("toggle_form_button"),
                ) {
                    Icon(
                        imageVector = if (form.isFormExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (form.isFormExpanded) "Collapse form" else "Expand form",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = form.isFormExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = form.phoneNumber,
                        onValueChange = onPhoneChange,
                        label = { Text(stringResource(R.string.field_phone_number)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                        },
                        isError = form.phoneError != null,
                        supportingText = form.phoneError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_phone_number"),
                    )

                    OutlinedTextField(
                        value = form.studentName,
                        onValueChange = onStudentNameChange,
                        label = { Text(stringResource(R.string.field_student_name)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_student_name"),
                    )

                    val selectedCourseEntity = remember(courses, form.course) {
                        courses.firstOrNull { it.name.equals(form.course, ignoreCase = true) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.field_interested_course),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(
                                onClick = onManageCoursesClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("manage_courses_inline_button"),
                            ) {
                                Icon(
                                    imageVector = if (courses.isEmpty()) Icons.Rounded.Add else Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (courses.isEmpty()) "Add Course" else "Edit / Add",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = courseDropdownExpanded,
                            onExpandedChange = {
                                if (courses.isEmpty()) {
                                    onManageCoursesClick()
                                } else {
                                    courseDropdownExpanded = it
                                }
                            },
                        ) {
                            OutlinedTextField(
                                value = form.course,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = {
                                    Text(
                                        if (courses.isEmpty()) {
                                            "Tap to add your first course"
                                        } else {
                                            "Select a course"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("course_dropdown"),
                            )
                            ExposedDropdownMenu(
                                expanded = courseDropdownExpanded,
                                onDismissRequest = { courseDropdownExpanded = false },
                            ) {
                                courses.forEach { courseEntity ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = courseEntity.name,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (courseEntity.price.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = courseEntity.price,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onCourseSelected(courseEntity.name)
                                            courseDropdownExpanded = false
                                        },
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Add or Edit Courses & Prices",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    onClick = {
                                        courseDropdownExpanded = false
                                        onManageCoursesClick()
                                    },
                                )
                            }
                        }

                        if (selectedCourseEntity != null && selectedCourseEntity.price.isNotBlank()) {
                            Surface(
                                onClick = onManageCoursesClick,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Course Price: ${selectedCourseEntity.price}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.field_call_status),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        CallStatusSelector(
                            selectedStatus = form.status,
                            onSelectStatus = onStatusSelected,
                        )
                    }

                    FollowUpSettingsPanel(
                        form = form,
                        todayIso = todayIso,
                        onOpenDatePicker = { showFollowUpDatePicker = true },
                        onOpenTimePicker = { showFollowUpTimePicker = true },
                        onQuickDateSelect = onCustomFollowUpDateSelected,
                        onScheduleRetryToggle = onScheduleRetryToggle,
                    )

                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(R.string.field_notes)) },
                        placeholder = { Text(stringResource(R.string.field_notes_placeholder)) },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notes"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (form.isEditing) {
                            OutlinedButton(
                                onClick = onCancelEdit,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("cancel_edit_button"),
                            ) {
                                Text(stringResource(R.string.action_cancel_edit))
                            }
                        }

                        Button(
                            onClick = onSubmit,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("save_lead_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (form.isEditing) {
                                    stringResource(R.string.action_update_lead)
                                } else {
                                    stringResource(R.string.action_save_lead)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCallDatePicker) {
        LocalDatePickerModal(
            initialIsoDate = form.callDateIso,
            title = "Select Call Date",
            onDateSelected = onCallDateSelected,
            onDismiss = { showCallDatePicker = false },
        )
    }

    if (showFollowUpDatePicker) {
        LocalDatePickerModal(
            initialIsoDate = form.customFollowUpDateIso,
            title = "Select Follow-up Date",
            onDateSelected = onCustomFollowUpDateSelected,
            onDismiss = { showFollowUpDatePicker = false },
        )
    }

    if (showFollowUpTimePicker) {
        LocalTimePickerModal(
            initialTime = form.customFollowUpTime,
            onTimeSelected = { onCustomFollowUpTimeSelected(it) },
            onClearTime = { onCustomFollowUpTimeSelected(null) },
            onDismiss = { showFollowUpTimePicker = false },
        )
    }
}

@Composable
private fun FollowUpSettingsPanel(
    form: LeadFormState,
    todayIso: String,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
    onQuickDateSelect: (String) -> Unit,
    onScheduleRetryToggle: (Boolean) -> Unit,
) {
    val status = form.status
    val containerColor = when {
        status.isAutoTwoDayFollowUp -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        status.requiresCustomFollowUp || (status.supportsOptionalRetryFollowUp && form.scheduleRetryCall) -> {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        }
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("followup_settings_panel"),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                status.isAutoTwoDayFollowUp -> {
                    val autoDate = form.computedAutoFollowUpIso ?: LocalDateHelper.addDays(form.callDateIso, 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto Follow-up Scheduled (+2 Days)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = "Due on ${LocalDateHelper.formatDisplayDate(autoDate)} in Reminders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                status.requiresCustomFollowUp -> {
                    Text(
                        text = "Call Back Follow-up Date & Time",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    DateTimePickerRow(
                        isoDate = form.customFollowUpDateIso,
                        hhmm = form.customFollowUpTime,
                        onOpenDatePicker = onOpenDatePicker,
                        onOpenTimePicker = onOpenTimePicker,
                    )
                }

                status.supportsOptionalRetryFollowUp -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Schedule another attempt",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Optional retry reminder for ${status.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = form.scheduleRetryCall,
                            onCheckedChange = onScheduleRetryToggle,
                            modifier = Modifier.testTag("retry_schedule_switch"),
                        )
                    }
                    AnimatedVisibility(visible = form.scheduleRetryCall) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onQuickDateSelect(todayIso) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                ) {
                                    Text("Today", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onQuickDateSelect(LocalDateHelper.addDays(todayIso, 1)) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                ) {
                                    Text("Tomorrow", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            DateTimePickerRow(
                                isoDate = form.customFollowUpDateIso,
                                hhmm = form.customFollowUpTime,
                                onOpenDatePicker = onOpenDatePicker,
                                onOpenTimePicker = onOpenTimePicker,
                            )
                        }
                    }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${status.label} will not create an automatic follow-up reminder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTimePickerRow(
    isoDate: String,
    hhmm: String?,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onOpenDatePicker,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1.2f)
                .defaultMinSize(minHeight = 44.dp)
                .testTag("pick_followup_date_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = "Pick follow-up date",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = LocalDateHelper.formatDisplayDate(isoDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            onClick = onOpenTimePicker,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(0.85f)
                .defaultMinSize(minHeight = 44.dp)
                .testTag("pick_followup_time_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Pick follow-up time",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = LocalDateHelper.formatTimeDisplay(hhmm) ?: "Any time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LeadHistoryHeaderAndSearch(
    searchQuery: String,
    visibleCount: Int,
    totalCount: Int,
    onSearchQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Saved Leads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (searchQuery.isBlank()) "$totalCount total" else "$visibleCount of $totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_leads_input"),
        )
    }
}

@Composable
private fun LeadHistoryCard(
    lead: LeadEntity,
    coursePrice: String,
    todayIso: String,
    onCallClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lead_card_${lead.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lead.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (lead.studentName.isNotBlank()) {
                        Text(
                            text = lead.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = lead.callStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lead.course.isNotBlank()) {
                    val courseLabel = if (coursePrice.isNotBlank()) {
                        "${lead.course} • $coursePrice"
                    } else {
                        lead.course
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    text = "Called ${LocalDateHelper.formatShortDate(lead.callDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (lead.hasActiveFollowUp && lead.followUpDate != null) {
                val timeSuffix = LocalDateHelper.formatTimeDisplay(lead.followUpTime)?.let { " • $it" }.orEmpty()
                val relative = LocalDateHelper.formatRelativeDate(lead.followUpDate, todayIso)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Follow-up: ${LocalDateHelper.formatDisplayDate(lead.followUpDate)}$timeSuffix ($relative)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (lead.notes.isNotBlank()) {
                Text(
                    text = lead.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onCallClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("lead_call_button_${lead.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.action_call_student),
                        tint = CallActionGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Call",
                        color = CallActionGreen,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("lead_edit_button_${lead.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.action_edit_lead),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("lead_delete_button_${lead.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete_lead),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLeadsState(isSearching: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = if (isSearching) Icons.Outlined.Search else Icons.Outlined.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isSearching) {
                    stringResource(R.string.empty_search_title)
                } else {
                    stringResource(R.string.empty_leads_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSearching) {
                    stringResource(R.string.empty_search_subtitle)
                } else {
                    stringResource(R.string.empty_leads_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
