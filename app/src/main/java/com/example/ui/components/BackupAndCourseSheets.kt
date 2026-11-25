package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CourseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupToolsBottomSheet(
    leadCount: Int,
    onExportCsv: () -> Unit,
    onBackupJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onManageCourses: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SheetActionItem(
                icon = Icons.Outlined.TableChart,
                title = stringResource(R.string.backup_export_csv),
                subtitle = "${stringResource(R.string.backup_export_csv_desc)} ($leadCount leads)",
                onClick = {
                    onDismiss()
                    onExportCsv()
                },
                modifier = Modifier.testTag("action_export_csv"),
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetActionItem(
                icon = Icons.Outlined.CloudDownload,
                title = stringResource(R.string.backup_export_json),
                subtitle = stringResource(R.string.backup_export_json_desc),
                onClick = {
                    onDismiss()
                    onBackupJson()
                },
                modifier = Modifier.testTag("action_backup_json"),
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetActionItem(
                icon = Icons.Outlined.FileUpload,
                title = stringResource(R.string.backup_restore_json),
                subtitle = stringResource(R.string.backup_restore_json_desc),
                onClick = {
                    onDismiss()
                    onRestoreJson()
                },
                modifier = Modifier.testTag("action_restore_json"),
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetActionItem(
                icon = Icons.Outlined.School,
                title = stringResource(R.string.manage_courses_title),
                subtitle = stringResource(R.string.manage_courses_desc),
                onClick = {
                    onDismiss()
                    onManageCourses()
                },
                modifier = Modifier.testTag("action_manage_courses"),
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ManageCoursesDialog(
    courses: List<CourseEntity>,
    onAddCourse: (name: String, price: String) -> Unit,
    onUpdateCourse: (course: CourseEntity, newName: String, newPrice: String) -> Unit,
    onDeleteCourse: (CourseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var courseName by remember { mutableStateOf("") }
    var coursePrice by remember { mutableStateOf("") }

    fun resetInputs() {
        editingCourse = null
        courseName = ""
        coursePrice = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (editingCourse != null) "Edit Course" else stringResource(R.string.manage_courses_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (editingCourse != null) {
                        "Update course name or price below"
                    } else {
                        "Add courses with their fee and edit anytime"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name *") },
                    placeholder = { Text("e.g. Digital Marketing") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_name_input"),
                )

                OutlinedTextField(
                    value = coursePrice,
                    onValueChange = { coursePrice = it },
                    label = { Text("Course Price / Fee (Optional)") },
                    placeholder = { Text("e.g. 25,000 or $499") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_price_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (editingCourse != null) {
                        OutlinedButton(
                            onClick = { resetInputs() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("cancel_edit_course_button"),
                        ) {
                            Text("Cancel")
                        }
                    }

                    Button(
                        onClick = {
                            val cleanName = courseName.trim()
                            val cleanPrice = coursePrice.trim()
                            if (cleanName.isNotEmpty()) {
                                val target = editingCourse
                                if (target != null) {
                                    onUpdateCourse(target, cleanName, cleanPrice)
                                } else {
                                    onAddCourse(cleanName, cleanPrice)
                                }
                                resetInputs()
                            }
                        },
                        enabled = courseName.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("save_course_button"),
                    ) {
                        Icon(
                            imageVector = if (editingCourse != null) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (editingCourse != null) "Save Changes" else "Add Course",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (courses.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_courses_state"),
                    ) {
                        Text(
                            text = "No courses added yet. Add your first course and price above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(courses, key = { it.id }) { course ->
                            val isBeingEdited = editingCourse?.id == course.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isBeingEdited) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                },
                                border = BorderStroke(
                                    width = if (isBeingEdited) 1.5.dp else 1.dp,
                                    color = if (isBeingEdited) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("course_row_${course.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = course.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (course.price.isNotBlank()) {
                                                "Price: ${course.price}"
                                            } else {
                                                "No price set"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (course.price.isNotBlank()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editingCourse = course
                                            courseName = course.name
                                            coursePrice = course.price
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("edit_course_button_${course.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit ${course.name}",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (editingCourse?.id == course.id) {
                                                resetInputs()
                                            }
                                            onDeleteCourse(course)
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("delete_course_button_${course.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete ${course.name}",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
