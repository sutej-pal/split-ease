package com.splitease.app.presentation.expenses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExpenseAttachmentsGalleryScreen(
    expenseId: String,
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val attachments by viewModel.observeExpensePhotos(expenseId).collectAsStateWithLifecycle()
    val safeStart = startIndex.coerceIn(0, (attachments.size - 1).coerceAtLeast(0))
    val pagerState =
        rememberPagerState(
            initialPage = safeStart,
            pageCount = { attachments.size.coerceAtLeast(1) },
        )

    LaunchedEffect(expenseId) {
        viewModel.refreshExpenseSideData(expenseId)
    }

    LaunchedEffect(safeStart, attachments.size) {
        if (attachments.isNotEmpty() && pagerState.currentPage != safeStart) {
            pagerState.scrollToPage(safeStart)
        }
    }

    SeSystemBars(
        statusBarColor = Color.Black,
        navigationBarColor = Color.Black,
        statusBarDarkIcons = false,
        navigationBarDarkIcons = false,
    )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                    )
                }
                Text(
                    text =
                        if (attachments.isEmpty()) {
                            stringResource(R.string.expense_attachments_section)
                        } else {
                            stringResource(
                                R.string.expense_attachment_gallery_counter,
                                pagerState.currentPage + 1,
                                attachments.size,
                            )
                        },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
    ) { paddingValues ->
        if (attachments.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.expense_attachments_empty),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val attachment = attachments[page]
                AttachmentGalleryPage(displayUri = attachment.displayUri)
            }

            val current = attachments[pagerState.currentPage]
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (attachments.size > 1) {
                    AttachmentPagerDots(
                        pagerState = pagerState,
                        pageCount = attachments.size,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text =
                        stringResource(
                            R.string.expense_attachment_added_by,
                            current.authorLabel,
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AttachmentPagerDots(
    pagerState: PagerState,
    pageCount: Int,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = pagerState.currentPage == index
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.35f)
                            },
                        ).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 14.dp),
                            role = Role.Tab,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                        ),
            )
        }
    }
}

private sealed interface AttachmentGalleryLoadState {
    data object Loading : AttachmentGalleryLoadState

    data class Ready(
        val bitmap: ImageBitmap,
    ) : AttachmentGalleryLoadState

    data object Failed : AttachmentGalleryLoadState
}

@Composable
private fun AttachmentGalleryPage(displayUri: String) {
    val context = LocalContext.current
    val loadState by produceState<AttachmentGalleryLoadState>(
        AttachmentGalleryLoadState.Loading,
        displayUri,
    ) {
        value = AttachmentGalleryLoadState.Loading
        value =
            withContext(Dispatchers.IO) {
                val decoded =
                    AvatarImageIO
                        .decodeScaled(
                            context = context,
                            photoUrl = displayUri,
                            maxSidePx = AvatarImageIO.ATTACHMENT_GALLERY_MAX_SIDE_PX,
                        )?.asImageBitmap()
                if (decoded != null) {
                    AttachmentGalleryLoadState.Ready(decoded)
                } else {
                    AttachmentGalleryLoadState.Failed
                }
            }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val imageLoad = loadState) {
            AttachmentGalleryLoadState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp,
                )
            }
            is AttachmentGalleryLoadState.Ready -> {
                Image(
                    bitmap = imageLoad.bitmap,
                    contentDescription = stringResource(R.string.cd_expense_attachment),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AttachmentGalleryLoadState.Failed -> {
                Text(
                    text = stringResource(R.string.msg_image_load_failed),
                    color = SplitEaseColors.NavyMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
