package com.splitease.app.presentation.account

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitease.app.R
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.model.MemberRole
import com.splitease.app.presentation.groups.GroupSettingsContent
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeActionChipRow
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewFollowUps20261013Test {
    @Test
    fun supportEmail_createsUnpinnedMailtoIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val email = context.getString(R.string.support_email)
        val subject = context.getString(R.string.support_email_subject)
        assertEquals("support@splitease.app", email)

        val intent = buildSupportEmailIntent(email, subject)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertNull(intent.`package`)
    }

    @Test
    fun faqSplitCopy_usesUnequally() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val answer = context.getString(R.string.faq_add_expense_a)
        assertTrue("Expected faq_add_expense_a to contain 'unequally'", answer.contains("unequally"))
        assertTrue("Expected faq_add_expense_a not to contain 'exact amounts'", !answer.contains("exact amounts"))
    }

    @Test
    fun composeScreens_renderSemanticsAndHeightsCorrectlyOnDevice() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var composeView: ComposeView? = null
            scenario.onActivity { activity ->
                val cv = ComposeView(activity)
                composeView = cv
                activity.setContentView(cv)
                cv.setContent {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    ) {
                        SePreview {
                            Column {
                                SeActionChipRow {
                                    SeActionChip(
                                        label = "Settle up",
                                        icon = Icons.Filled.AccountBalanceWallet,
                                        selected = true,
                                        onClick = {},
                                        modifier = Modifier.testTag("chip_settle"),
                                    )
                                    SeActionChip(
                                        label = "Balances",
                                        onClick = {},
                                        modifier = Modifier.testTag("chip_balances"),
                                    )
                                    SeActionChip(
                                        label = "Totals",
                                        onClick = {},
                                        modifier = Modifier.testTag("chip_totals"),
                                    )
                                    SeActionChip(
                                        label = "Pin Board",
                                        onClick = {},
                                        modifier = Modifier.testTag("chip_pin"),
                                    )
                                }
                                HelpCenterScreen(onBack = {})
                            }
                        }
                    }
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val rootView = composeView!!.getChildAt(0)
                val ownerField = rootView.javaClass.getDeclaredMethod("getSemanticsOwner")
                val semanticsOwner = ownerField.invoke(rootView) as SemanticsOwner
                val allNodes = semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

                val density = activity.resources.displayMetrics.density
                listOf("chip_settle", "chip_balances", "chip_totals", "chip_pin").forEach { tag ->
                    val node =
                        semanticsOwner
                            .getAllSemanticsNodes(mergingEnabled = false)
                            .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                    assertNotNull("Expected chip node $tag", node)
                    val heightDp = node!!.boundsInRoot.height / density
                    assertTrue("Expected $tag height >= 44dp, got $heightDp", heightDp >= 43.5f)
                }

                val faqNode =
                    allNodes.firstOrNull { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any {
                            it.text == "How do I add an expense?"
                        } == true
                    }
                assertNotNull("Expected FAQ question node", faqNode)
                assertEquals(
                    "Collapsed",
                    faqNode!!.config.getOrNull(SemanticsProperties.StateDescription),
                )
                val expandAction = faqNode.config.getOrNull(SemanticsActions.Expand)?.action
                assertNotNull("Expected Expand semantics action on collapsed FAQ row", expandAction)
                expandAction!!.invoke()
            }
            Thread.sleep(300)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val rootView = composeView!!.getChildAt(0)
                val ownerField = rootView.javaClass.getDeclaredMethod("getSemanticsOwner")
                val semanticsOwner = ownerField.invoke(rootView) as SemanticsOwner
                val allNodes = semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

                val faqNodeAfter =
                    allNodes.firstOrNull { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any {
                            it.text == "How do I add an expense?"
                        } == true
                    }
                assertNotNull(faqNodeAfter)
                assertEquals(
                    "Expanded",
                    faqNodeAfter!!.config.getOrNull(SemanticsProperties.StateDescription),
                )
                assertNotNull(
                    "Expected Collapse semantics action on expanded FAQ row",
                    faqNodeAfter.config.getOrNull(SemanticsActions.Collapse)?.action,
                )

                val answerVisible =
                    allNodes.any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any {
                            it.text.contains("You can split equally, unequally, by percentages")
                        } == true
                    }
                assertTrue("Expected expanded FAQ answer with 'unequally' to be visible", answerVisible)
            }

            // Now test GroupSettingsContent
            scenario.onActivity {
                composeView!!.setContent {
                    SePreview {
                        GroupSettingsContent(
                            group =
                                Group(
                                    id = "group-1",
                                    name = "Trip to Goa",
                                    defaultCurrencyCode = "INR",
                                    groupType = GroupType.HOME,
                                    createdByUserId = "user-1",
                                    createdAtEpochMs = 0L,
                                    updatedAtEpochMs = 0L,
                                ),
                            members =
                                listOf(
                                    GroupMember(
                                        id = "member-1",
                                        groupId = "group-1",
                                        userId = "user-1",
                                        role = MemberRole.OWNER,
                                        joinedAtEpochMs = 0L,
                                    ),
                                ),
                            friends = emptyList(),
                            userDisplayNames = mapOf("user-1" to "Alex Rivera"),
                            userPhotoUrls = emptyMap(),
                            groupBalance = null,
                            currentUserId = "user-1",
                            isOwner = true,
                            simplifyDebts = true,
                            groupMuted = false,
                            isExporting = false,
                            errorMessage = null,
                            infoMessage = null,
                            onBack = {},
                            onEditGroup = {},
                            onChangePhoto = {},
                            onAddPeople = {},
                            onInviteViaLink = {},
                            onCopyInviteLink = {},
                            onShareInviteAgain = {},
                            onSelectMember = {},
                            onExportCsv = {},
                            onSimplifyDebtsChange = {},
                            onShowSimplifyInfo = {},
                            onGroupMutedChange = {},
                            onShowDefaultSplitInfo = {},
                            onLeaveGroup = {},
                            onDeleteGroup = {},
                        )
                    }
                }
            }
            Thread.sleep(200)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val rootView = composeView!!.getChildAt(0)
                val ownerField = rootView.javaClass.getDeclaredMethod("getSemanticsOwner")
                val semanticsOwner = ownerField.invoke(rootView) as SemanticsOwner
                val texts =
                    semanticsOwner
                        .getAllSemanticsNodes(mergingEnabled = false)
                        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { a -> a.text } }
                assertTrue("Expected 'Trip to Goa' in GroupSettingsContent", texts.contains("Trip to Goa"))
                assertTrue("Expected 'Alex Rivera (you)' in GroupSettingsContent", texts.contains("Alex Rivera (you)"))
                assertTrue("Expected no 'asda' placeholder", !texts.contains("asda"))
            }

            // Verify RateAppDialog strings and openPlayListing intent
            scenario.onActivity { activity ->
                assertEquals("Rate SplitEase", activity.getString(R.string.rate_app_title))
                assertEquals(
                    "Share your rating and review on Google Play.",
                    activity.getString(R.string.rate_app_body),
                )
                assertEquals("Rate on Google Play", activity.getString(R.string.action_rate_on_play))
                assertEquals("Rate us on Google Play", activity.getString(R.string.settings_rate_app_subtitle))
            }
        }
    }

    private fun SemanticsOwner.getAllSemanticsNodes(mergingEnabled: Boolean): List<SemanticsNode> {
        val root = if (mergingEnabled) rootSemanticsNode else unmergedRootSemanticsNode
        val result = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            result.add(node)
            node.children.forEach(::collect)
        }
        collect(root)
        return result
    }
}
