package org.aust.dialer.ui.sms

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.core.Format
import org.aust.dialer.data.ContactIndex
import org.aust.dialer.sms.Conversation
import org.aust.dialer.ui.Avatar
import org.aust.dialer.ui.EmptyState
import org.aust.dialer.ui.MessageCard
import org.aust.dialer.ui.rememberPermissionRequester

@Composable
fun MessagesTab(
    dialerVm: DialerViewModel,
    smsVm: SmsViewModel,
    listState: LazyListState,
    onOpenThread: (String) -> Unit,
) {
    val perms by smsVm.perms.collectAsState()
    val conversations by smsVm.conversations.collectAsState()
    val index by dialerVm.contactIndex.collectAsState()
    val request = rememberPermissionRequester(smsVm::refreshPermissions, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS))

    if (!perms.readSms) {
        MessageCard(stringResource(R.string.sms_permission_text), stringResource(R.string.action_grant), request)
        return
    }
    if (conversations.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.Message, stringResource(R.string.sms_empty), stringResource(R.string.sms_empty_hint))
        return
    }

    LazyColumn(state = listState) {
        items(conversations, key = { it.threadId }) { conv ->
            val contact = index.findByNumber(conv.address)
            val name = contact?.name ?: conv.address
            ListItem(
                modifier = androidx.compose.ui.Modifier.clickable(onClickLabel = stringResource(R.string.action_open)) {
                    onOpenThread(conv.address)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Avatar(contact?.name ?: conv.address, contact?.thumbUri, 44.dp) },
                headlineContent = {
                    Text(
                        name,
                        maxLines = 1,
                        fontWeight = if (conv.hasUnread) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                supportingContent = {
                    val prefix = if (conv.outgoing) stringResource(R.string.sms_you_prefix) else ""
                    Text(
                        prefix + conv.snippet,
                        maxLines = 1,
                        fontWeight = if (conv.hasUnread) FontWeight.Bold else FontWeight.Normal,
                        color = if (conv.hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Text(Format.callTime(androidx.compose.ui.platform.LocalContext.current, conv.date), style = MaterialTheme.typography.labelSmall)
                },
            )
        }
    }
}
