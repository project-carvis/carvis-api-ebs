package cloud.carvis.api.events.listener

import cloud.carvis.api.model.events.UserSignupEvent
import cloud.carvis.api.service.NotificationService
import cloud.carvis.api.user.service.UserService
import io.awspring.cloud.messaging.listener.SqsMessageDeletionPolicy.NO_REDRIVE
import io.awspring.cloud.messaging.listener.annotation.SqsListener
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class UserSignupEventListener(
    private val notificationService: NotificationService,
    private val userService: UserService
) {

    private val logger = KotlinLogging.logger {}
    private val userSignupFunctions: List<Pair<String, (e: UserSignupEvent) -> Unit>> = listOf(
        "notificationService.notifyUserSignup(event)" to { event -> notificationService.notifyUserSignup(event) },
        "userService.persistNewUserSignup(event)" to { event -> userService.persistNewUserSignup(event) }
    )

    @SqsListener("\${sqs.queues.user-signup}", deletionPolicy = NO_REDRIVE)
    fun onMessage(event: UserSignupEvent) {
        logger.info("received $event")

        val errors = userSignupFunctions
            .mapNotNull { consumeEvent(event, it.first, it.second) }

        if (errors.isNotEmpty()) {
            throw errors.first()
        }
    }

    private fun consumeEvent(event: UserSignupEvent, fnDescription: String, fn: (event: UserSignupEvent) -> Unit): Exception? = try {
        fn.invoke(event)
        null
    } catch (e: Exception) {
        logger.error(e) { "Error while executing function after user signup: $fnDescription" }
        e
    }
}
