package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.DueTodayAccent
import com.example.ui.theme.StatusEnrolledFg
import com.example.ui.theme.StatusInterestedFg
import com.example.ui.viewmodel.DashboardStats

@Composable
fun DashboardSummaryRow(
    stats: DashboardStats,
    onDueTodayClick: () -> Unit,
    onTotalLeadsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val totalDot = MaterialTheme.colorScheme.primary
    val interestedDot = if (isDark) Color(0xFF7BE0AE) else StatusInterestedFg
    val dueTodayDot = if (isDark) Color(0xFFFFB574) else DueTodayAccent
    val enrolledDot = if (isDark) Color(0xFF6CD6CC) else StatusEnrolledFg

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryStatCard(
            label = stringResource(R.string.stat_total_leads),
            count = stats.totalLeads,
            accentColor = totalDot,
            onClick = onTotalLeadsClick,
            modifier = Modifier
                .weight(1f)
                .testTag("stat_card_total"),
        )
        SummaryStatCard(
            label = stringResource(R.string.stat_interested),
            count = stats.interestedCount,
            accentColor = interestedDot,
            onClick = onTotalLeadsClick,
            modifier = Modifier
                .weight(1f)
                .testTag("stat_card_interested"),
        )
        SummaryStatCard(
            label = stringResource(R.string.stat_due_today),
            count = stats.dueTodayCount,
            accentColor = dueTodayDot,
            highlight = stats.dueTodayCount > 0,
            onClick = onDueTodayClick,
            modifier = Modifier
                .weight(1f)
                .testTag("stat_card_due_today"),
        )
        SummaryStatCard(
            label = stringResource(R.string.stat_enrolled),
            count = stats.enrolledCount,
            accentColor = enrolledDot,
            onClick = onTotalLeadsClick,
            modifier = Modifier
                .weight(1f)
                .testTag("stat_card_enrolled"),
        )
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (highlight) 1.5.dp else 1.dp,
            color = if (highlight) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        ),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
