package cloud.carvis.api.integration

import cloud.carvis.api.AbstractApplicationTest
import cloud.carvis.api.dao.repositories.NewUserRepository
import cloud.carvis.api.service.NotificationService
import cloud.carvis.api.user.model.UserDto
import cloud.carvis.api.user.model.UserRole
import cloud.carvis.api.user.service.UserService
import cloud.carvis.api.util.helpers.SesHelper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.annotation.DirtiesContext
import java.util.concurrent.TimeUnit.SECONDS


// inject spies properly
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class UserSignupEventListenerTest : AbstractApplicationTest() {

    @Autowired
    private lateinit var newUserRepository: NewUserRepository

    @Autowired
    private lateinit var sesHelper: SesHelper

    @SpyBean
    private lateinit var userServiceSpy: UserService

    @SpyBean
    private lateinit var notificationServiceSpy: NotificationService

    @Test
    fun `onMessage - send mail to admin`() {
        // given
        auth0Mock
            .withUsers(
                UserDto(
                    userId = "user-id",
                    name = "na-me",
                    company = "com-pany",
                    phone = "ph-one",
                    email = "dummy@dummy.com",
                    roles = listOf(UserRole.ADMIN)
                )
            )

        // when
        val event = testDataGenerator
            .withUserSignupEvent()
            .getUserSignupEvent()
            .value()

        // then
        val mail = sesHelper.latestMail()
        assertThat(mail.read<String>("Source")).isEqualTo("usersignup@carvis.cloud")
        assertThat(mail.read<List<String>>("Destinations.ToAddresses")).hasSize(1)
        assertThat(mail.read<List<String>>("Destinations.ToAddresses")[0]).isEqualTo("dummy@dummy.com")
        assertThat(mail.read<List<String>>("Destinations.CcAddresses")).isEmpty()
        assertThat(mail.read<List<String>>("Destinations.BccAddresses")).isEmpty()
        assertThat(mail.read<String>("Subject")).isEqualTo("Neuer Nutzer")
        assertThat(mail.read<String>("Body")).isEqualTo(
            """
            Hi,

            ein neuer Nutzer hat sich registiert:
            
            User-ID: ${event.userId}
            E-Mail: ${event.email}
            Name: ${event.name}

            Bitte gib dem Nutzer die entsprechende Berechtigung: https://carvis.cloud/user-management
            """.trimIndent()
        )
    }

    @Test
    fun `onMessage - increase new users counter from 0 to 1`() {
        // given
        auth0Mock
            .withUsers(
                UserDto(
                    userId = "user-id",
                    name = "na-me",
                    company = "com-pany",
                    phone = "ph-one",
                    email = "dummy@dummy.com",
                    roles = listOf(UserRole.ADMIN)
                )
            )

        // when
        val event = testDataGenerator
            .withUserSignupEvent()
            .getUserSignupEvent()
            .value()

        // then
        await().atMost(10, SECONDS)
            .until { newUserRepository.count() == 1L }
        assertThat(newUserRepository.findByIdOrNull(event.userId)).isNotNull
    }

    @Test
    fun `onMessage - increase new users counter from 1 to 2`() {
        // given
        auth0Mock
            .withUsers(
                UserDto(
                    userId = "user-id",
                    name = "na-me",
                    company = "com-pany",
                    phone = "ph-one",
                    email = "dummy@dummy.com",
                    roles = listOf(UserRole.ADMIN)
                )
            )

        // when
        val eventOne = testDataGenerator
            .withUserSignupEvent()
            .getUserSignupEvent()
            .value()
        val eventTwo = testDataGenerator
            .withUserSignupEvent()
            .getUserSignupEvent()
            .value()

        // then
        await().atMost(10, SECONDS)
            .until { newUserRepository.findAll().count() == 2 }
        assertThat(newUserRepository.findByIdOrNull(eventOne.userId)).isNotNull
        assertThat(newUserRepository.findByIdOrNull(eventTwo.userId)).isNotNull
    }

    @Test
    fun `onMessage - processing error`() {
        // given
        doThrow(RuntimeException::class).`when`(userServiceSpy).persistNewUserSignup(any())
        doNothing().`when`(notificationServiceSpy).notifyUserSignup(any())

        // when
        val event = testDataGenerator
            .withUserSignupEvent()
            .getUserSignupEvent()
            .value()

        // then
        await().atMost(10, SECONDS)
            .until { testDataGenerator.getUserSignupDlqMessages().isNotEmpty() }
        verify(notificationServiceSpy).notifyUserSignup(event)
        verify(userServiceSpy).persistNewUserSignup(event)
    }
}
