package com.splitease.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.splitease.app.data.local.db.SplitEaseDatabase
import com.splitease.app.data.local.entity.ExpenseEntity
import com.splitease.app.data.local.entity.ExpenseSplitEntity
import com.splitease.app.data.local.entity.FriendEntity
import com.splitease.app.data.local.entity.GroupEntity
import com.splitease.app.data.local.entity.GroupMemberEntity
import com.splitease.app.data.local.entity.PaymentEntity
import com.splitease.app.data.local.entity.UserEntity
import com.splitease.app.domain.model.MemberRole
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.math.BigDecimal

/**
 * In-memory Room DAO tests (instrumented).
 */
@RunWith(AndroidJUnit4::class)
class DaoInstrumentedTest {
    private lateinit var db: SplitEaseDatabase
    private lateinit var userDao: UserDao
    private lateinit var friendDao: FriendDao
    private lateinit var groupDao: GroupDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var paymentDao: PaymentDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, SplitEaseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        friendDao = db.friendDao()
        groupDao = db.groupDao()
        expenseDao = db.expenseDao()
        paymentDao = db.paymentDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun userUpsertAndQueryByEmail() =
        runTest {
            val user = sampleUser("u1", "alex@example.com", "Alex")
            userDao.upsert(user)

            assertEquals("Alex", userDao.getById("u1")?.displayName)
            assertEquals("u1", userDao.getByEmail("alex@example.com")?.id)

            userDao.observeAll().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun friendBelongsToOwnerAndCascadesOnOwnerDelete() =
        runTest {
            userDao.upsert(sampleUser("owner", "owner@example.com", "Owner"))
            userDao.upsert(sampleUser("friend", "friend@example.com", "Friend"))
            friendDao.upsert(
                FriendEntity(
                    id = "f1",
                    ownerUserId = "owner",
                    friendUserId = "friend",
                    emailSnapshot = "friend@example.com",
                    displayNameSnapshot = "Friend",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            )

            friendDao.observeByOwner("owner").test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }

            userDao.deleteById("owner")
            assertNull(friendDao.getById("f1"))
        }

    @Test
    fun groupMembersCascadeWhenGroupDeleted() =
        runTest {
            userDao.upsert(sampleUser("u1", "a@example.com", "A"))
            userDao.upsert(sampleUser("u2", "b@example.com", "B"))
            groupDao.upsert(
                GroupEntity(
                    id = "g1",
                    name = "Trip",
                    defaultCurrencyCode = "INR",
                    createdByUserId = "u1",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            )
            groupDao.upsertMember(
                GroupMemberEntity(
                    id = "m1",
                    groupId = "g1",
                    userId = "u1",
                    role = MemberRole.OWNER,
                    joinedAtEpochMs = 1L,
                ),
            )
            groupDao.upsertMember(
                GroupMemberEntity(
                    id = "m2",
                    groupId = "g1",
                    userId = "u2",
                    role = MemberRole.MEMBER,
                    joinedAtEpochMs = 2L,
                ),
            )

            groupDao.observeMembers("g1").test {
                assertEquals(2, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }

            groupDao.deleteById("g1")
            groupDao.observeMembers("g1").test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun expenseWithSplitsPersistsBigDecimalAmounts() =
        runTest {
            userDao.upsert(sampleUser("u1", "a@example.com", "A"))
            userDao.upsert(sampleUser("u2", "b@example.com", "B"))
            userDao.upsert(sampleUser("u3", "c@example.com", "C"))
            groupDao.upsert(
                GroupEntity(
                    id = "g1",
                    name = "Home",
                    defaultCurrencyCode = "INR",
                    createdByUserId = "u1",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            )

            val expense = ExpenseEntity(
                id = "e1",
                description = "Groceries",
                amount = BigDecimal("100.00"),
                currencyCode = "INR",
                paidByUserId = "u1",
                groupId = "g1",
                expenseDateEpochMs = 10L,
                splitType = SplitType.UNEQUAL,
                isRecurring = false,
                recurrenceFrequency = RecurrenceFrequency.NONE,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
            val splits = listOf(
                ExpenseSplitEntity("s1", "e1", "u1", BigDecimal("33.34")),
                ExpenseSplitEntity("s2", "e1", "u2", BigDecimal("33.33")),
                ExpenseSplitEntity("s3", "e1", "u3", BigDecimal("33.33")),
            )
            expenseDao.upsertExpenseWithSplits(expense, splits)

            val loaded = expenseDao.getById("e1")
            assertEquals(0, BigDecimal("100.00").compareTo(loaded!!.amount))
            val loadedSplits = expenseDao.getSplits("e1")
            assertEquals(3, loadedSplits.size)
            val sum = loadedSplits.fold(BigDecimal.ZERO) { acc, row -> acc + row.owedAmount }
            assertEquals(0, BigDecimal("100.00").compareTo(sum))
        }

    @Test
    fun paymentInsertAndGroupFilter() =
        runTest {
            userDao.upsert(sampleUser("u1", "a@example.com", "A"))
            userDao.upsert(sampleUser("u2", "b@example.com", "B"))
            groupDao.upsert(
                GroupEntity(
                    id = "g1",
                    name = "Home",
                    defaultCurrencyCode = "USD",
                    createdByUserId = "u1",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            )
            paymentDao.upsert(
                PaymentEntity(
                    id = "p1",
                    fromUserId = "u1",
                    toUserId = "u2",
                    amount = BigDecimal("50.00"),
                    currencyCode = "USD",
                    groupId = "g1",
                    note = "Cash",
                    paidAtEpochMs = 20L,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            )

            paymentDao.observeByGroup("g1").test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals(0, BigDecimal("50.00").compareTo(items.first().amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun sampleUser(
        id: String,
        email: String,
        name: String,
    ) = UserEntity(
        id = id,
        email = email,
        displayName = name,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
