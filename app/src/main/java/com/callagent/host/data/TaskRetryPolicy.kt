package com.callagent.host.data

enum class RetryAction {
    RETRY_SAME_KEY,
    QUERY_EXISTING_MESSAGE,
    NONE
}

fun retryAction(taskKey: String?, serverMessageId: String?): RetryAction = when {
    taskKey.isNullOrBlank() -> RetryAction.NONE
    !serverMessageId.isNullOrBlank() -> RetryAction.QUERY_EXISTING_MESSAGE
    else -> RetryAction.RETRY_SAME_KEY
}
