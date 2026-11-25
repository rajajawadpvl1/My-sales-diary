package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CallStatus
import com.example.ui.theme.StatusBusyBg
import com.example.ui.theme.StatusBusyFg
import com.example.ui.theme.StatusCallBackBg
import com.example.ui.theme.StatusCallBackFg
import com.example.ui.theme.StatusEnrolledBg
import com.example.ui.theme.StatusEnrolledFg
import com.example.ui.theme.StatusInterestedBg
import com.example.ui.theme.StatusInterestedFg
import com.example.ui.theme.StatusNotConnectedBg
import com.example.ui.theme.StatusNotConnectedFg
import com.example.ui.theme.StatusNotInterestedBg
import com.example.ui.theme.StatusNotInterestedFg
import com.example.ui.theme.StatusSwitchedOffBg
import com.example.ui.theme.StatusSwitchedOffFg
import com.example.ui.theme.StatusWrongNumberBg
import com.example.ui.theme.StatusWrongNumberFg

data class StatusVisualStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
)

@Composable
fun callStatusStyle(status: CallStatus): StatusVisualStyle {
    val isDark = isSystemInDarkTheme()
    return when (status) {
        CallStatus.INTERESTED -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF123626) else StatusInterestedBg,
            contentColor = if (isDark) Color(0xFF7BE0AE) else StatusInterestedFg,
            icon = Icons.Rounded.ThumbUp,
        )
        CallStatus.ENROLLED -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF0E3532) else StatusEnrolledBg,
            contentColor = if (isDark) Color(0xFF6CD6CC) else StatusEnrolledFg,
            icon = Icons.Rounded.School,
        )
        CallStatus.NOT_INTERESTED -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF3E1818) else StatusNotInterestedBg,
            contentColor = if (isDark) Color(0xFFFF9B9B) else StatusNotInterestedFg,
            icon = Icons.Rounded.Block,
        )
        CallStatus.WRONG_NUMBER -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF3C1725) else StatusWrongNumberBg,
            contentColor = if (isDark) Color(0xFFFF9DB8) else StatusWrongNumberFg,
            icon = Icons.Rounded.PhoneDisabled,
        )
        CallStatus.CALL_BACK -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF40250D) else StatusCallBackBg,
            contentColor = if (isDark) Color(0xFFFFB574) else StatusCallBackFg,
            icon = Icons.Rounded.PhoneCallback,
        )
        CallStatus.BUSY -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF3A2B0E) else StatusBusyBg,
            contentColor = if (isDark) Color(0xFFF3C66E) else StatusBusyFg,
            icon = Icons.Rounded.DoNotDisturbOn,
        )
        CallStatus.NOT_CONNECTED -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF212B3B) else StatusNotConnectedBg,
            contentColor = if (isDark) Color(0xFFB5C4D8) else StatusNotConnectedFg,
            icon = Icons.Rounded.PhoneMissed,
        )
        CallStatus.SWITCHED_OFF -> StatusVisualStyle(
            containerColor = if (isDark) Color(0xFF272242) else StatusSwitchedOffBg,
            contentColor = if (isDark) Color(0xFFB8B0F5) else StatusSwitchedOffFg,
            icon = Icons.Rounded.PhoneDisabled,
        )
    }
}

@Composable
fun StatusBadge(
    status: CallStatus,
    modifier: Modifier = Modifier,
) {
    val style = callStatusStyle(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = style.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.contentColor,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = style.contentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallStatusSelector(
    selectedStatus: CallStatus,
    onSelectStatus: (CallStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CallStatus.entries.forEach { status ->
            val isSelected = status == selectedStatus
            val style = callStatusStyle(status)
            Surface(
                onClick = { onSelectStatus(status) },
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .testTag("status_chip_${status.name.lowercase()}"),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) style.containerColor else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) style.contentColor else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else style.icon,
                        contentDescription = null,
                        tint = if (isSelected) style.contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) style.contentColor else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
